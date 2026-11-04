package com.madovygabor.maturimate.features.profileDetails.domain

import com.madovygabor.maturimate.features.learning.domain.RecentActivity
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class HistoryGroupTest {

    private val today = LocalDate(2026, 11, 4)
    private fun daysAgo(days: Int) = HistoryGroup.of(today.minus(DatePeriod(days = days)), today)

    @Test
    fun theFirstWeekGoesByDays() {
        assertEquals(HistoryGroup.Today, daysAgo(0))
        assertEquals(HistoryGroup.Yesterday, daysAgo(1))
        assertEquals(HistoryGroup.DaysAgo(2), daysAgo(2))
        assertEquals(HistoryGroup.DaysAgo(6), daysAgo(6))
    }

    @Test
    fun thenByWeeksUpToFourWeeks() {
        assertEquals(HistoryGroup.WeeksAgo(1), daysAgo(7))
        assertEquals(HistoryGroup.WeeksAgo(1), daysAgo(13))
        assertEquals(HistoryGroup.WeeksAgo(2), daysAgo(14))
        assertEquals(HistoryGroup.WeeksAgo(3), daysAgo(27))
    }

    @Test
    fun fourWeeksAndOlderGoByMonth() {
        assertEquals(HistoryGroup.InMonth(2026, Month.OCTOBER), daysAgo(28))
        assertEquals(HistoryGroup.InMonth(2025, Month.DECEMBER), HistoryGroup.of(LocalDate(2025, 12, 31), today))
    }

    @Test
    fun aDayAfterTodayCountsAsToday() {
        assertEquals(HistoryGroup.Today, HistoryGroup.of(LocalDate(2026, 11, 5), today))
    }

    @Test
    fun sectionsKeepTheOrderAndPutTheSameDayTogether() {
        fun activity(id: Long, at: String) = RecentActivity(id, "Lecke $id", "Szóképek", isTest = false, at = Instant.parse(at), passed = null)
        val activities = listOf(
            activity(1, "2026-11-04T09:00:00Z"),
            activity(2, "2026-11-04T08:00:00Z"),
            activity(3, "2026-11-03T20:00:00Z"),
            activity(4, "2026-10-01T10:00:00Z"),
        )
        val sections = ActivityHistory.sections(activities, today, TimeZone.UTC)
        assertEquals(listOf(HistoryGroup.Today, HistoryGroup.Yesterday, HistoryGroup.InMonth(2026, Month.OCTOBER)), sections.map { it.group })
        assertEquals(listOf(1L, 2L), sections[0].activities.map { it.unitId })
    }
}
