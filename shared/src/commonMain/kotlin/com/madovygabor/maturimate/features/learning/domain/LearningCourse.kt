package com.madovygabor.maturimate.features.learning.domain

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.learning.domain.model.LearningUnit
import com.madovygabor.maturimate.features.learning.domain.model.PathStep
import com.madovygabor.maturimate.features.learning.domain.model.PracticeKind
import com.madovygabor.maturimate.features.learning.domain.model.StepCount
import com.madovygabor.maturimate.features.learning.domain.model.StepKind
import com.madovygabor.maturimate.features.learning.domain.model.StepState
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
            TopicLevel.Path(topic.title, parentTitle, steps(units, done))
        } else {
            val below = children.fold(StepCount.ZERO) { sum, child -> sum + count(child, done) }
            val final = units.filterIsInstance<LearningUnit.Practice>().firstOrNull { it.kind == PracticeKind.FINAL }
            val finalStep = final?.let {
                val state = when {
                    it.id in done -> StepState.DONE
                    below.done == below.total -> StepState.CURRENT
                    else -> StepState.TODO
                }
                PathStep(it, StepKind.FINAL_TEST, state)
            }
            TopicLevel.Branch(topic.title, parentTitle, children.map { summary(it, done) }, finalStep)
        }
    }

    private fun steps(units: List<LearningUnit>, done: Set<Long>): List<PathStep> {
        var currentTaken = false
        return units.sortedBy { it.sortOrder }.map { unit ->
            val state = when {
                unit.id in done -> StepState.DONE
                !currentTaken -> StepState.CURRENT.also { currentTaken = true }
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

/** Carries the error of a failed read out of the middle of a calculation. */
private class CourseFailure(val error: LearningError) : Exception()

private fun <T> OperationResult<T, LearningError>.orThrow(): T = when (this) {
    is OperationResult.Success -> data
    is OperationResult.Error -> throw CourseFailure(error)
}

private suspend fun <T> guarded(block: suspend () -> T): OperationResult<T, LearningError> =
    try {
        OperationResult.Success(block())
    } catch (e: CourseFailure) {
        OperationResult.Error(e.error)
    }
