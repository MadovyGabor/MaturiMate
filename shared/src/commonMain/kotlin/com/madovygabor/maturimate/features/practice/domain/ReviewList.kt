package com.madovygabor.maturimate.features.practice.domain

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.learning.domain.ContentRepository
import com.madovygabor.maturimate.features.learning.domain.LearningError
import com.madovygabor.maturimate.features.learning.domain.ProgressRepository
import com.madovygabor.maturimate.features.learning.domain.ReadFailure
import com.madovygabor.maturimate.features.learning.domain.guarded
import com.madovygabor.maturimate.features.learning.domain.model.LearningUnit
import com.madovygabor.maturimate.features.learning.domain.model.Subject
import com.madovygabor.maturimate.features.learning.domain.model.Topic
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgressStatus
import com.madovygabor.maturimate.features.learning.domain.orThrow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** A finished topic the student has not seen for [daysAgo] days. */
data class ReviewItem(val topic: Topic, val subject: Subject, val daysAgo: Int)

/**
 * The finished topics the student has not seen for a long time, the oldest first (9a.5).
 *
 * "Last seen" is not stored on its own; it is the newest of what is already recorded for the topic: the time its
 * units were finished (a lesson read to the end again saves a new time) and the time of every answer to its tasks, in
 * any round (a test, practice, mistakes, a review). Opening a lesson without reading it to the end does not count
 * (decided 2026-11-05). A review round answers the topic's tasks, so it takes the topic off the list by itself.
 */
@OptIn(ExperimentalTime::class)
class ReviewList(
    private val content: ContentRepository,
    private val progress: ProgressRepository,
    private val now: () -> Instant = { Clock.System.now() },
    private val timeZone: suspend () -> TimeZone = { TimeZone.currentSystemDefault() },
) {

    suspend fun load(): OperationResult<List<ReviewItem>, LearningError> = guarded {
        val tree = content.publishedTree()
        val units = progress.unitProgress().orThrow()
        val done = units.filter { it.status == UnitProgressStatus.COMPLETED }.map { it.learningUnitId }.toSet()
        val unitTimes = units.mapNotNull { unit -> unit.completedAt?.let { unit.learningUnitId to it } }.toMap()
        val lastAnswers = progress.lastAnswerTimes().orThrow()

        // the newest answer of each topic, through the topic of each task answered
        val answeredInTopic = mutableMapOf<Long, Instant>()
        for ((taskId, at) in lastAnswers) {
            val task = when (val result = content.task(taskId)) {
                is OperationResult.Success -> result.data
                is OperationResult.Error -> if (result.error == LearningError.NOT_FOUND) continue else throw ReadFailure(result.error)
            }
            val earlier = answeredInTopic[task.topicId]
            if (earlier == null || at > earlier) answeredInTopic[task.topicId] = at
        }

        val zone = timeZone()
        val today = now().toLocalDateTime(zone).date
        tree.leaves.mapNotNull { topicId ->
            val topicUnits: List<LearningUnit> = content.units(topicId).orThrow()
            // only finished topics are reviewed
            if (topicUnits.isEmpty() || !topicUnits.all { it.id in done }) return@mapNotNull null
            val lastSeen = (topicUnits.mapNotNull { unitTimes[it.id] } + listOfNotNull(answeredInTopic[topicId])).maxOrNull()
                ?: return@mapNotNull null
            val days = lastSeen.toLocalDateTime(zone).date.daysUntil(today)
            if (days < DUE_AFTER_DAYS) null else ReviewItem(tree.topics.getValue(topicId), tree.subjectOf.getValue(topicId), days)
        }
            .sortedWith(compareByDescending<ReviewItem> { it.daysAgo }.thenBy { it.topic.title })
            .take(SHOWN)
    }

    companion object {
        /**
         * Days since a finished topic was last seen before it is offered for review. Low for the beta, so it can be
         * tried the next day; set back to 14 before the public release (ROADMAP 19.1).
         */
        const val DUE_AFTER_DAYS = 1

        /** The most topics offered at once. */
        const val SHOWN = 5

        /** The tasks of one review round. */
        const val ROUND = 5
    }
}
