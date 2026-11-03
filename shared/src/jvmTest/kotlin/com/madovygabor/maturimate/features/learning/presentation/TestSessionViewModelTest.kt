package com.madovygabor.maturimate.features.learning.presentation

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.database.content.ContentDatabase
import com.madovygabor.maturimate.database.content.Learning_unit
import com.madovygabor.maturimate.database.content.Subject
import com.madovygabor.maturimate.database.content.Task
import com.madovygabor.maturimate.database.content.Task_accepted_answer
import com.madovygabor.maturimate.database.content.Task_format
import com.madovygabor.maturimate.database.content.Task_key
import com.madovygabor.maturimate.database.content.Task_option
import com.madovygabor.maturimate.database.content.Topic
import com.madovygabor.maturimate.database.content.Unit_task
import com.madovygabor.maturimate.features.learning.data.ContentRepositoryImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Test 50 (round size 3) lists: 100 multiple choice, 101 true or false, 102 matching (no screen yet, skipped),
 * 103 short answer, 104 a reserve task, 105 an inactive task and 106 another multiple choice. The round is
 * therefore 100, 101 and 103. Test 51 has only a matching task; test 52 is a lesson.
 */
class TestSessionViewModelTest {

    private val database = ContentDatabase(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { ContentDatabase.Schema.create(it) }
    ).apply {
        subjectQueries.insert(Subject(1, "HU_MJL", "Magyar", "Maďarčina", "HU", null))
        topicQueries.insert(Topic(10, 1, null, "Nyelvtan", null, 1, true))
        taskFormatQueries.insert(Task_format(1, "MC_SINGLE", "MC", "CHOICE", null))
        taskFormatQueries.insert(Task_format(2, "TRUE_FALSE", "TF", "CHOICE", null))
        taskFormatQueries.insert(Task_format(3, "MATCHING", "M", "CHOICE", null))
        taskFormatQueries.insert(Task_format(7, "SHORT_ANSWER", "SA", "TEXT", null))

        // 100: multiple choice, B is right and A has an explanation
        taskQueries.insert(Task(100, 10, 1, "Melyik?", "Mert B.", 1, null, true, 0))
        taskQueries.insertOption(Task_option(10001, 100, "ANSWER", "A", "hasonlat", "A hasonlat összehasonlít."))
        taskQueries.insertOption(Task_option(10002, 100, "ANSWER", "B", "metafora", null))
        taskQueries.insertKey(Task_key(100, 1, null, 10002))
        // 101: true or false
        taskQueries.insert(Task(101, 10, 2, "Igaz?", null, 1, null, true, 0))
        taskQueries.insertOption(Task_option(10101, 101, "ANSWER", "I", "igaz", null))
        taskQueries.insertOption(Task_option(10102, 101, "ANSWER", "H", "hamis", null))
        taskQueries.insertKey(Task_key(101, 1, null, 10101))
        // 102: matching, not shown yet
        taskQueries.insert(Task(102, 10, 3, "Párosítsd!", null, 2, null, true, 0))
        taskQueries.insertOption(Task_option(10201, 102, "PROMPT", "1", "x", null))
        taskQueries.insertOption(Task_option(10202, 102, "ANSWER", "A", "y", null))
        taskQueries.insertKey(Task_key(102, 1, 10201, 10202))
        // 103: short answer
        taskQueries.insert(Task(103, 10, 7, "Hogy hívjuk?", null, 1, 1, true, 0))
        taskQueries.insertAcceptedAnswer(Task_accepted_answer(10301, 103, 1, "megszemélyesítés", false))
        // 104 (reserve), 105 (inactive), 106 (another multiple choice)
        for (id in 104L..106L) {
            taskQueries.insert(Task(id, 10, 1, "Még?", null, 1, null, id != 105L, 0))
            taskQueries.insertOption(Task_option(id * 100 + 1, id, "ANSWER", "A", "a", null))
            taskQueries.insertOption(Task_option(id * 100 + 2, id, "ANSWER", "B", "b", null))
            taskQueries.insertKey(Task_key(id, 1, null, id * 100 + 1))
        }
        learningUnitQueries.insert(Learning_unit(50, 10, "PRACTICE", "Mini teszt", "MINI", 2, 3, 10, 1, true, 0))
        listOf(100L to true, 101L to true, 102L to true, 103L to true, 104L to false, 105L to true, 106L to true)
            .forEachIndexed { index, (id, core) -> learningUnitQueries.insertUnitTask(Unit_task(50, id, index + 1L, core)) }
        learningUnitQueries.insert(Learning_unit(51, 10, "PRACTICE", "Csak párosítás", "MINI", 2, 3, 10, 2, true, 0))
        learningUnitQueries.insertUnitTask(Unit_task(51, 102, 1, true))
        learningUnitQueries.insert(Learning_unit(52, 10, "SUMMARY", "Lecke", null, 3, 5, 10, 3, true, 0))
    }

