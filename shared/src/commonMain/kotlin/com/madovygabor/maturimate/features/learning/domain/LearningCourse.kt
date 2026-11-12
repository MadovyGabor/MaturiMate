package com.madovygabor.maturimate.features.learning.domain

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.learning.domain.model.ContinueTarget
import com.madovygabor.maturimate.features.learning.domain.model.LearningUnit
import com.madovygabor.maturimate.features.learning.domain.model.LessonTestCount
import com.madovygabor.maturimate.features.learning.domain.model.PartProgress
import com.madovygabor.maturimate.features.learning.domain.model.PathStep
import com.madovygabor.maturimate.features.learning.domain.model.PracticeKind
import com.madovygabor.maturimate.features.learning.domain.model.StepCount
import com.madovygabor.maturimate.features.learning.domain.model.StepKind
import com.madovygabor.maturimate.features.learning.domain.model.StepState
import com.madovygabor.maturimate.features.learning.domain.model.SubjectBreakdown
import com.madovygabor.maturimate.features.learning.domain.model.SubjectProgress
import com.madovygabor.maturimate.features.learning.domain.model.Topic
import com.madovygabor.maturimate.features.learning.domain.model.TopicLevel
import com.madovygabor.maturimate.features.learning.domain.model.TopicSummary
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgressStatus

/**
 * Builds the learning path from the content and the user's progress: which steps a subject, a part and a
 * topic have, how many of them are done, and which one comes next. It holds the rules the screens
 * show; the screens only draw them.
 *
 * * A step is done when its learning unit is `COMPLETED` in the progress.
 * * In a path the first step that is not done is the current one, every later step is still to do.
 * * A topic with topics below it lists them; its final test (a `FINAL` unit on the topic) comes
 *   last, and is the current step once everything below it is done.
 */
