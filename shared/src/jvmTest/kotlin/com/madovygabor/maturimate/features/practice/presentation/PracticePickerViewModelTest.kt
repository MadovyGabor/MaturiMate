package com.madovygabor.maturimate.features.practice.presentation

import androidx.compose.ui.state.ToggleableState
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.database.content.ContentDatabase
import com.madovygabor.maturimate.database.content.Learning_unit
import com.madovygabor.maturimate.database.content.Page_block
import com.madovygabor.maturimate.database.content.Subject
import com.madovygabor.maturimate.database.content.Task
import com.madovygabor.maturimate.database.content.Task_format
import com.madovygabor.maturimate.database.content.Topic
import com.madovygabor.maturimate.database.content.Unit_page
import com.madovygabor.maturimate.database.content.Unit_task
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.learning.data.ContentRepositoryImpl
import com.madovygabor.maturimate.features.learning.data.ProgressRepositoryImpl
import com.madovygabor.maturimate.features.learning.domain.LearningError
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgress
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgressStatus
import com.madovygabor.maturimate.features.practice.domain.PracticeCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The picker and the tab on real databases. Subject 1 has the part "Nyelvtan" (10) with "Szóképek" (11: tasks 100,
 * 101, 102; 101 is in two tests) and "Alakzatok" (12: task 110), the part "Irodalom" (20) with "Romantika" (21: only a
 * lesson) and "Realizmus" (22: nothing yet), and the part "Szövegértés" (30), a topic itself, with tasks 140 and 141.
 */
@OptIn(ExperimentalTime::class)
class PracticePickerViewModelTest {

    private val database = ContentDatabase(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { ContentDatabase.Schema.create(it) }
    ).apply {
        subjectQueries.insert(Subject(1, "HU_MJL", "Magyar", "Maďarčina", "HU", null))
        topicQueries.insert(Topic(10, 1, null, "Nyelvtan", null, 1, true))
        topicQueries.insert(Topic(11, null, 10, "Szóképek", null, 1, true))
        topicQueries.insert(Topic(12, null, 10, "Alakzatok", null, 2, true))
        topicQueries.insert(Topic(20, 1, null, "Irodalom", null, 2, true))
        topicQueries.insert(Topic(21, null, 20, "Romantika", null, 1, true))
        topicQueries.insert(Topic(22, null, 20, "Realizmus", null, 2, true))
        topicQueries.insert(Topic(30, 1, null, "Szövegértés", null, 3, true))
        taskFormatQueries.insert(Task_format(1, "MC_SINGLE", "MC", "CHOICE", null))
        lesson(50, 11)
        practice(51, 11, 100, 101)
        practice(52, 11, 101, 102)
        lesson(60, 12)
        practice(61, 12, 110)
        lesson(80, 21)
        practice(91, 30, 140, 141)
    }

    private fun ContentDatabase.lesson(id: Long, topic: Long) {
        learningUnitQueries.insert(Learning_unit(id, topic, "SUMMARY", "Lecke $id", null, 3, 5, 10, 1, true, 0))
        learningUnitQueries.insertPage(Unit_page(id, 1, null))
        learningUnitQueries.insertBlock(Page_block(id, 1, 1, "PARAGRAPH", "Szöveg"))
    }

    private fun ContentDatabase.practice(id: Long, topic: Long, vararg tasks: Long) {
        learningUnitQueries.insert(Learning_unit(id, topic, "PRACTICE", "Teszt $id", "MINI", 2, 3, 10, id, true, 0))
        tasks.forEachIndexed { index, task ->
            if (taskQueries.selectById(task).executeAsOneOrNull() == null) taskQueries.insert(Task(task, topic, 1, "Kérdés", null, 1, null, true, 0))
            learningUnitQueries.insertUnitTask(Unit_task(id, task, index + 1L, true))
        }
    }

