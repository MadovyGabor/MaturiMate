package com.madovygabor.maturimate.features.learning.domain

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.learning.data.ProgressRepositoryImpl
import com.madovygabor.maturimate.features.learning.domain.model.AttemptStatus
import com.madovygabor.maturimate.features.learning.domain.model.AttemptType
import com.madovygabor.maturimate.features.learning.domain.model.KeySlot
import com.madovygabor.maturimate.features.learning.domain.model.OptionRole
import com.madovygabor.maturimate.features.learning.domain.model.Task
import com.madovygabor.maturimate.features.learning.domain.model.TaskFormat
import com.madovygabor.maturimate.features.learning.domain.model.TaskOption
import com.madovygabor.maturimate.features.learning.domain.model.TaskProgressStatus
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgress
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgressStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.fail
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** The practice rounds of [TestRecorder]: recorded like a test round, but they belong to no unit and finish none. */
@OptIn(ExperimentalTime::class)
class TestRecorderPracticeTest {

    private val database = UserDatabase(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) }
    )
    private val progress = ProgressRepositoryImpl(database, LocalUserProvider(database), Dispatchers.Unconfined)
    private var clock = 1_000L
    private val tracker = StudyTracker(progress, { StudySettings(1, TimeZone.UTC) }) { Instant.fromEpochMilliseconds(clock) }
    private val recorder = TestRecorder(progress, tracker) { Instant.fromEpochMilliseconds(clock) }

    private fun <T> OperationResult<T, LearningError>.data(): T = when (this) {
        is OperationResult.Success -> data
        is OperationResult.Error -> fail("expected success, got $error")
    }

    private val multipleChoice = Task(
        100, 1, TaskFormat.MC_SINGLE, "?", null, 1, null, true,
        options = listOf(TaskOption("A", "a", OptionRole.ANSWER), TaskOption("B", "b", OptionRole.ANSWER)),
        key = listOf(KeySlot(1, "B", null)), acceptedAnswers = emptyList(), attachments = emptyList(),
    )

    private suspend fun answer(attemptId: Long, label: String): RoundEntry {
        val answer = Answer.Choice(mapOf(1 to label))
        return recorder.answer(attemptId, multipleChoice, answer, AnswerChecker.check(multipleChoice, answer))
    }

    @Test
    fun aPracticeRoundHasATypeAndNoUnitAndNoTopic() = runBlocking {
        val free = recorder.startPractice(AttemptType.FREE_PRACTICE).data()
        val mistakes = recorder.startPractice(AttemptType.MISTAKE_PRACTICE).data()

        val attempts = progress.attempts().data().associateBy { it.id }
        assertEquals(AttemptType.FREE_PRACTICE, attempts.getValue(free).type)
        assertEquals(AttemptType.MISTAKE_PRACTICE, attempts.getValue(mistakes).type)
        attempts.values.forEach {
            assertNull(it.learningUnitId)
            assertNull(it.topicId)
            assertEquals(AttemptStatus.IN_PROGRESS, it.status)
        }
    }

    @Test
    fun onlyThePracticeTypesCanStartAPracticeRound() {
        listOf(AttemptType.UNIT_ROUND, AttemptType.EXTRA_PRACTICE, AttemptType.REVIEW).forEach { type ->
            assertFailsWith<IllegalArgumentException> { runBlocking { recorder.startPractice(type) } }
        }
    }

    @Test
    fun theAnswersOfAPracticeRoundAreRecordedAndCountedLikeInATest() = runBlocking {
        val id = recorder.startPractice(AttemptType.FREE_PRACTICE).data()
        clock += 40_000
        val wrong = answer(id, "A")
        assertEquals(false, wrong.correct)

        val recorded = progress.answersOfAttempt(id).data().single()
        assertEquals(100L, recorded.taskId)
        assertEquals(false, recorded.isCorrect)
        assertEquals("1=A", recorded.givenAnswer)
        val taskProgress = progress.taskProgress(100).data()!!
        assertEquals(1, taskProgress.timesWrong)
        // the time and the task count towards the daily goal, as in a test
        assertEquals(40, tracker.today().data().secondsStudied)
    }

    @Test
    fun threeRightAnswersInARowAcrossPracticeRoundsMasterTheTask() = runBlocking {
        repeat(3) { answer(recorder.startPractice(AttemptType.FREE_PRACTICE).data(), "B") }

        val taskProgress = progress.taskProgress(100).data()!!
        assertEquals(3, taskProgress.correctStreak)
        assertEquals(TaskProgressStatus.MASTERED, taskProgress.status)
    }

    @Test
    fun finishingAPracticeRoundSavesThePointsAndFinishesNoUnit() = runBlocking {
        // unit 51 is open before the round
        progress.saveUnitProgress(UnitProgress(51, UnitProgressStatus.IN_PROGRESS, Instant.fromEpochMilliseconds(500), null)).data()
        val id = recorder.startPractice(AttemptType.FREE_PRACTICE).data()
        val entry = answer(id, "B") // every answer right

        clock = 9_000
        recorder.finishPractice(id, listOf(entry)).data()

        val attempt = progress.attempts().data().single()
        assertEquals(AttemptStatus.FINISHED, attempt.status)
        assertEquals(1, attempt.earnedPoints)
        assertEquals(1, attempt.maxPoints)
        assertEquals(Instant.fromEpochMilliseconds(9_000), attempt.finishedAt)
        // the progress of the units is untouched: nothing is done, the open unit is still open
        assertEquals(listOf(UnitProgressStatus.IN_PROGRESS), progress.unitProgress().data().map { it.status })
    }

    @Test
    fun leavingAPracticeRoundKeepsTheAnswersAndEndsItAsAbandoned() = runBlocking {
        val id = recorder.startPractice(AttemptType.MISTAKE_PRACTICE).data()
        val entry = answer(id, "B")

        recorder.abandon(id, listOf(entry)).data()

        assertEquals(AttemptStatus.ABANDONED, progress.attempts().data().single().status)
        assertEquals(1, progress.answersOfAttempt(id).data().size)
    }
}
