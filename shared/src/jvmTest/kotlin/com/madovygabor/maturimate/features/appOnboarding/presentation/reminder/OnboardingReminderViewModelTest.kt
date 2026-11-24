package com.madovygabor.maturimate.features.appOnboarding.presentation.reminder

import com.madovygabor.maturimate.testutil.awaitFirst
import com.madovygabor.maturimate.testutil.awaitWithin
import com.madovygabor.maturimate.testutil.testDbDispatcher
import com.madovygabor.maturimate.core.presentation.NotificationPermissionResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.userProfile.data.UserProfileRepositoryImpl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingReminderViewModelTest {

    // the app's setup: one thread runs the database calls (AppModul); with Unconfined a ViewModel on the main thread and the
    // test could use the one connection at the same time
    private val dbDispatcher = testDbDispatcher()

    private fun repository(): UserProfileRepositoryImpl {
        val database = UserDatabase(
            JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) }
        )
        return UserProfileRepositoryImpl(database, LocalUserProvider(database), dbDispatcher)
    }

    @Test
    fun turningOnSavesTheChosenTimeAndNotNowTurnsItOff() = runBlocking {
        val repository = repository()
        val viewModel = ReminderViewModel(repository, SnackbarController())
        viewModel.onTimeSelected(LocalTime(20, 15))

        val on = CompletableDeferred<Unit>()
        viewModel.onAnswer(true) { on.complete(Unit) }
        on.awaitWithin()
        assertTrue(repository.profile.first()!!.reminderEnabled)
        assertEquals(LocalTime(20, 15), repository.profile.first()!!.reminderTime)

        val off = CompletableDeferred<Unit>()
        viewModel.onAnswer(false) { off.complete(Unit) }
        off.awaitWithin()
        assertFalse(repository.profile.first()!!.reminderEnabled)
    }

    @Test
    fun aRefusedPermissionSavesNothingAndKeepsTheStepOpen() = runBlocking {
        val repository = repository()
        val viewModel = ReminderViewModel(repository, SnackbarController())
        var next = 0

        viewModel.onPermissionResult(NotificationPermissionResult.DENIED) { next++ }
        assertEquals(NotificationPermissionResult.DENIED, viewModel.state.value.permission)
        viewModel.onPermissionResult(NotificationPermissionResult.BLOCKED) { next++ }
        assertEquals(NotificationPermissionResult.BLOCKED, viewModel.state.value.permission)

        assertEquals(0, next)
        // nothing was saved as the user's choice
        assertFalse(repository.profile.first()?.reminderEnabled ?: false)
        assertEquals(null, repository.profile.first()?.reminderTime)

        val on = CompletableDeferred<Unit>()
        viewModel.onPermissionResult(NotificationPermissionResult.GRANTED) { on.complete(Unit) }
        on.awaitWithin()
        assertTrue(repository.profile.first()!!.reminderEnabled)
        assertEquals(null, viewModel.state.value.permission)
    }

    @Test
    fun savedTimeIsOfferedAgain() = runBlocking {
        val repository = repository()
        repository.saveReminder(false, LocalTime(6, 45))

        val viewModel = ReminderViewModel(repository, SnackbarController())

        assertEquals(LocalTime(6, 45), viewModel.state.awaitFirst { it.time != DEFAULT_REMINDER_TIME }.time)
    }
}
