package com.madovygabor.maturimate.features.learning.domain

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.learning.data.ProgressRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class StudyTrackerTest {

    private val database = UserDatabase(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) }
    )
    private val progress = ProgressRepositoryImpl(database, LocalUserProvider(database), Dispatchers.Unconfined)
    private var goal: Int? = 10
    private var zone: TimeZone = TimeZone.UTC
    private var clock = Instant.parse("2026-11-04T10:00:00Z")
    private val tracker = StudyTracker(progress, { StudySettings(goal, zone) }) { clock }

    private fun day(iso: String) {
        clock = Instant.parse("${iso}T10:00:00Z")
    }

    private fun <T> OperationResult<T, LearningError>.data(): T = when (this) {
        is OperationResult.Success -> data
        is OperationResult.Error -> fail("expected success, got $error")
    }

    @Test
    fun goalIsReachedByStudySecondsAcrossSessions() = runBlocking {
        tracker.record(seconds = 300, tasks = 4).data()
        assertFalse(tracker.today().data().reached)
        assertEquals(0, tracker.currentStreak().data())

        tracker.record(seconds = 300).data()
        val today = tracker.today().data()
        assertTrue(today.reached)
        assertEquals(1f, today.fraction)
        assertEquals(1, tracker.currentStreak().data())
    }

    @Test
    fun answeredTasksDoNotCountTowardsTheGoal() = runBlocking {
        tracker.record(seconds = 0, tasks = 500).data()
        assertEquals(0, tracker.today().data().secondsStudied)
        assertEquals(0, tracker.currentStreak().data())
    }

    @Test
    fun streakGrowsOnConsecutiveDaysAndCountsADayOnce() = runBlocking {
        day("2026-11-01"); tracker.record(600).data(); tracker.record(600).data()
        day("2026-11-02"); tracker.record(600).data()
        day("2026-11-03"); tracker.record(600).data()
        assertEquals(3, tracker.currentStreak().data())
        assertEquals(3, progress.streak().data()!!.longestStreak)
    }

    @Test
    fun streakShowsZeroAfterAMissedDayAndRestartsAtOne() = runBlocking {
        day("2026-11-01"); tracker.record(600).data()
        day("2026-11-02"); tracker.record(600).data()
        // the 3rd is still open: the streak is alive
        day("2026-11-03"); assertEquals(2, tracker.currentStreak().data())
        // the 4th: a whole day was missed
        day("2026-11-04"); assertEquals(0, tracker.currentStreak().data())

        tracker.record(600).data()
        assertEquals(1, tracker.currentStreak().data())
        assertEquals(2, progress.streak().data()!!.longestStreak)
    }

    @Test
    fun theDayFollowsTheUsersTimeZone() = runBlocking {
        zone = TimeZone.of("Europe/Bratislava")
        // 23:30 UTC on the 1st is already the 2nd in Bratislava
        clock = Instant.parse("2026-11-01T23:30:00Z")
        tracker.record(600).data()
        assertEquals(LocalDate(2026, 11, 2), progress.streak().data()!!.lastActivityDate)
    }

    @Test
    fun withoutAChosenGoalTheDefaultApplies() = runBlocking {
        goal = null
        assertEquals(StudyTracker.DEFAULT_GOAL_MINUTES * 60, tracker.today().data().goalSeconds)
    }

    @Test
    fun theWeekStartsOnMondayAndMarksReachedMissedTodayAndLaterDays() = runBlocking {
        // the 4th of November 2026 is a Wednesday
        day("2026-11-02"); tracker.record(600).data()
        day("2026-11-04"); tracker.record(60).data()

        val week = tracker.week().data()
        assertEquals(LocalDate(2026, 11, 2), week.first().date)
        assertEquals(LocalDate(2026, 11, 8), week.last().date)
        assertEquals(
            listOf(WeekDayState.DONE, WeekDayState.MISSED, WeekDayState.TODAY, WeekDayState.LATER, WeekDayState.LATER, WeekDayState.LATER, WeekDayState.LATER),
            week.map { it.state },
        )
        tracker.record(600).data()
        assertEquals(WeekDayState.DONE, tracker.week().data()[2].state)
    }

    @Test
    fun loweringTheGoalAfterStudyingCountsTheDayForTheStreak() = runBlocking {
        tracker.record(480).data()
        assertEquals(0, tracker.currentStreak().data())

        // 8 minutes were studied; the goal is lowered to 5 afterwards
        goal = 5
        assertTrue(tracker.today().data().reached)
        assertEquals(1, tracker.currentStreak().data())
        // and tomorrow goes on from it, not from zero
        day("2026-11-05"); tracker.record(300).data()
        assertEquals(2, tracker.currentStreak().data())
    }

    @Test
    fun aStreakIsKeptWhenTheLastDoneDayIsAheadOfToday() = runBlocking {
        // done on the 5th in Auckland; the phone is in Lisbon now, where it is still the 4th
        zone = TimeZone.of("Pacific/Auckland")
        clock = Instant.parse("2026-11-04T20:00:00Z")
        tracker.record(600).data()
        assertEquals(LocalDate(2026, 11, 5), progress.streak().data()!!.lastActivityDate)

        zone = TimeZone.of("Europe/Lisbon")
        clock = Instant.parse("2026-11-04T23:00:00Z")
        tracker.record(600).data()

        val streak = progress.streak().data()!!
        assertEquals(1, streak.currentStreak)
        assertEquals(LocalDate(2026, 11, 5), streak.lastActivityDate)
        assertEquals(1, tracker.currentStreak().data())
    }

    @Test
    fun theTrackerCountsInTheZoneOfTheSettings() = runBlocking {
        zone = TimeZone.of("Pacific/Auckland")
        assertEquals(TimeZone.of("Pacific/Auckland"), tracker.timeZone())
        // 23:30 UTC is already the next day in Auckland
        clock = Instant.parse("2026-11-04T23:30:00Z")
        assertEquals(LocalDate(2026, 11, 5), tracker.today().data().date)
    }

    @Test
    fun hadStreakIsTrueOnceAStreakHasStarted() = runBlocking {
        assertFalse(tracker.hadStreak())
        tracker.record(600).data()
        assertTrue(tracker.hadStreak())
    }
}
