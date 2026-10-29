package com.madovygabor.maturimate.features.learning.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.learning.domain.LearningError
import com.madovygabor.maturimate.features.learning.domain.model.AttemptStatus
import com.madovygabor.maturimate.features.learning.domain.model.AttemptType
import com.madovygabor.maturimate.features.learning.domain.model.DailyActivity
import com.madovygabor.maturimate.features.learning.domain.model.Streak
import com.madovygabor.maturimate.features.learning.domain.model.TaskProgress
import com.madovygabor.maturimate.features.learning.domain.model.TaskProgressStatus
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgress
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgressStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.fail
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class ProgressRepositoryImplTest {

    private val database = UserDatabase(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) }
    )
    private val repository = ProgressRepositoryImpl(database, Dispatchers.Unconfined)

    private fun at(millis: Long) = Instant.fromEpochMilliseconds(millis)

    private fun <T> OperationResult<T, LearningError>.data(): T = when (this) {
        is OperationResult.Success -> data
        is OperationResult.Error -> fail("expected success, got $error")
    }

    @Test
    fun attemptIsStartedAndFinished() = runBlocking {
        val id = repository.startAttempt(AttemptType.UNIT_ROUND, null, 51, at(1_000)).data()
        repository.finishAttempt(id, AttemptStatus.FINISHED, at(2_000), earnedPoints = 4, maxPoints = 5).data()

        val attempt = repository.attemptsOfUnit(51).data().single()
        assertEquals(id, attempt.id)
        assertEquals(AttemptStatus.FINISHED, attempt.status)
        assertEquals(at(2_000), attempt.finishedAt)
        assertEquals(4, attempt.earnedPoints)
    }

    @Test
    fun invalidAttemptIsADatabaseError() = runBlocking {
        // a unit round needs the unit (ck_attempt_scope)
        assertEquals(
            OperationResult.Error(LearningError.DATABASE_ERROR),
            repository.startAttempt(AttemptType.UNIT_ROUND, null, null, at(1_000)),
        )
    }

    @Test
    fun unitProgressIsSavedAndUpdated() = runBlocking {
        repository.saveUnitProgress(UnitProgress(51, UnitProgressStatus.IN_PROGRESS, at(1_000), null)).data()
        repository.saveUnitProgress(UnitProgress(51, UnitProgressStatus.COMPLETED, at(1_000), at(5_000))).data()

        val progress = repository.unitProgress().data().single()
        assertEquals(UnitProgressStatus.COMPLETED, progress.status)
        assertEquals(at(5_000), progress.completedAt)
    }

    @Test
    fun taskProgressRoundTripsAndDueTasksAreFound() = runBlocking {
        assertNull(repository.taskProgress(100).data())
        val due = TaskProgress(100, 1, 2, 0, at(1_000), at(2_000), TaskProgressStatus.LEARNING)
        val later = TaskProgress(101, 3, 0, 3, at(1_000), at(9_000), TaskProgressStatus.LEARNING)
        repository.saveTaskProgress(due).data()
        repository.saveTaskProgress(later).data()

        assertEquals(due, repository.taskProgress(100).data())
        assertEquals(listOf(100L), repository.dueTasks(now = at(5_000)).data().map { it.taskId })
    }

    @Test
    fun streakRoundTrips() = runBlocking {
        assertNull(repository.streak().data())
        val streak = Streak(currentStreak = 3, longestStreak = 7, lastActivityDate = LocalDate(2026, 10, 28))
        repository.saveStreak(streak).data()
        assertEquals(streak, repository.streak().data())
    }

    @Test
    fun activityOfADayIsAddedUp() = runBlocking {
        val day = LocalDate(2026, 10, 28)
        repository.addActivity(DailyActivity(day, xpEarned = 10, minutesStudied = 3, tasksAnswered = 5)).data()
        repository.addActivity(DailyActivity(day, xpEarned = 10, minutesStudied = 2, tasksAnswered = 5)).data()

        val total = repository.activity(day, day).data().single()
        assertEquals(DailyActivity(day, 20, 5, 10), total)
        assertEquals(20L, repository.totalXp().data())
    }

    @Test
    fun brokenStreakIsRejectedByTheDatabase() = runBlocking {
        // longest_streak >= current_streak (ck_user_streak_values)
        assertIs<OperationResult.Error<*, *>>(repository.saveStreak(Streak(5, 2, null)))
        Unit
    }
}
