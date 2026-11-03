package com.madovygabor.maturimate.features.learning.domain

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.learning.data.ProgressRepositoryImpl
import com.madovygabor.maturimate.features.learning.domain.model.AcceptedAnswer
import com.madovygabor.maturimate.features.learning.domain.model.AttemptStatus
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class TestRecorderTest {

    private val database = UserDatabase(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) }
    )
    private val progress = ProgressRepositoryImpl(database, LocalUserProvider(database), Dispatchers.Unconfined)
    private var clock = 1_000L
    private val recorder = TestRecorder(progress) { Instant.fromEpochMilliseconds(clock) }

    private fun <T> OperationResult<T, LearningError>.data(): T = when (this) {
        is OperationResult.Success -> data
        is OperationResult.Error -> fail("expected success, got $error")
    }

    private val multipleChoice = Task(
        100, 1, TaskFormat.MC_SINGLE, "?", null, 1, null, true,
        options = listOf(TaskOption("A", "a", OptionRole.ANSWER), TaskOption("B", "b", OptionRole.ANSWER)),
        key = listOf(KeySlot(1, "B", null)), acceptedAnswers = emptyList(), attachments = emptyList(),
    )
    private val matching = Task(
        101, 1, TaskFormat.MATCHING, "?", null, 2, null, true,
        options = emptyList(), key = listOf(KeySlot(1, "A", "1"), KeySlot(2, "B", "2")), acceptedAnswers = emptyList(), attachments = emptyList(),
    )
    private val typed = Task(
        102, 1, TaskFormat.SHORT_ANSWER, "?", null, 1, null, true,
        options = emptyList(), key = emptyList(), acceptedAnswers = listOf(AcceptedAnswer(1, "x", false)), attachments = emptyList(),
    )

    private suspend fun answer(attemptId: Long, task: Task, answer: Answer) =
        recorder.answer(attemptId, task, answer, AnswerChecker.check(task, answer))

    @Test
    fun startingARoundCreatesAnAttemptOfTheUnit() = runBlocking {
        val id = recorder.start(51).data()

        val attempt = progress.attemptsOfUnit(51).data().single()
        assertEquals(id, attempt.id)
        assertEquals(AttemptStatus.IN_PROGRESS, attempt.status)
    }

    @Test
    fun everyAnswerIsRecordedWithItsPointsAndTheAnswerGiven() = runBlocking {
        val id = recorder.start(51).data()
        answer(id, multipleChoice, Answer.Choice(mapOf(1 to "B")))
        clock = 2_000
        answer(id, typed, Answer.Typed(mapOf(1 to "y")))

        val answers = progress.answersOfAttempt(id).data()
        assertEquals(listOf(100L, 102L), answers.map { it.taskId })
        assertEquals(listOf(true, false), answers.map { it.isCorrect })
        assertEquals(listOf(1, 0), answers.map { it.earnedPoints })
        assertEquals(listOf("1=B", "1=y"), answers.map { it.givenAnswer })
    }

    @Test
    fun aTaskWithSeveralSlotsEarnsByTheShareOfRightSlots() = runBlocking {
        val id = recorder.start(51).data()
        val entry = answer(id, matching, Answer.Choice(mapOf(1 to "A", 2 to "A")))

        assertEquals(1, entry.earnedPoints)
        assertEquals(2, entry.maxPoints)
        assertEquals(false, entry.correct)
    }

    @Test
    fun theProgressOfATaskCountsRightAndWrongAndTheStreak() = runBlocking {
        val id = recorder.start(51).data()
        answer(id, multipleChoice, Answer.Choice(mapOf(1 to "A")))
        var progressOfTask = progress.taskProgress(100).data()!!
        assertEquals(0, progressOfTask.timesCorrect)
        assertEquals(1, progressOfTask.timesWrong)
        assertEquals(0, progressOfTask.correctStreak)

        // a later round: three right answers in a row master the task
        repeat(3) { round ->
            val next = recorder.start(51).data()
            answer(next, multipleChoice, Answer.Choice(mapOf(1 to "B")))
            progressOfTask = progress.taskProgress(100).data()!!
            assertEquals(round + 1, progressOfTask.correctStreak)
        }
        assertEquals(3, progressOfTask.timesCorrect)
        assertEquals(1, progressOfTask.timesWrong)
        assertEquals(TaskProgressStatus.MASTERED, progressOfTask.status)
        assertNull(progressOfTask.nextReviewAt)
    }

    @Test
    fun aWrongAnswerBreaksTheStreak() = runBlocking {
        val id = recorder.start(51).data()
        answer(id, multipleChoice, Answer.Choice(mapOf(1 to "B")))
        answer(recorder.start(51).data(), multipleChoice, Answer.Choice(mapOf(1 to "A")))

        val progressOfTask = progress.taskProgress(100).data()!!
        assertEquals(0, progressOfTask.correctStreak)
        assertEquals(TaskProgressStatus.LEARNING, progressOfTask.status)
    }

    @Test
    fun finishingClosesTheAttemptWithItsPointsAndMarksTheUnitDone() = runBlocking {
        val id = recorder.start(51).data()
        val entries = listOf(
            answer(id, multipleChoice, Answer.Choice(mapOf(1 to "B"))),
            answer(id, matching, Answer.Choice(mapOf(1 to "A", 2 to "B"))),
        )
        clock = 9_000

        recorder.finish(id, 51, entries).data()

        val attempt = progress.attemptsOfUnit(51).data().single()
        assertEquals(AttemptStatus.FINISHED, attempt.status)
        assertEquals(3, attempt.earnedPoints)
        assertEquals(3, attempt.maxPoints)
        val unit = progress.unitProgress().data().single()
        assertEquals(UnitProgressStatus.COMPLETED, unit.status)
        assertEquals(Instant.fromEpochMilliseconds(9_000), unit.completedAt)
    }

    @Test
    fun aRoundThatIsNotPassedIsKeptButTheUnitStaysOpen() = runBlocking {
        val id = recorder.start(51).data()
        val entries = listOf(
            answer(id, multipleChoice, Answer.Choice(mapOf(1 to "B"))),
            answer(id, typed, Answer.Typed(mapOf(1 to "y"))),
            answer(id, matching, Answer.Choice(mapOf(1 to "A", 2 to "B"))),
        )

        recorder.finish(id, 51, entries).data()

        // one task wrong out of three: the attempt is finished, the unit is not done
        assertEquals(AttemptStatus.FINISHED, progress.attemptsOfUnit(51).data().single().status)
        assertEquals(emptyList(), progress.unitProgress().data())
    }

    @Test
    fun theRoundIsPassedOnlyWithEveryTaskRight() {
        fun entries(right: Int, total: Int) = List(total) { index ->
            val answerGiven = if (index < right) Answer.Choice(mapOf(1 to "B")) else Answer.Choice(mapOf(1 to "A"))
            RoundEntry(multipleChoice, answerGiven, AnswerChecker.check(multipleChoice, answerGiven), 0, 1)
        }
        assertEquals(true, TestRecorder.passed(entries(5, 5)))
        assertEquals(false, TestRecorder.passed(entries(4, 5)))
        assertEquals(false, TestRecorder.passed(entries(2, 3)))
        assertEquals(false, TestRecorder.passed(emptyList()))
    }

    @Test
    fun anEarlierStartOfTheUnitIsKeptAndADoneUnitStaysDone() = runBlocking {
        progress.saveUnitProgress(UnitProgress(51, UnitProgressStatus.COMPLETED, Instant.fromEpochMilliseconds(100), Instant.fromEpochMilliseconds(200))).data()
        val id = recorder.start(51).data()
        clock = 9_000

        recorder.finish(id, 51, emptyList()).data()

        val unit = progress.unitProgress().data().single()
        assertEquals(Instant.fromEpochMilliseconds(100), unit.startedAt)
        assertEquals(Instant.fromEpochMilliseconds(200), unit.completedAt)
    }

    @Test
    fun leavingAbandonsTheAttemptAndLeavesTheUnitOpen() = runBlocking {
        val id = recorder.start(51).data()
        val entry = answer(id, multipleChoice, Answer.Choice(mapOf(1 to "B")))

        recorder.abandon(id, listOf(entry)).data()

        val attempt = progress.attemptsOfUnit(51).data().single()
        assertEquals(AttemptStatus.ABANDONED, attempt.status)
        assertEquals(1, attempt.earnedPoints)
        assertEquals(emptyList(), progress.unitProgress().data())
        // what was answered stays recorded
        assertEquals(1, progress.answersOfAttempt(id).data().size)
    }

    @Test
    fun theAnswerIsDescribedAsShortText() {
        assertEquals("1=B;2=A", Answer.Choice(mapOf(2 to "A", 1 to "B")).describe())
        assertEquals("1=azonosít;2=x", Answer.Typed(mapOf(1 to "azonosít", 2 to "x")).describe())
    }
}
