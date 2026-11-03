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
 * Test 50 (round size 4) lists: 100 multiple choice, 101 true or false, 102 matching (two pairs), 103 short answer,
 * 104 a reserve task, 105 an inactive task and 106 another multiple choice; the round is 100, 101, 102 and 103.
 * Test 51 has the ordering 110, the gaps from a list 111 and the typed gaps 112. Test 52 is a lesson and test 53 has
 * no task.
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
        taskFormatQueries.insert(Task_format(4, "ORDERING", "O", "CHOICE", null))
        taskFormatQueries.insert(Task_format(5, "GAP_CHOICE", "GC", "CHOICE", null))
        taskFormatQueries.insert(Task_format(6, "GAP_FILL", "GF", "TEXT", null))
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
        // 102: matching, 1 goes with A and 2 with B
        taskQueries.insert(Task(102, 10, 3, "Párosítsd!", null, 2, null, true, 0))
        taskQueries.insertOption(Task_option(10201, 102, "PROMPT", "1", "Olyan, mint a tűz.", null))
        taskQueries.insertOption(Task_option(10202, 102, "ANSWER", "A", "hasonlat", null))
        taskQueries.insertOption(Task_option(10203, 102, "PROMPT", "2", "Te vagy a napom.", null))
        taskQueries.insertOption(Task_option(10204, 102, "ANSWER", "B", "metafora", null))
        taskQueries.insertKey(Task_key(102, 1, 10201, 10202))
        taskQueries.insertKey(Task_key(102, 2, 10203, 10204))
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
        // 110: ordering, the right order is B, D, A, C
        taskQueries.insert(Task(110, 10, 4, "Sorrend?", null, 1, null, true, 0))
        listOf("A", "B", "C", "D").forEachIndexed { i, label -> taskQueries.insertOption(Task_option(11001L + i, 110, "ANSWER", label, label.lowercase(), null)) }
        taskQueries.insertKey(Task_key(110, 1, null, 11002))
        taskQueries.insertKey(Task_key(110, 2, null, 11004))
        taskQueries.insertKey(Task_key(110, 3, null, 11001))
        taskQueries.insertKey(Task_key(110, 4, null, 11003))
        // 111: gaps from a list, A in the first gap and B in the second
        taskQueries.insert(Task(111, 10, 5, "Az [[1]] és az [[2]].", null, 2, null, true, 0))
        listOf("anafora", "ellentét", "fokozás").forEachIndexed { i, word -> taskQueries.insertOption(Task_option(11101L + i, 111, "ANSWER", "ABC"[i].toString(), word, null)) }
        taskQueries.insertKey(Task_key(111, 1, null, 11101))
        taskQueries.insertKey(Task_key(111, 2, null, 11102))
        // 112: typed gaps
        taskQueries.insert(Task(112, 10, 6, "A [[1]] és a [[2]].", null, 2, null, true, 0))
        taskQueries.insertAcceptedAnswer(Task_accepted_answer(11201, 112, 1, "azonosít", false))
        taskQueries.insertAcceptedAnswer(Task_accepted_answer(11202, 112, 2, "összehasonlít", false))

        learningUnitQueries.insert(Learning_unit(50, 10, "PRACTICE", "Mini teszt", "MINI", 2, 4, 10, 1, true, 0))
        listOf(100L to true, 101L to true, 102L to true, 103L to true, 104L to false, 105L to true, 106L to true)
            .forEachIndexed { index, (id, core) -> learningUnitQueries.insertUnitTask(Unit_task(50, id, index + 1L, core)) }
        learningUnitQueries.insert(Learning_unit(51, 10, "PRACTICE", "Más formátumok", "MINI", 2, 3, 10, 2, true, 0))
        listOf(110L, 111L, 112L).forEachIndexed { index, id -> learningUnitQueries.insertUnitTask(Unit_task(51, id, index + 1L, true)) }
        learningUnitQueries.insert(Learning_unit(52, 10, "SUMMARY", "Lecke", null, 3, 5, 10, 3, true, 0))
        learningUnitQueries.insert(Learning_unit(53, 10, "PRACTICE", "Üres", "MINI", 2, 3, 10, 4, true, 0))
    }

    private val content = ContentRepositoryImpl({ database }, Dispatchers.Unconfined)

    private suspend fun TestSessionViewModel.ready() =
        withTimeout(5_000) { state.first { it !is TestUiState.Loading } }

    private val TestSessionViewModel.current get() = state.value as TestUiState.Ready

    /** Checks the answer given so far and goes on to the next task. */
    private fun TestSessionViewModel.checkAndNext() { check(); next() }

    @Test
    fun roundIsTheFirstActiveCoreTasksOfTheUnit() = runBlocking<Unit> {
        val viewModel = TestSessionViewModel(50, content)
        val state = viewModel.ready()

        assertIs<TestUiState.Ready>(state)
        assertEquals(listOf(100L, 101L, 102L, 103L), state.tasks.map { it.id })
        assertEquals("Mini teszt", state.title)
    }

    @Test
    fun aTestWithoutTasksIsEmpty() = runBlocking<Unit> {
        assertIs<TestUiState.Empty>(TestSessionViewModel(53, content).ready())
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
    fun matchingFillsTheActivePairAndMovesOn() = runBlocking<Unit> {
        val viewModel = TestSessionViewModel(50, content)
        viewModel.ready()
        viewModel.select("B"); viewModel.checkAndNext()
        viewModel.select("I"); viewModel.checkAndNext()

        // the first pair is active at the start
        assertEquals(1, viewModel.current.activeSlot)
        viewModel.pick("A")
        assertEquals(mapOf(1 to "A"), viewModel.current.choices)
        assertEquals(2, viewModel.current.activeSlot)
        assertFalse(viewModel.current.canCheck)

        viewModel.pick("B")
        assertEquals(null, viewModel.current.activeSlot)
        assertTrue(viewModel.current.canCheck)
        viewModel.check()
        assertTrue(viewModel.current.result!!.correct)
    }

    @Test
    fun aPairCanBeChosenAgainAndAWrongPairIsMarked() = runBlocking<Unit> {
        val viewModel = TestSessionViewModel(50, content)
        viewModel.ready()
        viewModel.select("B"); viewModel.checkAndNext()
        viewModel.select("I"); viewModel.checkAndNext()

        viewModel.pick("A"); viewModel.pick("A")
        viewModel.activate(1)
        viewModel.pick("B")
        assertEquals(mapOf(1 to "B", 2 to "A"), viewModel.current.choices)
        viewModel.check()
        assertEquals(mapOf(1 to false, 2 to false), viewModel.current.result!!.slots)
    }

    @Test
    fun shortAnswerIsTypedHintedAndChecked() = runBlocking<Unit> {
        val viewModel = TestSessionViewModel(50, content)
        viewModel.ready()
        viewModel.select("B"); viewModel.checkAndNext()
        viewModel.select("I"); viewModel.checkAndNext()
        viewModel.pick("A"); viewModel.pick("B"); viewModel.checkAndNext()

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
    fun orderingNumbersTheStepsInTheOrderTheyArePicked() = runBlocking<Unit> {
        val viewModel = TestSessionViewModel(51, content)
        viewModel.ready()

        listOf("B", "D", "A", "C").forEach { viewModel.pick(it) }
        assertEquals(mapOf(1 to "B", 2 to "D", 3 to "A", 4 to "C"), viewModel.current.choices)
        assertTrue(viewModel.current.canCheck)
        viewModel.check()
        assertTrue(viewModel.current.result!!.correct)
    }

    @Test
    fun anOrderingStepPickedAgainGoesBackAndTheRestMoveUp() = runBlocking<Unit> {
        val viewModel = TestSessionViewModel(51, content)
        viewModel.ready()

        listOf("B", "D", "A").forEach { viewModel.pick(it) }
        viewModel.pick("D")
        assertEquals(mapOf(1 to "B", 2 to "A"), viewModel.current.choices)
        assertFalse(viewModel.current.canCheck)

        viewModel.pick("C"); viewModel.pick("D")
        assertEquals(mapOf(1 to "B", 2 to "A", 3 to "C", 4 to "D"), viewModel.current.choices)
        viewModel.check()
        assertFalse(viewModel.current.result!!.correct)
    }

    @Test
    fun wordsFillTheFirstEmptyGapAndATapOnAGapEmptiesIt() = runBlocking<Unit> {
        val viewModel = TestSessionViewModel(51, content)
        viewModel.ready()
        viewModel.listOrder(); viewModel.checkAndNext()

        viewModel.pick("A")
        viewModel.pick("A")
        assertEquals(mapOf(1 to "A"), viewModel.current.choices)
        viewModel.pick("B")
        assertEquals(mapOf(1 to "A", 2 to "B"), viewModel.current.choices)

        viewModel.clear(1)
        assertEquals(mapOf(2 to "B"), viewModel.current.choices)
        assertFalse(viewModel.current.canCheck)
        viewModel.pick("A")
        assertEquals(mapOf(1 to "A", 2 to "B"), viewModel.current.choices)
        viewModel.check()
        assertTrue(viewModel.current.result!!.correct)
    }

    @Test
    fun typedGapsAreFilledOneByOne() = runBlocking<Unit> {
        val viewModel = TestSessionViewModel(51, content)
        viewModel.ready()
        viewModel.listOrder(); viewModel.checkAndNext()
        viewModel.pick("A"); viewModel.pick("B"); viewModel.checkAndNext()

        viewModel.typeIn(1, "azonosít")
        assertFalse(viewModel.current.canCheck)
        viewModel.typeIn(2, "összehasonlít")
        assertTrue(viewModel.current.canCheck)
        viewModel.check()
        assertTrue(viewModel.current.result!!.correct)
        assertTrue(viewModel.current.isLast)
    }

    @Test
    fun theRoundEndsAfterTheLastTask() = runBlocking<Unit> {
        val viewModel = TestSessionViewModel(50, content)
        viewModel.ready()
        viewModel.select("B"); viewModel.checkAndNext()
        viewModel.select("I"); viewModel.checkAndNext()
        viewModel.pick("A"); viewModel.pick("B"); viewModel.checkAndNext()
        viewModel.type("x"); viewModel.check()
        assertTrue(viewModel.current.isLast)

        viewModel.next()
        withTimeout(5_000) { viewModel.finished.first() }
    }

    /** The ordering task of test 51 answered in the right order. */
    private fun TestSessionViewModel.listOrder() = listOf("B", "D", "A", "C").forEach { pick(it) }
}
