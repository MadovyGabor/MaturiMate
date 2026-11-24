package com.madovygabor.maturimate.features.appOnboarding.presentation.role

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.userProfile.data.UserProfileRepositoryImpl
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import com.madovygabor.maturimate.testutil.awaitWithin
import com.madovygabor.maturimate.testutil.testDbDispatcher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RoleViewModelTest {

    // the app's setup: one thread runs the database calls (AppModul); with Unconfined a ViewModel on the main thread and the
    // test could use the one connection at the same time
    private val dbDispatcher = testDbDispatcher()

    private val snackbar = SnackbarController()

    @Test
    fun roleViewModelSavesTheRole() = runBlocking {
        val database = UserDatabase(
            JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) }
        )
        val repository = UserProfileRepositoryImpl(database, LocalUserProvider(database), dbDispatcher)
        val role = RoleViewModel(repository, snackbar)
        role.onSelected(UserRole.TEACHER)
        val done = CompletableDeferred<UserRole>()

        role.onNext { done.complete(it) }

        assertEquals(UserRole.TEACHER, done.awaitWithin())
        val profile = repository.profile.first()!!
        assertEquals(UserRole.TEACHER, profile.role)
        // the navigation decides when the onboarding ends, not the role
        assertFalse(profile.isOnboardingCompleted)
    }
}
