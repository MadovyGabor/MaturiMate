package com.madovygabor.maturimate.features.learning.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.learning.domain.LearningError
import com.madovygabor.maturimate.features.learning.domain.model.AttemptAnswer
import com.madovygabor.maturimate.features.learning.domain.model.AttemptStatus
import com.madovygabor.maturimate.features.learning.domain.model.AttemptType
import com.madovygabor.maturimate.features.learning.domain.model.DailyActivity
import com.madovygabor.maturimate.features.learning.domain.model.Streak
import com.madovygabor.maturimate.features.learning.domain.model.TaskProgress
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgress
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgressStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.fail
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class ProgressRepositoryImplTest {

    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) }
    private val database = UserDatabase(driver)
    private val repository = ProgressRepositoryImpl(database, LocalUserProvider(database), Dispatchers.Unconfined)

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

    private fun answer(attemptId: String, taskId: Long, correct: Boolean, at: Long = 1_000) =
        AttemptAnswer(attemptId, taskId, correct, if (correct) 2 else 0, 2, at(at), "1=B")

    @Test
    fun answersOfARoundAreRecordedAndListedInTheOrderTheyWereGiven() = runBlocking {
        val id = repository.startAttempt(AttemptType.UNIT_ROUND, null, 51, at(1_000)).data()
        repository.recordAnswer(answer(id, 101, correct = false, at = 3_000)).data()
        repository.recordAnswer(answer(id, 100, correct = true, at = 2_000)).data()

        val answers = repository.answersOfAttempt(id).data()
        assertEquals(listOf(100L, 101L), answers.map { it.taskId })
        assertEquals(listOf(true, false), answers.map { it.isCorrect })
        assertEquals(AttemptAnswer(id, 100, true, 2, 2, at(2_000), "1=B"), answers.first())
    }

    @Test
    fun aSecondAnswerToTheSameTaskReplacesTheFirst() = runBlocking {
        val id = repository.startAttempt(AttemptType.UNIT_ROUND, null, 51, at(1_000)).data()
        repository.recordAnswer(answer(id, 100, correct = false)).data()
        repository.recordAnswer(answer(id, 100, correct = true)).data()

        assertEquals(listOf(true), repository.answersOfAttempt(id).data().map { it.isCorrect })
    }

    @Test
    fun anAnswerNeedsItsAttemptAndSaneAnswers() = runBlocking {
        // no such round (the foreign key), and more points than possible (ck_attempt_answer_points)
        assertIs<OperationResult.Error<*, *>>(repository.recordAnswer(answer("no-such-round", 100, correct = true)))
        val id = repository.startAttempt(AttemptType.UNIT_ROUND, null, 51, at(1_000)).data()
        assertIs<OperationResult.Error<*, *>>(repository.recordAnswer(answer(id, 100, correct = true).copy(earnedPoints = 5)))
        Unit
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
    fun taskProgressRoundTrips() = runBlocking {
        assertNull(repository.taskProgress(100).data())
        val first = TaskProgress(100, 1, 2, 0, at(1_000))
        val second = TaskProgress(101, 3, 0, 3, at(1_000))
        repository.saveTaskProgress(first).data()
        repository.saveTaskProgress(second).data()

        assertEquals(first, repository.taskProgress(100).data())
        assertEquals(second, repository.taskProgress(101).data())
    }

    @Test
    fun answersAreCountedInTheDatabaseAndARightOneRaisesTheStreak() = runBlocking {
        repository.recordTaskAnswer(100, correct = true, answeredAt = at(1_000)).data()
        repository.recordTaskAnswer(100, correct = true, answeredAt = at(2_000)).data()
        repository.recordTaskAnswer(100, correct = false, answeredAt = at(3_000)).data()
        repository.recordTaskAnswer(100, correct = true, answeredAt = at(4_000)).data()

        assertEquals(TaskProgress(100, 3, 1, 1, at(4_000)), repository.taskProgress(100).data())
        // the mistakes are the tasks whose latest answer is wrong
        repository.recordTaskAnswer(101, correct = false, answeredAt = at(5_000)).data()
        assertEquals(listOf(101L), repository.mistakeTaskIds().data())
    }

    @Test
    fun theProgressOfOneUnitIsReadAlone() = runBlocking {
        assertNull(repository.unitProgressOf(51).data())
        repository.saveUnitProgress(UnitProgress(51, UnitProgressStatus.IN_PROGRESS, at(1_000), null)).data()
        repository.saveUnitProgress(UnitProgress(52, UnitProgressStatus.COMPLETED, at(1_000), at(2_000))).data()

        assertEquals(UnitProgressStatus.IN_PROGRESS, repository.unitProgressOf(51).data()!!.status)
        assertNull(repository.unitProgressOf(53).data())
    }

    @Test
    fun aRoundLeftOpenIsClosedWhenTheNextOneStarts() = runBlocking {
        val killed = repository.startAttempt(AttemptType.UNIT_ROUND, null, 51, at(1_000)).data()
        val finished = repository.startAttempt(AttemptType.UNIT_ROUND, null, 52, at(2_000)).data()
        repository.finishAttempt(finished, AttemptStatus.FINISHED, at(3_000), 1, 1).data()
        val next = repository.startAttempt(AttemptType.UNIT_ROUND, null, 53, at(4_000)).data()

        val byId = repository.attempts().data().associateBy { it.id }
        assertEquals(AttemptStatus.ABANDONED, byId.getValue(killed).status)
        assertEquals(AttemptStatus.FINISHED, byId.getValue(finished).status)
        assertEquals(AttemptStatus.IN_PROGRESS, byId.getValue(next).status)
        // a closed round has a finish time that is not before its start
        assertEquals(true, byId.getValue(killed).finishedAt!! >= byId.getValue(killed).startedAt)
    }

    @Test
    fun theStreakIsChangedInOneTransactionAndLeftAloneOnNull() = runBlocking {
        repository.updateStreak { null }.data()
        assertNull(repository.streak().data())

        repository.updateStreak { earlier -> assertNull(earlier); Streak(1, 1, LocalDate(2026, 10, 28)) }.data()
        repository.updateStreak { earlier -> earlier!!.copy(currentStreak = 2, longestStreak = 2) }.data()
        repository.updateStreak { null }.data()

        assertEquals(Streak(2, 2, LocalDate(2026, 10, 28)), repository.streak().data())
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
        repository.addActivity(DailyActivity(day, secondsStudied = 3, tasksAnswered = 5)).data()
        repository.addActivity(DailyActivity(day, secondsStudied = 2, tasksAnswered = 5)).data()

        val total = repository.activity(day, day).data().single()
        assertEquals(DailyActivity(day, 5, 10), total)
    }

    @Test
    fun theStudyTimeOfTwoDevicesOfOneAccountAddsUp() = runBlocking {
        // the rows of the other device arrive with the sync; here they are written the way the sync will
        val day = LocalDate(2026, 10, 28)
        repository.addActivity(DailyActivity(day, secondsStudied = 60, tasksAnswered = 3)).data()
        driver.execute(
            null,
            "INSERT INTO activity_log (user_id, activity_date, device_id, seconds_studied, tasks_answered) " +
                "SELECT user_id, activity_date, 'another-device', 90, 4 FROM activity_log",
            0,
        )

        assertEquals(1, database.activityQueries.selectActivity(LocalUserProvider(database).userId(), "2026-10-28", "2026-10-28").executeAsList().size)
        assertEquals(DailyActivity(day, 150, 7), repository.activity(day, day).data().single())
        // this device keeps its own row
        repository.addActivity(DailyActivity(day, secondsStudied = 10, tasksAnswered = 0)).data()
        assertEquals(DailyActivity(day, 160, 7), repository.activity(day, day).data().single())
    }

    @Test
    fun roundIdsAreMadeByTheDeviceAndNeverCollideAcrossDevices() = runBlocking {
        fun device(name: String): ProgressRepositoryImpl {
            val other = UserDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) })
            return ProgressRepositoryImpl(other, LocalUserProvider(other, newGuestId = { "user-1" }, newDeviceId = { name }), Dispatchers.Unconfined)
        }
        val phone = device("phone")
        val desktop = device("desktop")
        val ids = (1..5).flatMap {
            listOf(
                phone.startAttempt(AttemptType.UNIT_ROUND, null, 51, at(1_000)).data(),
                desktop.startAttempt(AttemptType.UNIT_ROUND, null, 51, at(1_000)).data(),
            )
        }
        assertEquals(10, ids.toSet().size)
    }

    private fun longOf(sql: String): Long = driver.executeQuery(
        null, sql, { cursor -> app.cash.sqldelight.db.QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null) }, 0,
    ).value ?: fail("no row for: $sql")

    @Test
    fun everyWriteMovesTheChangeTime() = runBlocking {
        // the change time is set to 0 by hand; a write must set it to the time of the write again
        repository.saveUnitProgress(UnitProgress(51, UnitProgressStatus.IN_PROGRESS, at(1_000), null)).data()
        driver.execute(null, "UPDATE user_unit_progress SET updated_at = 0", 0)
        repository.saveUnitProgress(UnitProgress(51, UnitProgressStatus.COMPLETED, at(1_000), at(5_000))).data()
        assertNotEquals(0L, longOf("SELECT updated_at FROM user_unit_progress"))

        repository.saveStreak(Streak(1, 1, LocalDate(2026, 10, 28))).data()
        driver.execute(null, "UPDATE user_streak SET updated_at = 0", 0)
        repository.saveStreak(Streak(2, 2, LocalDate(2026, 10, 29))).data()
        assertNotEquals(0L, longOf("SELECT updated_at FROM user_streak"))

        repository.addActivity(DailyActivity(LocalDate(2026, 10, 28), 3, 1)).data()
        driver.execute(null, "UPDATE activity_log SET updated_at = 0", 0)
        repository.addActivity(DailyActivity(LocalDate(2026, 10, 28), 3, 1)).data()
        assertNotEquals(0L, longOf("SELECT updated_at FROM activity_log"))
    }

    @Test
    fun brokenStreakIsRejectedByTheDatabase() = runBlocking {
        // longest_streak >= current_streak (ck_user_streak_values)
        assertIs<OperationResult.Error<*, *>>(repository.saveStreak(Streak(5, 2, null)))
        Unit
    }
}
