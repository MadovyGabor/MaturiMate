package com.madovygabor.maturimate.features.learning.presentation

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.database.content.ContentDatabase
import com.madovygabor.maturimate.database.content.Learning_unit
import com.madovygabor.maturimate.database.content.Page_block
import com.madovygabor.maturimate.database.content.Subject
import com.madovygabor.maturimate.database.content.Topic
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.learning.data.ProgressRepositoryImpl
import com.madovygabor.maturimate.features.learning.domain.StudySettings
import com.madovygabor.maturimate.features.learning.domain.StudyTracker
import com.madovygabor.maturimate.database.content.Unit_page
import com.madovygabor.maturimate.features.learning.data.ContentRepositoryImpl
import com.madovygabor.maturimate.features.learning.domain.LearningError
import com.madovygabor.maturimate.features.learning.domain.ProgressRepository
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgress
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgressStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Lesson 50 has three pages; unit 51 is a test and must not open as a lesson. */
@OptIn(ExperimentalTime::class)
class LessonViewModelTest {

    private val database = ContentDatabase(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { ContentDatabase.Schema.create(it) }
    ).apply {
        subjectQueries.insert(Subject(1, "HU_MJL", "Magyar", "Maďarčina", "HU", null))
        topicQueries.insert(Topic(10, 1, null, "Nyelvtan", null, 1, true))
        learningUnitQueries.insert(Learning_unit(50, 10, "SUMMARY", "Szóképek", null, 3, 5, 10, 1, true, 0))
        learningUnitQueries.insertPage(Unit_page(50, 1, "Mi a szókép?"))
        learningUnitQueries.insertBlock(Page_block(50, 1, 1, "PARAGRAPH", "Rövid szöveg."))
        learningUnitQueries.insertPage(Unit_page(50, 2, "Hasonlat"))
        // 450 words: three minutes
        learningUnitQueries.insertBlock(Page_block(50, 2, 1, "PARAGRAPH", List(450) { "szó" }.joinToString(" ")))
        learningUnitQueries.insertPage(Unit_page(50, 3, null))
        learningUnitQueries.insertBlock(Page_block(50, 3, 1, "TIP", "Tipp."))
        learningUnitQueries.insert(Learning_unit(51, 10, "PRACTICE", "Teszt", "MINI", 2, 3, 10, 2, true, 0))
        // a last lesson with no test after it
        learningUnitQueries.insert(Learning_unit(60, 10, "SUMMARY", "Utolsó lecke", null, 3, 5, 10, 9, true, 0))
        learningUnitQueries.insertPage(Unit_page(60, 1, null))
        learningUnitQueries.insertBlock(Page_block(60, 1, 1, "PARAGRAPH", "Szöveg."))
    }

    private val content = ContentRepositoryImpl({ database }, Dispatchers.Unconfined)
    private val saved = mutableListOf<UnitProgress>()

    private fun progress(existing: List<UnitProgress> = emptyList(), saveFails: Boolean = false) =
        object : ProgressRepository by unsupported<ProgressRepository>() {
            override suspend fun unitProgress(): OperationResult<List<UnitProgress>, LearningError> = OperationResult.Success(existing)
            override suspend fun unitProgressOf(learningUnitId: Long): OperationResult<UnitProgress?, LearningError> =
                OperationResult.Success(existing.firstOrNull { it.learningUnitId == learningUnitId })
            override suspend fun saveUnitProgress(progress: UnitProgress): OperationResult<Unit, LearningError> =
                if (saveFails) OperationResult.Error(LearningError.DATABASE_ERROR)
                else { saved += progress; OperationResult.Success(Unit) }
        }

    private fun viewModel(unitId: Long = 50, progress: ProgressRepository = progress(), snackbar: SnackbarController = SnackbarController()) =
        LessonViewModel(unitId, content, progress, snackbar, now = { Instant.fromEpochMilliseconds(5_000) })

    private suspend fun LessonViewModel.ready() =
        withTimeout(5_000) { state.first { it !is LessonUiState.Loading } } as LessonUiState.Ready

    @Test
    fun lessonOpensOnItsFirstPage() = runBlocking<Unit> {
        val state = viewModel().ready()
        assertEquals("Szóképek", state.title)
        assertEquals(listOf("Mi a szókép?", "Hasonlat", null), state.pages.map { it.title })
        assertEquals(0, state.index)
        assertEquals(true, state.isFirst)
        assertEquals(false, state.isLast)
    }

    @Test
    fun theLessonKnowsTheTestThatFollowsIt() = runBlocking<Unit> {
        assertEquals(51L, viewModel(unitId = 50).ready().nextTest)
        assertNull(viewModel(unitId = 60).ready().nextTest)
    }

