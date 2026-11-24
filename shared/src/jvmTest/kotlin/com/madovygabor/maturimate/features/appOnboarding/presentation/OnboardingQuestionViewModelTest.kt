package com.madovygabor.maturimate.features.appOnboarding.presentation

import com.madovygabor.maturimate.testutil.testDbDispatcher
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.features.userProfile.domain.DataError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.madovygabor.maturimate.testutil.awaitFirst
import com.madovygabor.maturimate.testutil.awaitWithin
import com.madovygabor.maturimate.testutil.flushMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The ViewModels run on the Swing main dispatcher here, so each check waits for the state it expects. */
class OnboardingQuestionViewModelTest {

    // the app's setup: one thread runs the database calls (AppModul); with Unconfined a ViewModel on the main thread and the
    // test could use the one connection at the same time
    private val dbDispatcher = testDbDispatcher()

    private val snackbar = SnackbarController()

    private fun <T : Any> question(
        load: suspend () -> T? = { null },
        save: suspend (T) -> OperationResult<Unit, DataError> = { OperationResult.Success(Unit) },
    ) = OnboardingQuestionViewModel(snackbar, load, save)

    private suspend fun <T : Any> OnboardingQuestionViewModel<T>.awaitState(check: (QuestionUiState<T>) -> Boolean) =
        state.awaitFirst(predicate = check)

    @Test
    fun savedAnswerIsSelected() = runBlocking {
        val question = question(load = { "B" })

        assertEquals("B", question.awaitState { it.selected != null }.selected)
    }

    @Test
    fun nothingIsSelectedWithoutASavedAnswer() = runBlocking {
        val question = question<String>(load = { null })

        assertNull(question.state.value.selected)
        assertFalse(question.state.value.canContinue)
    }

    @Test
    fun choiceMadeWhileLoadingWins() = runBlocking {
        val loaded = CompletableDeferred<String?>()
        val question = question(load = { loaded.await() })

        question.onSelected("A")
        loaded.complete("B")

        // everything queued on the main thread (the load resuming with "B") has run
        flushMain()
        assertEquals("A", question.state.value.selected)
    }

    @Test
    fun nextSavesThenContinuesWithTheAnswer() = runBlocking {
        val saved = mutableListOf<String>()
        val continued = CompletableDeferred<String>()
        val question = question<String>(save = { saved += it; OperationResult.Success(Unit) })

        question.onSelected("A")
        question.onNext { continued.complete(it) }

        assertEquals("A", continued.awaitWithin())
        assertEquals(listOf("A"), saved)
        assertFalse(question.awaitState { !it.saving }.saving)
    }

    @Test
    fun nextDoesNothingWithoutASelection() = runBlocking {
        var saves = 0
        val question = question<String>(save = { saves++; OperationResult.Success(Unit) })

        question.onNext { error("must not continue") }

        assertEquals(0, saves)
    }

    @Test
    fun failedSaveShowsASnackbarAndStays() = runBlocking {
        var continued = false
        val question = question<String>(save = { OperationResult.Error(DataError.STORAGE_FULL) })
        question.onSelected("A")
        question.onNext { continued = true }

        assertNotNull(snackbar.events.awaitFirst())
        assertFalse(continued)
        assertTrue(question.awaitState { !it.saving }.canContinue)
    }
}
