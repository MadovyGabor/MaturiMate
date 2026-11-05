package com.madovygabor.maturimate.features.practice.presentation

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.di.sharedModule
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.database.content.ContentDatabase
import com.madovygabor.maturimate.database.content.Learning_unit
import com.madovygabor.maturimate.database.content.Page_block
import com.madovygabor.maturimate.database.content.Subject
import com.madovygabor.maturimate.database.content.Task
import com.madovygabor.maturimate.database.content.Task_format
import com.madovygabor.maturimate.database.content.Task_key
import com.madovygabor.maturimate.database.content.Task_option
import com.madovygabor.maturimate.database.content.Topic
import com.madovygabor.maturimate.database.content.Unit_page
import com.madovygabor.maturimate.database.content.Unit_task
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.learning.data.ContentRepositoryImpl
import com.madovygabor.maturimate.features.learning.data.ProgressRepositoryImpl
import com.madovygabor.maturimate.features.learning.domain.ContentRepository
import com.madovygabor.maturimate.features.learning.domain.LearningError
import com.madovygabor.maturimate.features.learning.domain.RoundKind
import com.madovygabor.maturimate.features.learning.domain.TestRecorder
import com.madovygabor.maturimate.features.learning.domain.model.AttemptStatus
import com.madovygabor.maturimate.features.learning.domain.model.AttemptType
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgress
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgressStatus
import com.madovygabor.maturimate.features.learning.presentation.TestSessionViewModel
import com.madovygabor.maturimate.features.learning.presentation.TestUiState
import com.madovygabor.maturimate.features.practice.domain.PracticeCatalog
import com.madovygabor.maturimate.features.practice.domain.PracticeRoundSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * A free practice round on the real round view-model. The part "Nyelvtan" (10) has the finished topic "Szóképek"
 * (11: lesson 50 and mini test 51 with the multiple-choice tasks 200 to 205, A is always right) and the topic
 * "Alakzatok" (12: mini test 61 with tasks 210 and 211), which is not finished.
 */
@OptIn(ExperimentalTime::class)
class PracticeRoundViewModelTest {

    private val database = ContentDatabase(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { ContentDatabase.Schema.create(it) }
    ).apply {
        subjectQueries.insert(Subject(1, "HU_MJL", "Magyar", "Maďarčina", "HU", null))
        topicQueries.insert(Topic(10, 1, null, "Nyelvtan", null, 1, true))
        topicQueries.insert(Topic(11, null, 10, "Szóképek", null, 1, true))
        topicQueries.insert(Topic(12, null, 10, "Alakzatok", null, 2, true))
        taskFormatQueries.insert(Task_format(1, "MC_SINGLE", "MC", "CHOICE", null))
        learningUnitQueries.insert(Learning_unit(50, 11, "SUMMARY", "Lecke", null, 3, 5, 10, 1, true, 0))
        learningUnitQueries.insertPage(Unit_page(50, 1, null))
        learningUnitQueries.insertBlock(Page_block(50, 1, 1, "PARAGRAPH", "Szöveg"))
        learningUnitQueries.insert(Learning_unit(51, 11, "PRACTICE", "Mini teszt", "MINI", 2, 3, 10, 2, true, 0))
        learningUnitQueries.insert(Learning_unit(61, 12, "PRACTICE", "Mini teszt", "MINI", 2, 3, 10, 1, true, 0))
        (200L..205L).forEachIndexed { index, id -> task(id, 11); learningUnitQueries.insertUnitTask(Unit_task(51, id, index + 1L, index < 2)) }
        (210L..211L).forEachIndexed { index, id -> task(id, 12); learningUnitQueries.insertUnitTask(Unit_task(61, id, index + 1L, true)) }
    }

    private fun ContentDatabase.task(id: Long, topic: Long) {
        taskQueries.insert(Task(id, topic, 1, "Kérdés $id", null, 1, null, true, 0))
        taskQueries.insertOption(Task_option(id * 10 + 1, id, "ANSWER", "A", "jó", null))
        taskQueries.insertOption(Task_option(id * 10 + 2, id, "ANSWER", "B", "rossz", null))
        taskQueries.insertKey(Task_key(id, 1, null, id * 10 + 1))
    }

