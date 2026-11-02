package com.madovygabor.maturimate.core.presentation

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.userProfile.data.UserProfileRepositoryImpl
import com.madovygabor.maturimate.features.userProfile.domain.DataError
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import com.madovygabor.maturimate.features.userProfile.domain.UserSubject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** The app's language and start screen follow the saved profile, also after a failed read. */
@OptIn(ExperimentalTime::class)
class AppViewModelTest {

    // the app's setup: one dispatcher that runs one database call at a time (AppModul)
    private fun newRepository(): UserProfileRepositoryImpl {
        val database = UserDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) })
        return UserProfileRepositoryImpl(database, LocalUserProvider(database), Dispatchers.IO.limitedParallelism(1))
    }

    private val repository = newRepository()

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

    // This failed about 2 times in 100 while the reads and the saves could run at the same time
    // on one connection ("cannot start a transaction within a transaction").
    @Test
    fun aSaveRightAfterTheStartAlwaysArrives() = runBlocking {
        repeat(100) {
            val repository = newRepository()
            val viewModel = AppViewModel(repository)
            repository.saveTeachingLanguage(TeachingLanguage.HU)
            assertEquals(TeachingLanguage.HU, viewModel.awaitState { it.language != null }.language, "run $it")
        }
    }

    @Test
    fun aReadThatFailsOnceIsRetried() = runBlocking {
        val viewModel = AppViewModel(FailingProfile(failures = 1, profile = finished), retryDelaysMillis = listOf(1L))

        assertEquals(AppState(TeachingLanguage.HU, onboardingCompleted = true), viewModel.awaitState { true })
        assertEquals(false, viewModel.failed.value)
    }

    @Test
    fun aReadThatKeepsFailingShowsTheErrorUntilRetried() = runBlocking {
        val repository = FailingProfile(failures = 3, profile = finished)
        val viewModel = AppViewModel(repository, retryDelaysMillis = listOf(1L))

        withTimeout(5_000) { viewModel.failed.first { it } }
        // never the onboarding for a user who may have finished it
        assertNull(viewModel.state.value)

        viewModel.retry()
        assertEquals(AppState(TeachingLanguage.HU, onboardingCompleted = true), viewModel.awaitState { true })
        assertEquals(false, viewModel.failed.value)
    }

    private val finished = UserProfile(
        teachingLanguage = TeachingLanguage.HU,
        role = UserRole.STUDENT,
        graduationDate = null,
        uiLanguage = TeachingLanguage.HU,
        dailyGoalMinutes = 10,
        timezone = "Europe/Bratislava",
        onboardingCompletedAt = Instant.fromEpochMilliseconds(1_000),
        subjects = emptyList(),
        reminderEnabled = false,
        reminderTime = null,
    )

    /** A profile that cannot be read the first [failures] times it is collected. */
    private class FailingProfile(private var failures: Int, profile: UserProfile) : UserProfileRepository {
        private val saved = MutableStateFlow<UserProfile?>(profile)
        override val profile: Flow<UserProfile?> = flow {
            if (failures-- > 0) error("database is locked")
            emit(saved.first())
        }
        private val ok: OperationResult<Unit, DataError> = OperationResult.Success(Unit)
        override suspend fun saveTeachingLanguage(language: TeachingLanguage) = ok
        override suspend fun saveRole(role: UserRole) = ok
        override suspend fun saveSubjects(subjects: List<UserSubject>) = ok
        override suspend fun saveGraduationDate(date: LocalDate) = ok
        override suspend fun saveReminder(enabled: Boolean, time: LocalTime?) = ok
        override suspend fun saveDailyGoal(minutes: Int) = ok
        override suspend fun saveNames(firstName: String, lastName: String) = ok
        override suspend fun completeOnboarding(at: Instant) = ok
    }
}
