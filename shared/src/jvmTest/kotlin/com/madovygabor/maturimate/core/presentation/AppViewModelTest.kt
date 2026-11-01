package com.madovygabor.maturimate.core.presentation

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.userProfile.data.UserProfileRepositoryImpl
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** The app's language and start screen follow the saved profile. */
@OptIn(ExperimentalTime::class)
class AppViewModelTest {

    private val database = UserDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) })
    private val repository = UserProfileRepositoryImpl(database, LocalUserProvider(database), Dispatchers.Unconfined)

    private suspend fun AppViewModel.awaitState(check: (AppState) -> Boolean) = withTimeout(5_000) { state.first { it != null && check(it) } }!!

    @Test
    fun newUserStartsTheOnboardingInThePhonesLanguage() = runBlocking {
        val state = AppViewModel(repository).awaitState { true }

        assertEquals(AppState(language = null, onboardingCompleted = false), state)
    }

    @Test
    fun teachingLanguageSetsTheAppLanguageAndTheEndGoesHome() = runBlocking {
        val viewModel = AppViewModel(repository)

        repository.saveTeachingLanguage(TeachingLanguage.SK)
        assertEquals(TeachingLanguage.SK, viewModel.awaitState { it.language != null }.language)

        repository.completeOnboarding(Instant.fromEpochMilliseconds(1_000))
        assertEquals(AppState(TeachingLanguage.SK, onboardingCompleted = true), viewModel.awaitState { it.onboardingCompleted })
    }
}
