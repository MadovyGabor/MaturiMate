package com.madovygabor.maturimate.features.learning.domain

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.learning.domain.model.AttemptStatus
import com.madovygabor.maturimate.features.learning.domain.model.AttemptType
import com.madovygabor.maturimate.features.learning.domain.model.LearningUnit
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgressStatus
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** What a finished activity was: a lesson, a test round of the path, or a practice round. */
enum class ActivityKind {
    LESSON, TEST, PRACTICE, MISTAKE_PRACTICE;

    val isPractice: Boolean get() = this == PRACTICE || this == MISTAKE_PRACTICE
}

/**
 * A finished lesson, test round or practice round, for the "last activities" of the home screen and the profile and
 * for the full history. [passed] is empty for a lesson and for practice (practice has no passing); for a test it says
 * whether the round was passed (every task right). A practice round belongs to no unit ([unitId] is empty) and has no
 * title of its own (the screen names it by its [kind]); [subjectIds] are the subjects of the tasks it asked.
 */
@OptIn(ExperimentalTime::class)
data class RecentActivity(
    val unitId: Long?,
    val title: String,
    /** The topic the lesson or test belongs to; empty for practice. */
    val topicTitle: String,
    val kind: ActivityKind,
    val at: Instant,
    val passed: Boolean?,
    /** The points of a round (for "5 / 5"); empty for a lesson. */
    val earnedPoints: Int? = null,
    val maxPoints: Int? = null,
    val subjectIds: List<Long> = emptyList(),
) {
    val isTest: Boolean get() = kind == ActivityKind.TEST
}

/**
 * The finished lessons, test rounds and practice rounds, newest first. Units and topics that are no longer published
 * are left out; a round that was left in the middle (`ABANDONED`) is never listed.
 */
@OptIn(ExperimentalTime::class)
class RecentActivities(
    private val progress: ProgressRepository,
    private val content: ContentRepository,
) {

    /** Every finished lesson and test round, for the full history of the profile. */
    suspend fun all(): OperationResult<List<RecentActivity>, LearningError> = latest(limit = Int.MAX_VALUE)

    suspend fun latest(limit: Int = 3): OperationResult<List<RecentActivity>, LearningError> {
        val attempts = when (val result = progress.attempts()) {
            is OperationResult.Success -> result.data
            is OperationResult.Error -> return OperationResult.Error(result.error)
        }
        val units = when (val result = progress.unitProgress()) {
            is OperationResult.Success -> result.data
            is OperationResult.Error -> return OperationResult.Error(result.error)
        }
        val found = mutableListOf<RecentActivity>()
        val titles = mutableMapOf<Long, LearningUnit?>()
        suspend fun unit(id: Long) = titles.getOrPut(id) { (content.unit(id) as? OperationResult.Success)?.data }
        val topics = mutableMapOf<Long, String>()
        suspend fun topic(unit: LearningUnit) = topics.getOrPut(unit.topicId) {
            (content.topic(unit.topicId) as? OperationResult.Success)?.data?.title.orEmpty()
        }

        val subjects = mutableMapOf<Long, Long?>()
        suspend fun subjectOf(topicId: Long): Long? = subjects.getOrPut(topicId) {
            val topic = (content.topic(topicId) as? OperationResult.Success)?.data ?: return@getOrPut null
            topic.subjectId ?: topic.parentTopicId?.let { subjectOf(it) }
        }

        for (attempt in attempts) {
            val finished = attempt.finishedAt ?: continue
            if (attempt.status != AttemptStatus.FINISHED) continue
            val practice = when (attempt.type) {
                AttemptType.FREE_PRACTICE -> ActivityKind.PRACTICE
                AttemptType.MISTAKE_PRACTICE -> ActivityKind.MISTAKE_PRACTICE
                else -> null
            }
            if (practice != null) {
                // what a practice round covered is known from its answers: the subjects of the tasks asked
                val answers = (progress.answersOfAttempt(attempt.id) as? OperationResult.Success)?.data.orEmpty()
                val subjectIds = answers.mapNotNull { answer ->
                    (content.task(answer.taskId) as? OperationResult.Success)?.data?.let { subjectOf(it.topicId) }
                }.distinct()
                found += RecentActivity(
                    null, "", "", practice, at = finished, passed = null,
                    earnedPoints = attempt.earnedPoints, maxPoints = attempt.maxPoints, subjectIds = subjectIds,
                )
                continue
            }
            val id = attempt.learningUnitId ?: continue
            val unit = unit(id) ?: continue
            found += RecentActivity(
                id, unit.title, topic(unit), ActivityKind.TEST, at = finished, passed = attempt.earnedPoints == attempt.maxPoints,
                earnedPoints = attempt.earnedPoints, maxPoints = attempt.maxPoints,
            )
        }
        for (done in units) {
            val completed = done.completedAt ?: continue
            if (done.status != UnitProgressStatus.COMPLETED) continue
            val unit = unit(done.learningUnitId) as? LearningUnit.Summary ?: continue
            found += RecentActivity(unit.id, unit.title, topic(unit), ActivityKind.LESSON, at = completed, passed = null)
        }
        return OperationResult.Success(found.sortedByDescending { it.at }.take(limit))
    }
}
