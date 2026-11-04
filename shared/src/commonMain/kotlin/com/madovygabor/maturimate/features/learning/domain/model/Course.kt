package com.madovygabor.maturimate.features.learning.domain.model

/**
 * Models of the learning path: what the subject, topic and path screens show. They are built from the
 * content (subjects, topic tree, learning units) and the progress of the user by `LearningCourse`.
 */

/** How many steps of a topic, a part or a subject are done. */
data class StepCount(val done: Int, val total: Int) {
    val fraction: Float get() = if (total == 0) 0f else done.toFloat() / total

    operator fun plus(other: StepCount) = StepCount(done + other.done, total + other.total)

    companion object {
        val ZERO = StepCount(0, 0)
    }
}

enum class StepKind { LESSON, MINI_TEST, TOPIC_TEST, FINAL_TEST }

/** [CURRENT] is the first step not done yet; every step after it is [TODO]. */
enum class StepState { DONE, CURRENT, TODO }

/** One step of a path: a lesson or a test, with its state for the user. */
data class PathStep(val unit: LearningUnit, val kind: StepKind, val state: StepState)

/**
 * The next step the student has not done, and where it lies: [topicIds] is the way from the big part down to the
 * topic that holds the step (the path of a topic, or a parent topic for its final test), so a screen can open
 * the subject, the part and every topic on the way, as if the student had walked there.
 */
data class ContinueTarget(
    val subjectId: Long,
    val topicIds: List<Long>,
    val topicTitle: String,
    val unit: LearningUnit,
    val kind: StepKind,
)

/** A subject of the installed content with the user's progress in it. */
data class SubjectProgress(
    val subjectId: Long,
    val code: String,
    /** The big parts of the subject (its root topics). */
    val parts: Int,
    val steps: StepCount,
)

/** A topic in a list, with its progress. */
data class TopicSummary(
    val topic: Topic,
    val lessons: Int,
    val steps: StepCount,
    /** The topics directly below it; 0 for a topic with lessons and tests. */
    val childTopics: Int = 0,
) {
    /** The tests among the steps. */
    val tests: Int get() = steps.total - lessons
}

/**
 * What a level of the topic tree shows. A topic with topics below it is a [Branch], a topic without
 * is a [Path]; [title] is null at the top level of a subject (the screen shows the subject's name).
 */
sealed interface TopicLevel {
    val title: String?

    /** The title of the level above, for the header; null when that is the subject. */
    val parentTitle: String?

    /** The topics below, and the final test of the topic after them when it has one. */
    data class Branch(
        override val title: String?,
        override val parentTitle: String?,
        val topics: List<TopicSummary>,
        val finalTest: PathStep?,
    ) : TopicLevel

    /** The steps of a topic in order: lessons and tests. */
    data class Path(
        override val title: String,
        override val parentTitle: String?,
        val steps: List<PathStep>,
    ) : TopicLevel
}
