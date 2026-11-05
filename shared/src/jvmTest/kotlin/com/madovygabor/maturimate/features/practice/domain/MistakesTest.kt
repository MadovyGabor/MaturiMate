package com.madovygabor.maturimate.features.practice.domain

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.database.content.ContentDatabase
import com.madovygabor.maturimate.database.content.Learning_unit
import com.madovygabor.maturimate.database.content.Subject
import com.madovygabor.maturimate.database.content.Task
import com.madovygabor.maturimate.database.content.Task_format
import com.madovygabor.maturimate.database.content.Task_key
import com.madovygabor.maturimate.database.content.Task_option
import com.madovygabor.maturimate.database.content.Topic
import com.madovygabor.maturimate.database.content.Unit_task
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.learning.data.ContentRepositoryImpl
import com.madovygabor.maturimate.features.learning.data.ProgressRepositoryImpl
import com.madovygabor.maturimate.features.learning.domain.Answer
import com.madovygabor.maturimate.features.learning.domain.AnswerChecker
import com.madovygabor.maturimate.features.learning.domain.LearningError
import com.madovygabor.maturimate.features.learning.domain.TestRecorder
import com.madovygabor.maturimate.features.learning.domain.model.AttemptType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The mistakes: the tasks whose latest answer is wrong. The part "Nyelvtan" (10) has "Szóképek" (11: test 51 with the
 * tasks 100, 101, 102 and the switched-off 103) and "Alakzatok" (12: test 61 with task 110), and a hidden topic (13:
 * test 81 with task 130). Nothing is finished: mistakes count in topics that are not finished too. In every task "A"
 * is right and "B" is wrong.
 */
@OptIn(ExperimentalTime::class)
class MistakesTest {

    private val database = ContentDatabase(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { ContentDatabase.Schema.create(it) }
    ).apply {
        subjectQueries.insert(Subject(1, "HU_MJL", "Magyar", "Maďarčina", "HU", null))
        topicQueries.insert(Topic(10, 1, null, "Nyelvtan", null, 1, true))
        topicQueries.insert(Topic(11, null, 10, "Szóképek", null, 1, true))
        topicQueries.insert(Topic(12, null, 10, "Alakzatok", null, 2, true))
        topicQueries.insert(Topic(13, null, 10, "Rejtett", null, 3, false))
        taskFormatQueries.insert(Task_format(1, "MC_SINGLE", "MC", "CHOICE", null))
        practice(51, 11, 100, 101, 102, 103)
        practice(61, 12, 110)
        practice(81, 13, 130)
    }

    private fun ContentDatabase.practice(unit: Long, topic: Long, vararg tasks: Long) {
        learningUnitQueries.insert(Learning_unit(unit, topic, "PRACTICE", "Teszt $unit", "MINI", 2, 3, 10, 1, true, 0))
        tasks.forEachIndexed { index, id ->
            taskQueries.insert(Task(id, topic, 1, "Kérdés $id", null, 1, null, id != 103L, 0))
            taskQueries.insertOption(Task_option(id * 10 + 1, id, "ANSWER", "A", "jó", null))
            taskQueries.insertOption(Task_option(id * 10 + 2, id, "ANSWER", "B", "rossz", null))
            taskQueries.insertKey(Task_key(id, 1, null, id * 10 + 1))
            learningUnitQueries.insertUnitTask(Unit_task(unit, id, index + 1L, true))
        }
    }