    private val content = ContentRepositoryImpl({ database }, Dispatchers.Unconfined)
    private val userDatabase = UserDatabase(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) }
    )
    private val progress = ProgressRepositoryImpl(userDatabase, LocalUserProvider(userDatabase), Dispatchers.Unconfined)
    private val recorder = TestRecorder(progress)
    private val catalog = PracticeCatalog(content, progress)

    init {
        // the student finished "Szóképek" (its lesson and its test)
        runBlocking {
            listOf(50L, 51L).forEach {
                progress.saveUnitProgress(UnitProgress(it, UnitProgressStatus.COMPLETED, Instant.fromEpochMilliseconds(1), Instant.fromEpochMilliseconds(2))).data()
            }
        }
    }

    private fun <T> OperationResult<T, LearningError>.data(): T = when (this) {
        is OperationResult.Success -> data
        is OperationResult.Error -> fail("expected success, got $error")
    }

    private fun practice(topics: List<Long> = listOf(10), count: Int = 3, seed: Int = 1) =
        TestSessionViewModel(PracticeRoundSource(topics, count, "Szabad gyakorlás", catalog, content, recorder), Random(seed))

    private suspend fun TestSessionViewModel.ready() = withTimeout(5_000) { state.first { it !is TestUiState.Loading } }

    private val TestSessionViewModel.current get() = state.value as TestUiState.Ready

    private fun TestSessionViewModel.answerRight() { select("A"); check() }
    private fun TestSessionViewModel.answerWrong() { select("B"); check() }

    private suspend fun settled(condition: suspend () -> Boolean) = withTimeout(5_000) { while (!condition()) yield() }

    private suspend fun attempts() = progress.attempts().data()

    @Test
    fun theRoundHasTheAskedNumberOfTasksFromTheFinishedTopicsOnly() = runBlocking<Unit> {
        repeat(10) { seed ->
            val state = practice(count = 4, seed = seed).ready()
            assertIs<TestUiState.Ready>(state)
            val ids = state.tasks.map { it.id }
            assertEquals(4, ids.size)
            assertEquals(ids.size, ids.toSet().size)
            // the tasks of the unfinished topic never come
            assertTrue(ids.all { it in 200L..205L }, "unexpected tasks in $ids")
            assertEquals("Szabad gyakorlás", state.title)
        }
    }

    @Test
    fun aSmallPoolGivesEveryTaskOnce() = runBlocking<Unit> {
        val state = practice(count = 50).ready()
        assertIs<TestUiState.Ready>(state)
        assertEquals((200L..205L).toSet(), state.tasks.map { it.id }.toSet())
        assertEquals(6, state.tasks.size)
    }

    @Test
    fun aSelectionWithoutFinishedTopicsHasNothingToAsk() = runBlocking<Unit> {
        assertIs<TestUiState.Empty>(practice(topics = listOf(12)).ready())
        assertTrue(attempts().isEmpty(), "no attempt is started for an empty round")
    }

    @Test
    fun theRoundIsAFreePracticeAttemptAndEveryAnswerIsRecorded() = runBlocking<Unit> {
        val viewModel = practice()
        viewModel.ready()
        viewModel.answerWrong()

        settled { attempts().singleOrNull()?.let { progress.answersOfAttempt(it.id).data().size == 1 } == true }
        val attempt = attempts().single()
        assertEquals(AttemptType.FREE_PRACTICE, attempt.type)
        assertEquals(null, attempt.learningUnitId)
        assertEquals(false, progress.answersOfAttempt(attempt.id).data().single().isCorrect)
    }

    @Test
    fun theLastAnswerShowsThePracticeResultAndFinishesNoUnit() = runBlocking<Unit> {
        // the topic test of "Alakzatok" is open; a practice round must leave it open
        progress.saveUnitProgress(UnitProgress(61, UnitProgressStatus.IN_PROGRESS, Instant.fromEpochMilliseconds(1), null)).data()
        val viewModel = practice(count = 3)
        viewModel.ready()
        repeat(3) { viewModel.answerRight(); viewModel.next() }

        val result = viewModel.state.value
        assertIs<TestUiState.Result>(result)
        assertEquals(RoundKind.PRACTICE, result.kind)
        assertEquals(3, result.correctCount)
        settled { attempts().single().status == AttemptStatus.FINISHED }
        assertEquals(3, attempts().single().earnedPoints)
        val units = progress.unitProgress().data().associate { it.learningUnitId to it.status }
        assertEquals(mapOf(50L to UnitProgressStatus.COMPLETED, 51L to UnitProgressStatus.COMPLETED, 61L to UnitProgressStatus.IN_PROGRESS), units)
    }

    @Test
    fun closingAfterSomeAnswersFinishesTheRoundWithThoseAndShowsTheirResult() = runBlocking<Unit> {
        val viewModel = practice(count = 5)
        viewModel.ready()
        viewModel.answerRight()
        viewModel.next()
        viewModel.answerWrong() // checked, not yet moved on

        assertFalse(viewModel.close(), "the screen stays open to show the result")

        val result = viewModel.state.value
        assertIs<TestUiState.Result>(result)
        assertEquals(RoundKind.PRACTICE, result.kind)
        assertEquals(2, result.total)
        assertEquals(1, result.correctCount)
        settled { attempts().single().status == AttemptStatus.FINISHED }
        assertEquals(2, attempts().single().maxPoints)
        // closing the result then closes the screen
        assertTrue(viewModel.close())
    }

    @Test
    fun closingBeforeAnyAnswerLeavesTheRound() = runBlocking<Unit> {
        val viewModel = practice()
        viewModel.ready()

        assertTrue(viewModel.close())
        settled { attempts().single().status == AttemptStatus.ABANDONED }
    }

    @Test
    fun anotherRoundIsANewAttemptWithNewRandomTasks() = runBlocking<Unit> {
        val viewModel = practice(count = 3, seed = 4)
        val first = (viewModel.ready() as TestUiState.Ready).tasks.map { it.id }
        repeat(3) { viewModel.answerRight(); viewModel.next() }
        assertIs<TestUiState.Result>(viewModel.state.value)

        viewModel.load()
        // wait for the new round itself (the old result is still there for a moment)
        val second = withTimeout(5_000) { viewModel.state.first { it is TestUiState.Ready } as TestUiState.Ready }.tasks.map { it.id }

        settled { attempts().size == 2 }
        assertTrue(second.all { it in 200L..205L })
        // the same random source goes on, so the next round is another draw (with this seed, another order or set)
        assertNotEquals(first, second)
    }

    // The app's own module builds the practice view-model from the parameters the screen passes.
    @Test
    fun theAppModuleBuildsThePracticeRound() = runBlocking<Unit> {
        val koin = startKoin {
            allowOverride(true)
            modules(sharedModule, module {
                single { userDatabase }
                single<ContentRepository> { content }
            })
        }.koin
        try {
            val viewModel = koin.get<TestSessionViewModel>(named(PRACTICE_ROUND)) { parametersOf(listOf(10L), 2, "Szabad gyakorlás") }
            val state = viewModel.ready()
            assertIs<TestUiState.Ready>(state)
            assertEquals(2, state.tasks.size)
            assertEquals("Szabad gyakorlás", state.title)
        } finally {
            stopKoin()
        }
    }

    @Test
    fun closingATestAfterAnswersStillLeavesIt() = runBlocking<Unit> {
        // a test keeps its old behaviour: the close button abandons the attempt and closes at once
        val viewModel = TestSessionViewModel(51, content, recorder, Random(1))
        viewModel.ready()
        viewModel.answerRight()

        assertTrue(viewModel.close())
        assertIs<TestUiState.Ready>(viewModel.state.value)
        settled { attempts().single().status == AttemptStatus.ABANDONED }
    }
}
