package com.madovygabor.maturimate.features.learning.presentation

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.domain.error.OperationResult
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
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.learning.data.ContentRepositoryImpl
import com.madovygabor.maturimate.features.learning.data.ProgressRepositoryImpl
import com.madovygabor.maturimate.features.learning.domain.LearningError
import com.madovygabor.maturimate.features.learning.domain.TestRecorder
import com.madovygabor.maturimate.features.learning.domain.model.AttemptStatus
import com.madovygabor.maturimate.features.learning.domain.model.TaskFormat
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgressStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

/**
 * Test 50 (a round of 3) has the required tasks 100 (multiple choice) and 101 (true or false) and the others 103 (short
 * answer), 104 and 106 (multiple choice), and 105, which is switched off. Test 51 (a round of 4) has the required
 * ordering 110, gaps from a list 111, typed gaps 112 and matching 102. Test 52 is a lesson, test 53 has no task.
 */
@OptIn(ExperimentalTime::class)
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
        // 104 and 106 (multiple choice), 105 (switched off)
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

        learningUnitQueries.insert(Learning_unit(50, 10, "PRACTICE", "Mini teszt", "MINI", 2, 3, 10, 1, true, 0))
        listOf(100L to true, 101L to true, 103L to false, 104L to false, 105L to false, 106L to false)
            .forEachIndexed { index, (id, required) -> learningUnitQueries.insertUnitTask(Unit_task(50, id, index + 1L, required)) }
        learningUnitQueries.insert(Learning_unit(51, 10, "PRACTICE", "Más formátumok", "MINI", 2, 4, 10, 2, true, 0))
        listOf(110L, 111L, 112L, 102L).forEachIndexed { index, id -> learningUnitQueries.insertUnitTask(Unit_task(51, id, index + 1L, true)) }
        learningUnitQueries.insert(Learning_unit(52, 10, "SUMMARY", "Lecke", null, 3, 5, 10, 3, true, 0))
        learningUnitQueries.insert(Learning_unit(53, 10, "PRACTICE", "Üres", "MINI", 2, 3, 10, 4, true, 0))
    }

    private val content = ContentRepositoryImpl({ database }, Dispatchers.Unconfined)
    private val userDatabase = UserDatabase(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) }
    )
    private val progress = ProgressRepositoryImpl(userDatabase, LocalUserProvider(userDatabase), Dispatchers.Unconfined)
    private val recorder = TestRecorder(progress)

    private fun session(unitId: Long, seed: Int = 1) = TestSessionViewModel(unitId, content, recorder, Random(seed))

    private suspend fun TestSessionViewModel.ready() =
        withTimeout(5_000) { state.first { it !is TestUiState.Loading } }

    private val TestSessionViewModel.current get() = state.value as TestUiState.Ready

    /** Answers the task on the screen rightly. */
    private fun TestSessionViewModel.solve() {
        val task = current.task
        when (task.format) {
            TaskFormat.MATCHING -> task.key.sortedBy { it.slot }.forEach { activate(it.slot); pick(it.optionLabel) }
            TaskFormat.ORDERING, TaskFormat.GAP_CHOICE -> task.key.sortedBy { it.slot }.forEach { pick(it.optionLabel) }
            TaskFormat.GAP_FILL, TaskFormat.SHORT_ANSWER -> task.acceptedAnswers.groupBy { it.slot }.forEach { (slot, list) -> typeIn(slot, list.first().text) }
            else -> select(task.key.first().optionLabel)
        }
    }

    /** Answers a multiple-choice or true-or-false task wrongly, a short answer too. */
    private fun TestSessionViewModel.solveWrongly() {
        val task = current.task
        if (task.format == TaskFormat.SHORT_ANSWER) type("zzz")
        else select(task.options.first { opt -> task.key.none { it.optionLabel == opt.label } }.label)
    }

    private fun TestSessionViewModel.checkAndNext() { check(); next() }

    /** Solves tasks until the one on the screen has the [format]. */
    private fun TestSessionViewModel.advanceTo(format: TaskFormat) {
        while (current.task.format != format) { solve(); checkAndNext() }
    }

    private suspend fun settled(condition: suspend () -> Boolean) = withTimeout(5_000) { while (!condition()) yield() }

    @Test
    fun roundHasTheRequiredTasksAndARandomRestInRandomOrder() = runBlocking<Unit> {
        val seenThird = mutableSetOf<Long>()
        repeat(40) { seed ->
            val state = session(50, seed).ready()
            assertIs<TestUiState.Ready>(state)
            assertEquals(3, state.tasks.size)
            val ids = state.tasks.map { it.id }
            assertTrue(100L in ids && 101L in ids, "required tasks missing in $ids")
            assertFalse(105L in ids)
            seenThird += ids.single { it != 100L && it != 101L }
        }
        // over many rounds every optional task is drawn
        assertEquals(setOf(103L, 104L, 106L), seenThird)
    }

    @Test
    fun aRoundOfAllTheRequiredTasksHasThemAll() = runBlocking<Unit> {
        val state = session(51).ready()
        assertIs<TestUiState.Ready>(state)
        assertEquals(setOf(102L, 110L, 111L, 112L), state.tasks.map { it.id }.toSet())
        assertEquals("Más formátumok", state.title)
    }

    @Test
    fun aTestWithoutTasksIsEmpty() = runBlocking<Unit> {
        assertIs<TestUiState.Empty>(session(53).ready())
    }

    @Test
    fun aLessonOrAnUnknownUnitIsNotATest() = runBlocking<Unit> {
        assertIs<TestUiState.Failed>(session(52).ready())
        assertIs<TestUiState.Failed>(session(999).ready())
    }

    @Test
    fun checkingNeedsAnAnswerAndThenGivesTheResult() = runBlocking<Unit> {
        val viewModel = session(50)
        viewModel.ready()
        viewModel.advanceTo(TaskFormat.MC_SINGLE)

        viewModel.check()
        assertFalse(viewModel.current.checked)
        viewModel.solveWrongly()
        assertTrue(viewModel.current.canCheck)
        viewModel.check()
        assertTrue(viewModel.current.checked)
        assertFalse(viewModel.current.result!!.correct)
    }

    @Test
    fun theChosenOptionKeepsItsWhyForTheFeedback() = runBlocking<Unit> {
        val viewModel = session(50)
        viewModel.ready()
        viewModel.advanceTo(TaskFormat.MC_SINGLE)
        // task 100 is the multiple choice with the explained option A; the others have no explanation
        if (viewModel.current.task.id != 100L) return@runBlocking

        viewModel.select("A")
        viewModel.check()
        assertEquals("A hasonlat összehasonlít.", viewModel.current.chosenOption?.why)
    }

    @Test
    fun aCheckedAnswerCannotBeChangedAndNextNeedsChecking() = runBlocking<Unit> {
        val viewModel = session(50)
        viewModel.ready()
        viewModel.advanceTo(TaskFormat.MC_SINGLE)

        viewModel.next()
        assertEquals(0, viewModel.current.index.coerceAtMost(0))
        viewModel.solve()
        val right = viewModel.current.selected
        viewModel.check()
        viewModel.solveWrongly()
        assertEquals(right, viewModel.current.selected)
    }

    @Test
    fun nextStartsTheNextTaskFresh() = runBlocking<Unit> {
        val viewModel = session(50)
        viewModel.ready()
        viewModel.solve(); viewModel.check()
        viewModel.next()

        assertEquals(1, viewModel.current.index)
        assertNull(viewModel.current.selected)
        assertFalse(viewModel.current.checked)
    }

    @Test
    fun matchingFillsTheActivePairAndMovesOn() = runBlocking<Unit> {
        val viewModel = session(51)
        viewModel.ready()
        viewModel.advanceTo(TaskFormat.MATCHING)

        assertEquals(1, viewModel.current.activeSlot)
        viewModel.pick("A")
        assertEquals(mapOf(1 to "A"), viewModel.current.choices)
        assertEquals(2, viewModel.current.activeSlot)
        assertFalse(viewModel.current.canCheck)

        viewModel.pick("B")
        assertNull(viewModel.current.activeSlot)
        viewModel.check()
        assertTrue(viewModel.current.result!!.correct)
    }

    @Test
    fun aPairCanBeChosenAgainAndAWrongPairIsMarked() = runBlocking<Unit> {
        val viewModel = session(51)
        viewModel.ready()
        viewModel.advanceTo(TaskFormat.MATCHING)

        viewModel.pick("A"); viewModel.pick("A")
        viewModel.activate(1)
        viewModel.pick("B")
        assertEquals(mapOf(1 to "B", 2 to "A"), viewModel.current.choices)
        viewModel.check()
        assertEquals(mapOf(1 to false, 2 to false), viewModel.current.result!!.slots)
    }

    @Test
    fun orderingNumbersTheStepsInTheOrderTheyArePicked() = runBlocking<Unit> {
        val viewModel = session(51)
        viewModel.ready()
        viewModel.advanceTo(TaskFormat.ORDERING)

        listOf("B", "D", "A", "C").forEach { viewModel.pick(it) }
        assertEquals(mapOf(1 to "B", 2 to "D", 3 to "A", 4 to "C"), viewModel.current.choices)
        viewModel.check()
        assertTrue(viewModel.current.result!!.correct)
    }

    @Test
    fun anOrderingStepPickedAgainGoesBackAndTheRestMoveUp() = runBlocking<Unit> {
        val viewModel = session(51)
        viewModel.ready()
        viewModel.advanceTo(TaskFormat.ORDERING)

        listOf("B", "D", "A").forEach { viewModel.pick(it) }
        viewModel.pick("D")
        assertEquals(mapOf(1 to "B", 2 to "A"), viewModel.current.choices)
        assertFalse(viewModel.current.canCheck)
        viewModel.pick("C"); viewModel.pick("D")
        assertEquals(mapOf(1 to "B", 2 to "A", 3 to "C", 4 to "D"), viewModel.current.choices)
    }

    @Test
    fun wordsFillTheFirstEmptyGapAndATapOnAGapEmptiesIt() = runBlocking<Unit> {
        val viewModel = session(51)
        viewModel.ready()
        viewModel.advanceTo(TaskFormat.GAP_CHOICE)

        viewModel.pick("A"); viewModel.pick("A")
        assertEquals(mapOf(1 to "A"), viewModel.current.choices)
        viewModel.pick("B")
        viewModel.clear(1)
        assertEquals(mapOf(2 to "B"), viewModel.current.choices)
        viewModel.pick("A")
        assertEquals(mapOf(1 to "A", 2 to "B"), viewModel.current.choices)
        viewModel.check()
        assertTrue(viewModel.current.result!!.correct)
    }

    @Test
    fun typedGapsAreFilledOneByOne() = runBlocking<Unit> {
        val viewModel = session(51)
        viewModel.ready()
        viewModel.advanceTo(TaskFormat.GAP_FILL)

        viewModel.typeIn(1, "azonosít")
        assertFalse(viewModel.current.canCheck)
        viewModel.typeIn(2, "összehasonlít")
        viewModel.check()
        assertTrue(viewModel.current.result!!.correct)
    }

    @Test
    fun shortAnswerIsHintedAndChecked() = runBlocking<Unit> {
        // the third task of test 50 is the short answer in some rounds; find one
        var viewModel: TestSessionViewModel? = null
        for (seed in 0..40) {
            val candidate = session(50, seed)
            if (candidate.ready().let { it is TestUiState.Ready && it.tasks.any { t -> t.id == 103L } }) { viewModel = candidate; break }
        }
        val vm = viewModel!!
        vm.advanceTo(TaskFormat.SHORT_ANSWER)

        assertFalse(vm.current.canCheck)
        vm.showHint()
        assertTrue(vm.current.hintShown)
        assertEquals('m', vm.current.hint!!.firstLetter)
        vm.type("Megszemélyesítés")
        vm.check()
        assertTrue(vm.current.result!!.correct)
    }

    @Test
    fun theEndOfTheRoundShowsHowEveryTaskWent() = runBlocking<Unit> {
        val viewModel = session(50)
        viewModel.ready()
        viewModel.solve(); viewModel.checkAndNext()          // right
        viewModel.solveWrongly(); viewModel.checkAndNext()   // wrong
        viewModel.solve(); viewModel.check()
        assertTrue(viewModel.current.isLast)

        viewModel.next()

        val result = viewModel.state.value
        assertIs<TestUiState.Result>(result)
        assertEquals(3, result.total)
        assertEquals(2, result.correctCount)
        assertEquals(listOf(true, false, true), result.entries.map { it.correct })
        // one task was wrong, so the round is not passed
        assertFalse(result.passed)
    }

    @Test
    fun aRoundThatIsNotPassedDoesNotMarkTheUnitDone() = runBlocking<Unit> {
        val viewModel = session(50)
        viewModel.ready()
        repeat(3) { viewModel.solveWrongly(); viewModel.checkAndNext() }

        val result = viewModel.state.value
        assertIs<TestUiState.Result>(result)
        assertFalse(result.passed)
        settled { (progress.attemptsOfUnit(50) as OperationResult.Success).data.singleOrNull()?.status == AttemptStatus.FINISHED }
        assertEquals(listOf(UnitProgressStatus.IN_PROGRESS), (progress.unitProgress() as OperationResult.Success).data.map { it.status })
    }

    @Test
    fun aFinishedRoundIsRecordedAndTheUnitIsDone() = runBlocking<Unit> {
        val viewModel = session(51)
        viewModel.ready()
        repeat(4) { viewModel.solve(); viewModel.checkAndNext() }
        assertIs<TestUiState.Result>(viewModel.state.value)

        settled { progress.unitProgress().let { it is OperationResult.Success && it.data.isNotEmpty() } }

        val attempt = (progress.attemptsOfUnit(51) as OperationResult.Success).data.single()
        assertEquals(AttemptStatus.FINISHED, attempt.status)
        // ordering 1, matching 2, gaps from a list 2 and typed gaps 2
        assertEquals(7, attempt.earnedPoints)
        assertEquals(7, attempt.maxPoints)
        assertEquals(4, (progress.answersOfAttempt(attempt.id) as OperationResult.Success).data.size)
        assertEquals(UnitProgressStatus.COMPLETED, (progress.unitProgress() as OperationResult.Success).data.single().status)
        assertEquals(1, (progress.taskProgress(110) as OperationResult.Success).data!!.timesCorrect)
    }

    @Test
    fun leavingInTheMiddleAbandonsTheAttemptAndKeepsTheAnswers() = runBlocking<Unit> {
        val viewModel = session(51)
        viewModel.ready()
        viewModel.solve(); viewModel.checkAndNext()

        viewModel.leave()

        settled { (progress.attemptsOfUnit(51) as OperationResult.Success<List<com.madovygabor.maturimate.features.learning.domain.model.Attempt>, LearningError>).data.single().status == AttemptStatus.ABANDONED }
        val attempt = (progress.attemptsOfUnit(51) as OperationResult.Success).data.single()
        assertEquals(1, (progress.answersOfAttempt(attempt.id) as OperationResult.Success).data.size)
        // the unit was opened, and is not done
        assertEquals(listOf(UnitProgressStatus.IN_PROGRESS), (progress.unitProgress() as OperationResult.Success).data.map { it.status })
    }

    @Test
    fun againStartsANewRound() = runBlocking<Unit> {
        val viewModel = session(51)
        viewModel.ready()
        repeat(4) { viewModel.solve(); viewModel.checkAndNext() }
        assertIs<TestUiState.Result>(viewModel.state.value)

        viewModel.load()
        withTimeout(5_000) { viewModel.state.first { it is TestUiState.Ready } }

        assertEquals(0, viewModel.current.index)
        assertEquals(emptyList(), viewModel.current.entries)
        settled { (progress.attemptsOfUnit(51) as OperationResult.Success).data.size == 2 }
    }
}