class LearningCourse(
    private val content: ContentRepository,
    private val progress: ProgressRepository,
    /** A guest gets one lesson; after it, the next step is the sign-up (decided 2026-10-30, roadmap 12.5). */
    private val isGuest: suspend () -> Boolean = { false },
) {

    /** Every subject of the installed content with the user's progress in it. */
    suspend fun subjects(): OperationResult<List<SubjectProgress>, LearningError> = guarded {
        val done = completedUnits()
        content.subjects().orThrow().map { subject ->
            val parts = content.rootTopics(subject.id).orThrow()
            val steps = parts.fold(StepCount.ZERO) { sum, part -> sum + count(part, done) }
            SubjectProgress(subject.id, subject.code, parts.size, steps)
        }
    }

    /** Every subject of the installed content with its done lessons and tests, in total and per big part. */
    suspend fun breakdown(): OperationResult<List<SubjectBreakdown>, LearningError> = guarded {
        val done = completedUnits()
        content.subjects().orThrow().map { subject ->
            val parts = content.rootTopics(subject.id).orThrow().map { part -> PartProgress(part, lessonsAndTests(unitsBelow(part), done)) }
            SubjectBreakdown(subject.id, parts.fold(LessonTestCount.ZERO) { sum, part -> sum + part.count }, parts)
        }
    }

    private fun lessonsAndTests(units: List<LearningUnit>, done: Set<Long>): LessonTestCount {
        val (lessons, tests) = units.partition { it is LearningUnit.Summary }
        return LessonTestCount(
            StepCount(lessons.count { it.id in done }, lessons.size),
            StepCount(tests.count { it.id in done }, tests.size),
        )
    }

    /**
     * The step to go on with. Every step of the installed content is put in the order of the path (subject by
     * subject, part by part, topic by topic; the final test of a parent topic after the topics below it).
     * Without [afterUnitId] the answer is the first step that is not done. With it (the unit the student worked
     * on last) the search starts at that unit: a lesson that is done moves on to what follows it, a test that was
     * not passed comes up again, and when the end is reached the search goes on from the beginning. Empty when
     * everything is done.
     */
    suspend fun nextStep(afterUnitId: Long? = null): OperationResult<ContinueTarget?, LearningError> = guarded {
        val done = completedUnits()
        val steps = mutableListOf<ContinueTarget>()
        for (subject in content.subjects().orThrow()) {
            for (part in content.rootTopics(subject.id).orThrow()) collectSteps(subject.id, part, listOf(part.id), steps)
        }
        val start = steps.indexOfFirst { it.unit.id == afterUnitId }.coerceAtLeast(0)
        (steps.drop(start) + steps.take(start)).firstOrNull { it.unit.id !in done }
    }

    private suspend fun collectSteps(subjectId: Long, topic: Topic, chain: List<Long>, into: MutableList<ContinueTarget>) {
        val children = content.childTopics(topic.id).orThrow()
        val units = content.units(topic.id).orThrow().sortedBy { it.sortOrder }
        if (children.isEmpty()) {
            units.forEach { into += ContinueTarget(subjectId, chain, topic.title, it, kindOf(it)) }
            return
        }
        children.forEach { collectSteps(subjectId, it, chain + it.id, into) }
        units.filterIsInstance<LearningUnit.Practice>().filter { it.kind == PracticeKind.FINAL }
            .forEach { into += ContinueTarget(subjectId, chain, topic.title, it, StepKind.FINAL_TEST) }
    }

    /** The big parts of a subject. */
    suspend fun parts(subjectId: Long): OperationResult<TopicLevel.Branch, LearningError> = guarded {
        val done = completedUnits()
        val parts = content.rootTopics(subjectId).orThrow()
        TopicLevel.Branch(title = null, parentTitle = null, topics = parts.map { summary(it, done) }, finalTest = null)
    }

    /** A topic: its topics when it has some, otherwise its path. */
    suspend fun topic(topicId: Long): OperationResult<TopicLevel, LearningError> = guarded {
        val done = completedUnits()
        val topic = content.topic(topicId).orThrow()
        val parentTitle = topic.parentTopicId?.let { content.topic(it).orThrow().title }
        val children = content.childTopics(topicId).orThrow()
        val units = content.units(topicId).orThrow()

        if (children.isEmpty()) {
            TopicLevel.Path(topic.title, parentTitle, steps(units, done, current(done)))
        } else {
            val below = children.fold(StepCount.ZERO) { sum, child -> sum + count(child, done) }
            val final = units.filterIsInstance<LearningUnit.Practice>().firstOrNull { it.kind == PracticeKind.FINAL }
            val finalStep = final?.let {
                val state = when {
                    it.id in done -> StepState.DONE
                    below.done == below.total -> current(done)
                    else -> StepState.TODO
                }
                PathStep(it, StepKind.FINAL_TEST, state)
            }
            TopicLevel.Branch(topic.title, parentTitle, children.map { summary(it, done) }, finalStep)
        }
    }

    /** The state of the next step: the sign-up for a guest who has done a lesson already. */
    private suspend fun current(done: Set<Long>): StepState =
        if (done.isNotEmpty() && isGuest()) StepState.SIGN_UP else StepState.CURRENT

    private fun steps(units: List<LearningUnit>, done: Set<Long>, current: StepState): List<PathStep> {
        var currentTaken = false
        return units.sortedBy { it.sortOrder }.map { unit ->
            val state = when {
                unit.id in done -> StepState.DONE
                !currentTaken -> current.also { currentTaken = true }
                else -> StepState.TODO
            }
            PathStep(unit, kindOf(unit), state)
        }
    }

    private fun kindOf(unit: LearningUnit): StepKind = when (unit) {
        is LearningUnit.Summary -> StepKind.LESSON
        is LearningUnit.Practice -> when (unit.kind) {
            PracticeKind.MINI -> StepKind.MINI_TEST
            PracticeKind.TOPIC -> StepKind.TOPIC_TEST
            PracticeKind.FINAL -> StepKind.FINAL_TEST
        }
    }

    private suspend fun summary(topic: Topic, done: Set<Long>): TopicSummary {
        val units = unitsBelow(topic)
        return TopicSummary(
            topic = topic,
            lessons = units.count { it is LearningUnit.Summary },
            steps = StepCount(units.count { it.id in done }, units.size),
            childTopics = content.childTopics(topic.id).orThrow().size,
        )
    }

    private suspend fun count(topic: Topic, done: Set<Long>): StepCount {
        val units = unitsBelow(topic)
        return StepCount(units.count { it.id in done }, units.size)
    }

    /** The published units of a topic and of every published topic below it. */
    private suspend fun unitsBelow(topic: Topic): List<LearningUnit> =
        content.units(topic.id).orThrow() +
            content.childTopics(topic.id).orThrow().flatMap { unitsBelow(it) }

    private suspend fun completedUnits(): Set<Long> =
        progress.unitProgress().orThrow().filter { it.status == UnitProgressStatus.COMPLETED }.map { it.learningUnitId }.toSet()
}