    private val content = ContentRepositoryImpl({ database }, Dispatchers.Unconfined)
    private val userDatabase = UserDatabase(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) }
    )
    private val progress = ProgressRepositoryImpl(userDatabase, LocalUserProvider(userDatabase), Dispatchers.Unconfined)
    private var clock = 1_000L
    private val recorder = TestRecorder(progress) { Instant.fromEpochMilliseconds(clock) }
    private val catalog = PracticeCatalog(content, progress)

    private fun <T> OperationResult<T, LearningError>.data(): T = when (this) {
        is OperationResult.Success -> data
        is OperationResult.Error -> fail("expected success, got $error")
    }

    /** One round (a test or practice) with the given answers: task id to the label chosen. */
    private suspend fun round(vararg answers: Pair<Long, String>, practice: Boolean = false) {
        val attempt = if (practice) recorder.startPractice(AttemptType.FREE_PRACTICE).data() else recorder.start(51).data()
        for ((id, label) in answers) {
            clock += 1_000
            val task = content.task(id).data()
            val answer = Answer.Choice(mapOf(1 to label))
            recorder.answer(attempt, task, answer, AnswerChecker.check(task, answer))
        }
    }

    /** The rule as written in DECISIONS: the tasks whose newest answer (`attempt_answer`) is wrong. */
    private suspend fun byTheNewestAnswer(): Set<Long> {
        val answers = progress.attempts().data().flatMap { progress.answersOfAttempt(it.id).data() }
        return answers.groupBy { it.taskId }.mapValues { (_, list) -> list.maxBy { it.answeredAt } }.filterValues { !it.isCorrect }.keys
    }

    @Test
    fun theQueryGivesTheTasksWhoseNewestAnswerIsWrong() = runBlocking {
        round(100L to "B", 101L to "B", 102L to "A", 110L to "B")
        // a later round: 101 right now, 102 wrong now, 110 wrong again; a practice round counts the same
        round(101L to "A", 102L to "B")
        round(110L to "B", practice = true)

        assertEquals(setOf(100L, 102L, 110L), progress.mistakeTaskIds().data().toSet())
        assertEquals(byTheNewestAnswer(), progress.mistakeTaskIds().data().toSet())
    }

    @Test
    fun aMistakeAnsweredRightLeavesTheList() = runBlocking {
        round(100L to "B")
        assertEquals(listOf(100L), catalog.allMistakes().data())
        round(100L to "A", practice = true)
        assertEquals(emptyList(), catalog.allMistakes().data())
    }

    @Test
    fun theMistakeTreeCountsMistakesInUnfinishedTopicsAndLeavesOutHiddenAndSwitchedOffTasks() = runBlocking {
        round(100L to "B", 101L to "B", 103L to "B", 110L to "B", 130L to "B")

        val part = catalog.mistakes().data().single().parts.single()
        assertEquals(listOf(11L, 12L), part.children.map { it.topic.id })
        assertEquals(listOf(2, 1), part.children.map { it.taskCount })
        assertEquals(3, part.taskCount)
        assertEquals(listOf(11L, 12L), part.leafIds)
        assertEquals(setOf(100L, 101L, 110L), catalog.allMistakes().data().toSet())
    }

    @Test
    fun aTopicWithoutMistakesCannotBeChosen() = runBlocking {
        round(110L to "B", 100L to "A")

        val part = catalog.mistakes().data().single().parts.single()
        assertEquals(listOf(12L), part.leafIds)
        assertEquals(listOf(0, 1), part.children.map { it.taskCount })
    }

    @Test
    fun theMistakesOfTheChosenTopicsComeOnce() = runBlocking {
        round(100L to "B", 101L to "B", 110L to "B")

        assertEquals(setOf(100L, 101L), catalog.mistakePool(listOf(11)).data().toSet())
        assertEquals(setOf(100L, 101L, 110L), catalog.mistakePool(listOf(10, 11)).data().toSet())
        assertEquals(3, catalog.mistakePool(listOf(10, 11)).data().size)
        assertEquals(emptyList(), catalog.mistakePool(listOf(13, 999)).data())
    }

    @Test
    fun startingOverRemovesTheMistakes() = runBlocking {
        round(100L to "B", 110L to "B")
        assertTrue(catalog.allMistakes().data().isNotEmpty())

        progress.resetProgress().data()
        assertEquals(emptyList(), catalog.allMistakes().data())
    }
}
