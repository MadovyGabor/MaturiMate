package com.madovygabor.maturimate.features.profileDetails.domain

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.learning.domain.LearningError
import com.madovygabor.maturimate.features.learning.domain.RecentActivities
import com.madovygabor.maturimate.features.learning.domain.RecentActivity
import com.madovygabor.maturimate.features.learning.domain.StudyTracker
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime

/**
 * The heading an activity of the full history sits under: today, yesterday, a few days ago, a few weeks ago, and
 * for anything a month old or more the month (decided on 2026-11-04).
 */
sealed interface HistoryGroup {
    data object Today : HistoryGroup
    data object Yesterday : HistoryGroup

    /** 2 to 6 days ago. */
    data class DaysAgo(val days: Int) : HistoryGroup

    /** 1 to 3 weeks ago (7 to 27 days). */
    data class WeeksAgo(val weeks: Int) : HistoryGroup

    /** 28 days ago or earlier: the month; the screen adds the year when it is not this year's. */
    data class InMonth(val year: Int, val month: Month) : HistoryGroup

    companion object {
        /** The group of an activity done on [day], seen on [today]. A day after today (a clock change) counts as today. */
        fun of(day: LocalDate, today: LocalDate): HistoryGroup = when (val days = day.daysUntil(today)) {
            in Int.MIN_VALUE..0 -> Today
            1 -> Yesterday
            in 2..6 -> DaysAgo(days)
            in 7..27 -> WeeksAgo(days / 7)
            else -> InMonth(day.year, day.month)
        }
    }
}

/** The activities under one heading, newest first. */
@OptIn(ExperimentalTime::class)
data class HistorySection(val group: HistoryGroup, val activities: List<RecentActivity>)

/** The full history of the profile: every finished lesson and test round, newest first, under day headings. */
@OptIn(ExperimentalTime::class)
data class History(val sections: List<HistorySection>, val today: LocalDate)

/** Builds the full history from [RecentActivities]; the days are the days in the user's time zone. */
@OptIn(ExperimentalTime::class)
class ActivityHistory(
    private val recent: RecentActivities,
    private val tracker: StudyTracker,
    private val timeZone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {

    suspend fun load(): OperationResult<History, LearningError> {
        val today = when (val result = tracker.today()) {
            is OperationResult.Success -> result.data.date
            is OperationResult.Error -> return OperationResult.Error(result.error)
        }
        val all = when (val result = recent.all()) {
            is OperationResult.Success -> result.data
            is OperationResult.Error -> return OperationResult.Error(result.error)
        }
        return OperationResult.Success(History(sections(all, today, timeZone()), today))
    }

    companion object {
        /** Puts [activities] (newest first) under their headings, keeping the order. */
        fun sections(activities: List<RecentActivity>, today: LocalDate, zone: TimeZone): List<HistorySection> =
            activities
                .groupBy { HistoryGroup.of(it.at.toLocalDateTime(zone).date, today) }
                .map { (group, items) -> HistorySection(group, items) }
    }
}