    @Test
    fun pagingStaysInsideThePages() = runBlocking<Unit> {
        val viewModel = viewModel()
        viewModel.ready()

        viewModel.previous()
        assertEquals(0, (viewModel.state.value as LessonUiState.Ready).index)
        viewModel.next()
        viewModel.next()
        assertEquals(2, (viewModel.state.value as LessonUiState.Ready).index)
        assertEquals(true, (viewModel.state.value as LessonUiState.Ready).isLast)
        viewModel.next()
        assertEquals(2, (viewModel.state.value as LessonUiState.Ready).index)
        viewModel.goTo(1)
        assertEquals(1, (viewModel.state.value as LessonUiState.Ready).index)
        viewModel.goTo(7)
        assertEquals(1, (viewModel.state.value as LessonUiState.Ready).index)
    }

    @Test
    fun pageTurnsAndTheEndGoToTheDailyGoal() = runBlocking<Unit> {
        val userDatabase = UserDatabase(
            JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) }
        )
        val real = ProgressRepositoryImpl(userDatabase, LocalUserProvider(userDatabase), Dispatchers.Unconfined)
        var millis = 0L
        val time = { Instant.fromEpochMilliseconds(millis) }
        val tracker = StudyTracker(real, { StudySettings(1, TimeZone.UTC) }, time)
        val viewModel = LessonViewModel(50, content, real, SnackbarController(), tracker, time)
        viewModel.ready()

        millis += 20_000
        viewModel.next()
        millis += 400_000 // a phone put down: counts as two minutes only
        viewModel.next()
        millis += 10_000
        viewModel.finish()
        withTimeout(5_000) { viewModel.finished.first() }

        assertEquals(150, (tracker.today() as OperationResult.Success).data.secondsStudied)
    }

    @Test
    fun readingTimeIsAtLeastAMinute() = runBlocking<Unit> {
        val viewModel = viewModel()
        assertEquals(1, viewModel.ready().minutes)
        viewModel.next()
        assertEquals(3, (viewModel.state.value as LessonUiState.Ready).minutes)
    }

    @Test
    fun finishingSavesTheLessonAsDoneAndTellsTheScreen() = runBlocking<Unit> {
        val viewModel = viewModel()
        viewModel.ready()

        viewModel.finish()
        // the test that follows the lesson is opened next
        assertEquals(51L, withTimeout(5_000) { viewModel.finished.first() })

        // opening the lesson saved it as started, finishing it as done
        assertEquals(listOf(UnitProgressStatus.IN_PROGRESS, UnitProgressStatus.COMPLETED), saved.map { it.status })
        val progress = saved.last()
        assertEquals(50L, progress.learningUnitId)
        assertEquals(UnitProgressStatus.COMPLETED, progress.status)
        assertEquals(Instant.fromEpochMilliseconds(5_000), progress.completedAt)
        assertEquals(Instant.fromEpochMilliseconds(5_000), progress.startedAt)
    }

    @Test
    fun anEarlierStartIsKept() = runBlocking<Unit> {
        val earlier = UnitProgress(50, UnitProgressStatus.IN_PROGRESS, Instant.fromEpochMilliseconds(1_000), null)
        val viewModel = viewModel(progress = progress(existing = listOf(earlier)))
        viewModel.ready()

        viewModel.finish()
        withTimeout(5_000) { viewModel.finished.first() }

        assertEquals(Instant.fromEpochMilliseconds(1_000), saved.single().startedAt)
    }

    @Test
    fun aFailedSaveKeepsTheLessonOpenAndSaysSo() = runBlocking<Unit> {
        val snackbar = SnackbarController()
        val viewModel = viewModel(progress = progress(saveFails = true), snackbar = snackbar)
        viewModel.ready()

        viewModel.finish()
        withTimeout(5_000) { snackbar.events.first() }

        assertEquals(emptyList(), saved)
        assertIs<LessonUiState.Ready>(viewModel.state.value)
    }

    @Test
    fun aTestOrAnUnknownUnitIsNotALesson() = runBlocking<Unit> {
        assertIs<LessonUiState.Failed>(withTimeout(5_000) { viewModel(unitId = 51).state.first { it !is LessonUiState.Loading } })
        assertIs<LessonUiState.Failed>(withTimeout(5_000) { viewModel(unitId = 999).state.first { it !is LessonUiState.Loading } })
    }
}

/** A stand-in that rejects every call; a test overrides the one call it needs. */
private inline fun <reified T : Any> unsupported(): T =
    java.lang.reflect.Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        throw UnsupportedOperationException(method.name)
    } as T
