package com.madovygabor.maturimate.features.learning.domain

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.learning.domain.model.AttemptStatus
import com.madovygabor.maturimate.features.learning.domain.model.LearningUnit
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgressStatus
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * A finished lesson or test round, for the "last activities" of the home screen.
 * [passed] is empty for a lesson; for a test it says whether the round was passed (every task right).
 */
@OptIn(ExperimentalTime::class)
data class RecentActivity(
    val unitId: Long,
    val title: String,
    /** The topic the lesson or test belongs to. */
    val topicTitle: String,
    val isTest: Boolean,
    val at: Instant,
    val passed: Boolean?,
)

/** The latest finished lessons and test rounds, newest first. Units that are no longer published are left out. */
@OptIn(ExperimentalTime::class)
class RecentActivities(
    private val progress: ProgressRepository,
    private val content: ContentRepository,
) {

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

        for (attempt in attempts) {
            val id = attempt.learningUnitId ?: continue
            val finished = attempt.finishedAt ?: continue
            if (attempt.status != AttemptStatus.FINISHED) continue
            val unit = unit(id) ?: continue
            found += RecentActivity(id, unit.title, topic(unit), isTest = true, at = finished, passed = attempt.earnedPoints == attempt.maxPoints)
        }
        for (done in units) {
            val completed = done.completedAt ?: continue
            if (done.status != UnitProgressStatus.COMPLETED) continue
            val unit = unit(done.learningUnitId) as? LearningUnit.Summary ?: continue
            found += RecentActivity(unit.id, unit.title, topic(unit), isTest = false, at = completed, passed = null)
        }
        return OperationResult.Success(found.sortedByDescending { it.at }.take(limit))
    }
}
