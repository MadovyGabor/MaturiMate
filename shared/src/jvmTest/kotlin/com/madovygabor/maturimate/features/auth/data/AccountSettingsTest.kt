package com.madovygabor.maturimate.features.auth.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.auth.domain.AccountRepository
import com.madovygabor.maturimate.features.auth.domain.AccountUser
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.domain.AuthRepository
import com.madovygabor.maturimate.features.auth.domain.AuthState
import com.madovygabor.maturimate.features.auth.domain.ConsentType
import com.madovygabor.maturimate.features.auth.domain.RemoteProfile
import com.madovygabor.maturimate.features.learning.data.ProgressRepositoryImpl
import com.madovygabor.maturimate.features.learning.domain.model.AttemptStatus
import com.madovygabor.maturimate.features.learning.domain.model.AttemptType
import com.madovygabor.maturimate.features.learning.domain.model.DailyActivity
import com.madovygabor.maturimate.features.learning.domain.model.Streak
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgress
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgressStatus
import com.madovygabor.maturimate.features.userProfile.data.UserProfileRepositoryImpl
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class AccountSettingsTest {

    private val database = UserDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) })
    private var guests = 0
    private val localUser = LocalUserProvider(database) { "guest-${++guests}" }
    private val profiles = UserProfileRepositoryImpl(database, localUser, Dispatchers.Unconfined)
    private val progress = ProgressRepositoryImpl(database, localUser, Dispatchers.Unconfined)

    private class Auth(private val password: String = "Old12345") : AuthRepository {
        var newPassword: String? = null
        var signedOut = false
        override val state: Flow<AuthState> = emptyFlow()
        override fun currentUser() = AccountUser("account-1", "anna@example.com", "Anna", "Kovács")
        private val ok: OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
        override suspend fun signUp(email: String, password: String) = ok
        override suspend fun confirmSignUp(email: String, code: String) = ok
        override suspend fun resendSignUpCode(email: String) = ok
        override suspend fun signIn(email: String, password: String) =
            if (password == this.password) ok else OperationResult.Error(AuthError.INVALID_CREDENTIALS)
        override suspend fun signOut() = ok.also { signedOut = true }
        override suspend fun sendPasswordResetCode(email: String) = ok
        override suspend fun verifyResetCode(email: String, code: String) = ok
        override suspend fun setNewPassword(newPassword: String) = ok.also { this.newPassword = newPassword }
    }

    private class Account(private val fails: Boolean = false) : AccountRepository {
        var names: Pair<String, String>? = null
        override suspend fun saveSignUp(profile: UserProfile, firstName: String, lastName: String, consents: Set<ConsentType>, finishedAt: Instant):
            OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
        override suspend fun downloadProfile(): OperationResult<RemoteProfile?, AuthError> = OperationResult.Success(null)
        override suspend fun saveNames(firstName: String, lastName: String): OperationResult<Unit, AuthError> {
            if (fails) return OperationResult.Error(AuthError.NETWORK)
            names = firstName to lastName
            return OperationResult.Success(Unit)
        }
    }

    private fun settings(auth: Auth = Auth(), account: Account = Account()) =
        AccountSettingsImpl(auth, account, profiles, localUser, Dispatchers.Unconfined)

    @Test
    fun aNewNameGoesToTheServerAndThenToThisPhone() = runBlocking {
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        val account = Account()
        assertEquals(OperationResult.Success(Unit), settings(account = account).saveNames(" Anna ", "Kiss"))
        assertEquals("Anna" to "Kiss", account.names)
        val saved = profiles.profile.first()!!
        assertEquals("Anna", saved.firstName)
        assertEquals("Kiss", saved.lastName)
    }

    @Test
    fun aNameTheServerDidNotTakeIsNotSavedHere() = runBlocking {
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        assertEquals(OperationResult.Error(AuthError.NETWORK), settings(account = Account(fails = true)).saveNames("Anna", "Kiss"))
        assertNull(profiles.profile.first()!!.firstName)
    }

    @Test
    fun thePasswordChangesOnlyWithTheRightOldOneAndAStrongNewOne() = runBlocking {
        val auth = Auth()
        val settings = settings(auth)
        assertEquals(OperationResult.Error(AuthError.INVALID_CREDENTIALS), settings.changePassword("wrong", "New12345"))
        assertEquals(OperationResult.Error(AuthError.WEAK_PASSWORD), settings.changePassword("Old12345", "short"))
        assertEquals(OperationResult.Error(AuthError.SAME_PASSWORD), settings.changePassword("Old12345", "Old12345"))
        assertNull(auth.newPassword)
        assertEquals(OperationResult.Success(Unit), settings.changePassword("Old12345", "New12345"))
        assertEquals("New12345", auth.newPassword)
    }

    @Test
    fun aSignOutRemovesEverythingAndANewGuestStarts() = runBlocking {
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        progress.saveUnitProgress(UnitProgress(50, UnitProgressStatus.COMPLETED, Instant.fromEpochMilliseconds(1), Instant.fromEpochMilliseconds(1)))
        val before = localUser.userId()
        val auth = Auth()

        assertEquals(OperationResult.Success(Unit), settings(auth).signOut())
        assertTrue(auth.signedOut)
        assertTrue(localUser.userId() != before && localUser.isGuest())
        // the new guest has no profile: the app starts with the welcome screen
        assertNull(profiles.profile.first())
        assertEquals(emptyList(), (progress.unitProgress() as OperationResult.Success).data)
    }

    @Test
    fun aResetRemovesWhatWasDoneButKeepsTheStreakAndTheStudyTime() = runBlocking {
        val at = Instant.fromEpochMilliseconds(1_000)
        progress.saveUnitProgress(UnitProgress(50, UnitProgressStatus.COMPLETED, at, at))
        val attempt = (progress.startAttempt(AttemptType.UNIT_ROUND, null, 51, at) as OperationResult.Success).data
        progress.finishAttempt(attempt, AttemptStatus.FINISHED, at, 5, 5)
        progress.saveStreak(Streak(4, 7, LocalDate(2026, 11, 4)))
        progress.addActivity(DailyActivity(LocalDate(2026, 11, 4), xpEarned = 0, secondsStudied = 600, tasksAnswered = 5))

        assertEquals(OperationResult.Success(Unit), progress.resetProgress())

        assertEquals(emptyList(), (progress.unitProgress() as OperationResult.Success).data)
        assertEquals(emptyList(), (progress.attempts() as OperationResult.Success).data)
        assertEquals(4, (progress.streak() as OperationResult.Success).data!!.currentStreak)
        val days = (progress.activity(LocalDate(2026, 11, 4), LocalDate(2026, 11, 4)) as OperationResult.Success).data
        assertEquals(600, days.single().secondsStudied)
    }
}