    private val content = ContentRepositoryImpl({ database }, Dispatchers.Unconfined)
    private val userDatabase = UserDatabase(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) }
    )
    private val progress = ProgressRepositoryImpl(userDatabase, LocalUserProvider(userDatabase), Dispatchers.Unconfined)
    private val catalog = PracticeCatalog(content, progress)

    private fun <T> OperationResult<T, LearningError>.data(): T = when (this) {
        is OperationResult.Success -> data
        is OperationResult.Error -> fail("expected success, got $error")
    }

    private fun finish(vararg units: Long) = runBlocking {
        units.forEach {
            progress.saveUnitProgress(UnitProgress(it, UnitProgressStatus.COMPLETED, Instant.fromEpochMilliseconds(1), Instant.fromEpochMilliseconds(2))).data()
        }
    }

    /** A picker after the student finished both grammar topics, "Romantika" and "Szövegértés". */
    private suspend fun picker(): PracticePickerViewModel {
        finish(50, 51, 52, 60, 61, 80, 91)
        val viewModel = PracticePickerViewModel(catalog)
        withTimeout(5_000) { viewModel.state.first { it is PickerUiState.Ready } }
        return viewModel
    }

    private val PracticePickerViewModel.ready get() = state.value as PickerUiState.Ready

    private suspend fun PracticePickerViewModel.counted(size: Int) =
        withTimeout(5_000) { while (ready.poolSize != size) yield() }

    @Test
    fun thePickerStartsWithTheSubjectsClosedThePartsOpenAndNothingChosen() = runBlocking<Unit> {
        val state = picker().ready
        assertEquals(listOf("Nyelvtan", "Irodalom", "Szövegértés"), state.subjects.single().parts.map { it.topic.title })
        assertTrue(state.openSubjects.isEmpty())
        assertTrue(state.closedGroups.isEmpty())
        assertTrue(state.selected.isEmpty())
        assertEquals(RoundLength.TEN, state.length)
        assertFalse(state.canStart)
        assertNull(state.start())
    }

    @Test
    fun aTopicIsChosenAndItsPartShowsItPartly() = runBlocking<Unit> {
        val viewModel = picker()
        viewModel.toggle(listOf(11))
        viewModel.counted(3)

        val state = viewModel.ready
        assertEquals(setOf(11L), state.selected)
        assertEquals(ToggleableState.Indeterminate, state.stateOf(listOf(11, 12)))
        assertEquals(1, state.chosen(listOf(11, 12)))
        // ten asked, three tasks: the round has three
        assertEquals(3, state.questions)
        assertEquals(PracticeStart(listOf(11), 3), state.start())
    }

    @Test
    fun aPartIsChosenWholeAndClearedWhole() = runBlocking<Unit> {
        val viewModel = picker()
        viewModel.toggle(listOf(11))
        viewModel.toggle(listOf(11, 12)) // partly chosen: the tick chooses all
        viewModel.counted(4)
        assertEquals(ToggleableState.On, viewModel.ready.stateOf(listOf(11, 12)))

        viewModel.toggle(listOf(11, 12)) // all chosen: the tick clears all
        viewModel.counted(0)
        assertEquals(ToggleableState.Off, viewModel.ready.stateOf(listOf(11, 12)))
        assertFalse(viewModel.ready.canStart)
    }

    @Test
    fun aSubjectChoosesEveryFinishedTopicWithTasks() = runBlocking<Unit> {
        val viewModel = picker()
        val subjectLeaves = viewModel.ready.subjects.single().parts.flatMap { it.leafIds }
        // "Romantika" is finished but has no task, "Realizmus" is not finished: neither can be chosen
        assertEquals(listOf(11L, 12L, 30L), subjectLeaves)

        viewModel.toggle(subjectLeaves)
        viewModel.counted(6)
        assertEquals(6, viewModel.ready.questions)
    }

    @Test
    fun nothingToChooseChangesNothing() = runBlocking<Unit> {
        val viewModel = picker()
        viewModel.toggle(emptyList())
        assertTrue(viewModel.ready.selected.isEmpty())
    }

    @Test
    fun theLengthOfTheRoundIsAtMostTheTasksChosen() = runBlocking<Unit> {
        val viewModel = picker()
        viewModel.toggle(listOf(11, 12, 30))
        viewModel.counted(6)

        viewModel.selectLength(RoundLength.TWENTY)
        assertEquals(6, viewModel.ready.questions)
        viewModel.toggle(listOf(11, 12, 30))
        viewModel.toggle(listOf(30))
        viewModel.counted(2)
        viewModel.selectLength(RoundLength.FIFTY)
        assertEquals(2, viewModel.ready.questions)
    }

    @Test
    fun theCustomLengthIsANumberFromOneTo999() = runBlocking<Unit> {
        val viewModel = picker()
        viewModel.toggle(listOf(11, 12, 30))
        viewModel.counted(6)

        viewModel.selectLength(RoundLength.CUSTOM)
        // a suggestion is there at once, so the button works
        assertEquals("15", viewModel.ready.customText)
        assertEquals(6, viewModel.ready.questions)

        viewModel.typeCustom("4")
        assertEquals(4, viewModel.ready.questions)
        assertEquals(PracticeStart(listOf(11, 12, 30), 4), viewModel.ready.start())

        viewModel.typeCustom("0")
        assertTrue(viewModel.ready.customInvalid)
        assertFalse(viewModel.ready.canStart)

        viewModel.typeCustom("")
        assertTrue(viewModel.ready.customInvalid)
        assertFalse(viewModel.ready.canStart)

        // only digits, at most three
        viewModel.typeCustom("a1b2")
        assertEquals("12", viewModel.ready.customText)
        viewModel.typeCustom("12345")
        assertEquals("123", viewModel.ready.customText)
        assertFalse(viewModel.ready.customInvalid)
    }

    @Test
    fun subjectsAndPartsFoldAndUnfold() = runBlocking<Unit> {
        val viewModel = picker()
        viewModel.foldSubject(1)
        assertEquals(setOf(1L), viewModel.ready.openSubjects)
        viewModel.foldGroup(10)
        assertEquals(setOf(10L), viewModel.ready.closedGroups)
        viewModel.foldSubject(1)
        viewModel.foldGroup(10)
        assertTrue(viewModel.ready.openSubjects.isEmpty())
        assertTrue(viewModel.ready.closedGroups.isEmpty())
    }

    @Test
    fun aRefreshKeepsTheChoiceAndTheFolds() = runBlocking<Unit> {
        val viewModel = picker()
        viewModel.toggle(listOf(11))
        viewModel.foldSubject(1)
        viewModel.selectLength(RoundLength.FIFTY)
        viewModel.counted(3)

        viewModel.refresh()
        withTimeout(5_000) { while (viewModel.ready.poolSize != 3) yield() }
        assertEquals(setOf(11L), viewModel.ready.selected)
        assertEquals(setOf(1L), viewModel.ready.openSubjects)
        assertEquals(RoundLength.FIFTY, viewModel.ready.length)
    }

    /** A wrong answer to each of [tasks] (a mistake each), as a practice round. */
    private fun answerWrong(vararg tasks: Long) = runBlocking {
        val recorder = com.madovygabor.maturimate.features.learning.domain.TestRecorder(progress)
        val attempt = recorder.startPractice(com.madovygabor.maturimate.features.learning.domain.model.AttemptType.FREE_PRACTICE).data()
        for (id in tasks) progress.recordAnswer(
            com.madovygabor.maturimate.features.learning.domain.model.AttemptAnswer(attempt, id, false, 0, 1, Instant.fromEpochMilliseconds(5), "1=B")
        ).data()
        for (id in tasks) progress.saveTaskProgress(
            com.madovygabor.maturimate.features.learning.domain.model.TaskProgress(
                id, 0, 1, 0, Instant.fromEpochMilliseconds(5),
            )
        ).data()
    }

    @Test
    fun inMistakeModeTheTopicsWithMistakesCanBeChosenFinishedOrNot() = runBlocking<Unit> {
        // "Alakzatok" is not finished; its mistake counts all the same
        answerWrong(100, 101, 110)
        val viewModel = PracticePickerViewModel(catalog, com.madovygabor.maturimate.features.practice.domain.PracticeMode.MISTAKES)
        withTimeout(5_000) { viewModel.state.first { it is PickerUiState.Ready } }

        val state = viewModel.ready
        assertTrue(state.mistakes)
        val grammar = state.subjects.single().parts.first()
        assertEquals(listOf(11L, 12L), grammar.leafIds)
        assertEquals(listOf(2, 1), grammar.children.map { it.taskCount })

        viewModel.toggle(listOf(11))
        viewModel.counted(2)
        assertEquals(PracticeStart(listOf(11), 2), viewModel.ready.start())
    }

    @Test
    fun theTabCountsTheMistakes() = runBlocking<Unit> {
        answerWrong(100, 110)
        val tab = PracticeViewModel(catalog)
        tab.refresh()
        assertEquals(2, (withTimeout(5_000) { tab.state.first { it is PracticeUiState.Ready } } as PracticeUiState.Ready).mistakes)
    }

    @Test
    fun theTabOffersPracticeOnlyAfterAFinishedTopicWithTasks() = runBlocking<Unit> {
        val tab = PracticeViewModel(catalog)
        // only "Romantika" (a lesson, no task) is finished: nothing to practice yet
        finish(80)
        tab.refresh()
        assertEquals(PracticeUiState.Ready(false), withTimeout(5_000) { tab.state.first { it is PracticeUiState.Ready } })

        finish(91)
        tab.refresh()
        withTimeout(5_000) { tab.state.first { it == PracticeUiState.Ready(true) } }
        assertIs<PracticeUiState.Ready>(tab.state.value)
    }
}