    private val content = ContentRepositoryImpl({ database }, Dispatchers.Unconfined)

    private suspend fun TestSessionViewModel.ready() =
        withTimeout(5_000) { state.first { it !is TestUiState.Loading } }

    private val TestSessionViewModel.current get() = state.value as TestUiState.Ready

    @Test
    fun roundIsTheFirstActiveCoreTasksWithAScreen() = runBlocking<Unit> {
        val viewModel = TestSessionViewModel(50, content)
        val state = viewModel.ready()

        assertIs<TestUiState.Ready>(state)
        assertEquals(listOf(100L, 101L, 103L), state.tasks.map { it.id })
        assertEquals("Mini teszt", state.title)
    }

    @Test
    fun aTestWithoutShowableTasksIsEmpty() = runBlocking<Unit> {
        assertIs<TestUiState.Empty>(TestSessionViewModel(51, content).ready())
    }

    @Test
    fun aLessonOrAnUnknownUnitIsNotATest() = runBlocking<Unit> {
        assertIs<TestUiState.Failed>(TestSessionViewModel(52, content).ready())
        assertIs<TestUiState.Failed>(TestSessionViewModel(999, content).ready())
    }

    @Test
    fun checkingNeedsAChoiceAndThenGivesTheResult() = runBlocking<Unit> {
        val viewModel = TestSessionViewModel(50, content)
        viewModel.ready()

        viewModel.check()
        assertFalse(viewModel.current.checked)

        viewModel.select("A")
        assertTrue(viewModel.current.canCheck)
        viewModel.check()
        assertTrue(viewModel.current.checked)
        assertFalse(viewModel.current.result!!.correct)
        assertEquals("A hasonlat összehasonlít.", viewModel.current.chosenOption?.why)
    }

    @Test
    fun aRightChoiceIsCorrectAndCannotBeChangedAfterChecking() = runBlocking<Unit> {
        val viewModel = TestSessionViewModel(50, content)
        viewModel.ready()

        viewModel.select("B")
        viewModel.check()
        assertTrue(viewModel.current.result!!.correct)

        viewModel.select("A")
        assertEquals("B", viewModel.current.selected)
    }

    @Test
    fun nextGoesOnOnlyAfterCheckingAndStartsTheNextTaskFresh() = runBlocking<Unit> {
        val viewModel = TestSessionViewModel(50, content)
        viewModel.ready()

        viewModel.next()
        assertEquals(0, viewModel.current.index)

        viewModel.select("B")
        viewModel.check()
        viewModel.next()
        assertEquals(1, viewModel.current.index)
        assertNull(viewModel.current.selected)
        assertFalse(viewModel.current.checked)
    }

    @Test
    fun shortAnswerIsTypedHintedAndChecked() = runBlocking<Unit> {
        val viewModel = TestSessionViewModel(50, content)
        viewModel.ready()
        viewModel.select("B"); viewModel.check(); viewModel.next()
        viewModel.select("I"); viewModel.check(); viewModel.next()

        assertEquals("Hogy hívjuk?", viewModel.current.task.questionText)
        assertFalse(viewModel.current.canCheck)
        assertFalse(viewModel.current.hintShown)
        viewModel.showHint()
        assertTrue(viewModel.current.hintShown)
        assertEquals('m', viewModel.current.hint!!.firstLetter)

        viewModel.type("Megszemélyesítés")
        viewModel.check()
        assertTrue(viewModel.current.result!!.correct)
    }

    @Test
    fun theRoundEndsAfterTheLastTask() = runBlocking<Unit> {
        val viewModel = TestSessionViewModel(50, content)
        viewModel.ready()
        viewModel.select("B"); viewModel.check(); viewModel.next()
        viewModel.select("I"); viewModel.check(); viewModel.next()
        viewModel.type("x"); viewModel.check()
        assertTrue(viewModel.current.isLast)

        viewModel.next()
        withTimeout(5_000) { viewModel.finished.first() }
    }
}
