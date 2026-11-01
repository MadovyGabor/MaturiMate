package com.madovygabor.maturimate.features.appOnboarding.presentation

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.appOnboarding.presentation.role.RoleViewModel
import com.madovygabor.maturimate.features.userProfile.data.UserProfileRepositoryImpl
import com.madovygabor.maturimate.features.userProfile.domain.DataError
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The ViewModels run on the Swing main dispatcher here, so each check waits for the state it expects. */
class OnboardingQuestionViewModelTest {

    private val snackbar = SnackbarController()

    private fun <T : Any> question(
        load: suspend () -> T? = { null },
        save: suspend (T) -> OperationResult<Unit, DataError> = { OperationResult.Success(Unit) },
    ) = OnboardingQuestionViewModel(snackbar, load, save)

    private suspend fun <T : Any> OnboardingQuestionViewModel<T>.awaitState(check: (QuestionUiState<T>) -> Boolean) =
        withTimeout(5_000) { state.first(check) }

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

        // loading has finished once the saved answer could have arrived
        delay(200)
        assertEquals("A", question.state.value.selected)
    }

    @Test
    fun nextSavesThenContinuesWithTheAnswer() = runBlocking {
        val saved = mutableListOf<String>()
        val continued = CompletableDeferred<String>()
        val question = question<String>(save = { saved += it; OperationResult.Success(Unit) })

        question.onSelected("A")
        question.onNext { continued.complete(it) }

        assertEquals("A", withTimeout(5_000) { continued.await() })
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
        val event = async(Dispatchers.Default) { withTimeout(5_000) { snackbar.events.first() } }
        delay(100)

        question.onSelected("A")
        question.onNext { continued = true }

        assertNotNull(event.await())
        assertFalse(continued)
        assertTrue(question.awaitState { !it.saving }.canContinue)
    }

    @Test
    fun roleViewModelSavesTheRole() = runBlocking {
        val database = UserDatabase(
            JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) }
        )
        val repository = UserProfileRepositoryImpl(database, LocalUserProvider(database), Dispatchers.Unconfined)
        val role = RoleViewModel(repository, snackbar)
        role.onSelected(UserRole.TEACHER)
        val done = CompletableDeferred<UserRole>()

        role.onNext { done.complete(it) }

        assertEquals(UserRole.TEACHER, withTimeout(5_000) { done.await() })
        val profile = repository.profile.first()!!
        assertEquals(UserRole.TEACHER, profile.role)
        // the navigation decides when the onboarding ends, not the role
        assertFalse(profile.isOnboardingCompleted)
    }
}
