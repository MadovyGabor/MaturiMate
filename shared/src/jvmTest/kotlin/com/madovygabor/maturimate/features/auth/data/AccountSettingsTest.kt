package com.madovygabor.maturimate.features.auth.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.auth.domain.AccountUser
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import com.madovygabor.maturimate.features.auth.domain.ExportSaver
import com.madovygabor.maturimate.features.auth.domain.RemoteProfile
import com.madovygabor.maturimate.features.auth.domain.ConsentType
import com.madovygabor.maturimate.features.auth.domain.AccountRepository
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.domain.AuthRepository
import com.madovygabor.maturimate.features.auth.domain.AuthState
import com.madovygabor.maturimate.features.sync.data.SyncStoreImpl
import com.madovygabor.maturimate.features.sync.domain.SyncChanges
import com.madovygabor.maturimate.features.sync.domain.SyncEngine
import com.madovygabor.maturimate.features.sync.domain.SyncError
import com.madovygabor.maturimate.features.sync.domain.SyncProfile
import com.madovygabor.maturimate.features.sync.domain.SyncRepository
import com.madovygabor.maturimate.features.sync.domain.SyncResponse
import com.madovygabor.maturimate.features.sync.domain.SyncSession
import com.madovygabor.maturimate.features.sync.domain.SyncSettings
import com.madovygabor.maturimate.features.learning.data.ProgressRepositoryImpl
import com.madovygabor.maturimate.features.learning.domain.model.AttemptStatus
import com.madovygabor.maturimate.features.learning.domain.model.AttemptType
import com.madovygabor.maturimate.features.learning.domain.model.DailyActivity
import com.madovygabor.maturimate.features.learning.domain.model.Streak
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgress
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgressStatus
import com.madovygabor.maturimate.features.userProfile.data.UserProfileRepositoryImpl
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
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
    private val progress = ProgressRepositoryImpl(database, localUser, Dispatchers.Unconfined, now = { Instant.fromEpochMilliseconds(1_000) })

    private class Auth(private val password: String = "Old12345", offline: Boolean = false) : AuthRepository {
        var newPassword: String? = null
        var signedOut = false
        var offline = offline
        var localSessionCleared = false
        var revoked: String? = null
        override fun refreshToken() = "refresh-1"
        override suspend fun clearLocalSession() { localSessionCleared = true }
        override suspend fun revokeSession(refreshToken: String): OperationResult<Unit, AuthError> =
            if (offline) OperationResult.Error(AuthError.NETWORK) else ok.also { revoked = refreshToken }
        override val state: Flow<AuthState> = emptyFlow()
        override fun currentUser() = AccountUser("account-1", "anna@example.com", "Anna", "Kovács")
        private val ok: OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
        override suspend fun signUp(email: String, password: String) = ok
        override suspend fun confirmSignUp(email: String, code: String) = ok
        override suspend fun resendSignUpCode(email: String) = ok
        override suspend fun signIn(email: String, password: String) =
            if (password == this.password) ok else OperationResult.Error(AuthError.INVALID_CREDENTIALS)
        override suspend fun signOut(): OperationResult<Unit, AuthError> =
            if (offline) OperationResult.Error(AuthError.NETWORK) else ok.also { signedOut = true }
        override suspend fun sendPasswordResetCode(email: String) = ok
        override suspend fun verifyResetCode(email: String, code: String) = ok
        override suspend fun setNewPassword(newPassword: String) = ok.also { this.newPassword = newPassword }
    }

    private fun settings(auth: Auth = Auth(), sync: SyncEngine? = null, revocation: PendingRevocation? = null) =
        AccountSettingsImpl(auth, profiles, localUser, Dispatchers.Unconfined, sync, revocation)

    @Test
    fun aNewNameIsSavedOnThisPhoneAndLeftToTheSync() = runBlocking {
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        assertEquals(OperationResult.Success(Unit), settings().saveNames(" Anna ", "Kiss"))
        val saved = profiles.profile.first()!!
        assertEquals("Anna", saved.firstName)
        assertEquals("Kiss", saved.lastName)
        // an empty name is not saved
        assertEquals(OperationResult.Error(AuthError.UNKNOWN), settings().saveNames(" ", "Kiss"))
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

    private class Repo(var result: OperationResult<SyncResponse, SyncError>) : SyncRepository {
        override suspend fun sync(cursor: String?, deviceId: String, guestMerge: Boolean, changes: SyncChanges) = result
    }

    private val store = SyncStoreImpl(database, localUser, Dispatchers.Unconfined)
    private val signedIn = object : SyncSession {
        override suspend fun isSignedIn() = true
        override suspend fun deviceId() = "dev"
    }
    private fun engine(repo: Repo) = SyncEngine(repo, store, signedIn, now = { 1_000 })

    @Test
    fun aSignOutUploadsFirstAndKeepsEverythingWhenTheUploadFails() = runBlocking {
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        val auth = Auth()
        val repo = Repo(OperationResult.Error(SyncError.NETWORK))
        val before = localUser.userId()

        assertEquals(OperationResult.Error(AuthError.NETWORK), settings(auth, engine(repo)).signOut())

        assertTrue(!auth.signedOut)
        assertEquals(before, localUser.userId())
        assertEquals(TeachingLanguage.HU, profiles.profile.first()!!.teachingLanguage)
    }

    @Test
    fun aSignOutAfterAGoodUploadRemovesTheRows() = runBlocking {
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        val auth = Auth()
        val repo = Repo(OperationResult.Success(SyncResponse(serverTime = 1_000, cursor = "c")))

        assertEquals(OperationResult.Success(Unit), settings(auth, engine(repo)).signOut())

        assertTrue(auth.signedOut)
        assertNull(profiles.profile.first())
        // the sync state went with the user (this test's session always says "signed in", so the new guest may have an empty one)
        val state = database.syncQueries.selectSyncState().executeAsOneOrNull()
        assertTrue(state == null || (state.user_id == localUser.userId() && state.pull_cursor == null))
    }

    @Test
    fun theSyncStateOfTheOldAccountGoesWithTheSignOut() = runBlocking {
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        // signed in while the phone is not a guest's
        val session = object : SyncSession {
            override suspend fun isSignedIn() = !localUser.isGuest()
            override suspend fun deviceId() = "dev"
        }
        localUser.adoptAccount("account-1")
        val engine = SyncEngine(Repo(OperationResult.Error(SyncError.NETWORK)), store, session, now = { 1_000 })
        engine.sync(com.madovygabor.maturimate.features.sync.domain.SyncReason.MANUAL)
        assertTrue(engine.state.value.signedIn)

        assertEquals(OperationResult.Success(Unit), settings(Auth(), engine).signOut(force = true))

        // the new guest sees no sync state and no cloud badge of the old account
        assertTrue(!engine.state.value.signedIn)
        assertNull(engine.state.value.lastError)
    }

    @Test
    fun theUserCanSignOutAnywayAndLoseWhatWasNotUploaded() = runBlocking {
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        val auth = Auth()
        val repo = Repo(OperationResult.Error(SyncError.NETWORK))

        assertEquals(OperationResult.Success(Unit), settings(auth, engine(repo)).signOut(force = true))

        assertTrue(auth.signedOut)
        assertNull(profiles.profile.first())
    }

    @Test
    fun anOfflineSignOutKeepsTheOldSessionToEndItOnTheServerLater() = runBlocking {
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        val auth = Auth(offline = true)
        val revocation = PendingRevocation(database, auth, Dispatchers.Unconfined)

        assertEquals(OperationResult.Success(Unit), settings(auth, null, revocation).signOut())

        // the phone is signed out and empty, the server not told yet
        assertTrue(auth.localSessionCleared)
        assertNull(profiles.profile.first())
        assertTrue(revocation.hasPending())

        // still offline at the next opening: kept
        revocation.revoke()
        assertTrue(revocation.hasPending())
        // the network is back
        auth.offline = false
        revocation.revoke()
        assertEquals("refresh-1", auth.revoked)
        assertTrue(!revocation.hasPending())
    }

    @Test
    fun thePasswordAndTheEmailNeverTouchTheSync() = runBlocking {
        // the sync has no field for the account itself: its shapes hold the profile, the settings and the learning data only
        val fields = (0 until SyncProfile.serializer().descriptor.elementsCount).map { SyncProfile.serializer().descriptor.getElementName(it) }.toSet() +
            (0 until SyncSettings.serializer().descriptor.elementsCount).map { SyncSettings.serializer().descriptor.getElementName(it) }.toSet()
        assertTrue(fields.none { it.contains("password") || it.contains("email") || it.contains("token") }, fields.toString())
        val repo = Repo(OperationResult.Error(SyncError.NETWORK))
        val auth = Auth()
        assertEquals(OperationResult.Success(Unit), settings(auth, engine(repo)).changePassword("Old12345", "New12345"))
        assertEquals("New12345", auth.newPassword)
    }

    private class Server(private val fails: Boolean = false) : AccountRepository {
        var deleted = false
        override suspend fun saveSignUp(profile: UserProfile, firstName: String, lastName: String, consents: Set<ConsentType>, finishedAt: Instant):
            OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
        override suspend fun saveNames(firstName: String, lastName: String): OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
        override suspend fun downloadProfile(): OperationResult<RemoteProfile?, AuthError> = OperationResult.Success(null)
        override suspend fun deleteAccount(): OperationResult<Unit, AuthError> =
            if (fails) OperationResult.Error(AuthError.NETWORK) else OperationResult.Success<Unit, AuthError>(Unit).also { deleted = true }
        override suspend fun exportMyData(): OperationResult<String, AuthError> =
            if (fails) OperationResult.Error(AuthError.NETWORK) else OperationResult.Success("""{"profile":{}}""")
    }

    @Test
    fun aDeletedAccountLeavesNothingOnThisPhone() = runBlocking {
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        val auth = Auth()
        val server = Server()
        val settings = AccountSettingsImpl(auth, profiles, localUser, Dispatchers.Unconfined, account = server)

        assertEquals(OperationResult.Success(Unit), settings.deleteAccount())

        assertTrue(server.deleted)
        // the server no longer knows the user: only the local session goes, no server sign-out
        assertTrue(auth.localSessionCleared)
        assertTrue(!auth.signedOut)
        assertNull(profiles.profile.first())
        assertTrue(localUser.isGuest())
    }

    @Test
    fun withoutANetworkNothingIsDeleted() = runBlocking {
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        val settings = AccountSettingsImpl(Auth(), profiles, localUser, Dispatchers.Unconfined, account = Server(fails = true))

        assertEquals(OperationResult.Error(AuthError.NETWORK), settings.deleteAccount())
        assertEquals(TeachingLanguage.HU, profiles.profile.first()!!.teachingLanguage)
    }

    @Test
    fun theExportIsHandedOverWithTheDateInItsName() = runBlocking {
        var saved: Pair<String, String>? = null
        val saver = ExportSaver { name, text -> saved = name to text; "/home/anna/$name" }
        val settings = AccountSettingsImpl(Auth(), profiles, localUser, Dispatchers.Unconfined, account = Server(), exportSaver = saver, today = { "2026-11-12" })

        assertEquals(OperationResult.Success("/home/anna/maturimate-adataim-2026-11-12.json"), settings.exportData())
        assertEquals("maturimate-adataim-2026-11-12.json" to """{"profile":{}}""", saved)
        val offline = AccountSettingsImpl(Auth(), profiles, localUser, Dispatchers.Unconfined, account = Server(fails = true), exportSaver = saver)
        assertEquals(OperationResult.Error(AuthError.NETWORK), offline.exportData())
    }

    @Test
    fun aResetRemovesWhatWasDoneButKeepsTheStreakAndTheStudyTime() = runBlocking {
        val at = Instant.fromEpochMilliseconds(1_000)
        progress.saveUnitProgress(UnitProgress(50, UnitProgressStatus.COMPLETED, at, at))
        val attempt = (progress.startAttempt(AttemptType.UNIT_ROUND, null, 51, at) as OperationResult.Success).data
        progress.finishAttempt(attempt, AttemptStatus.FINISHED, at, 5, 5)
        progress.saveStreak(Streak(4, 7, LocalDate(2026, 11, 4)))
        progress.addActivity(DailyActivity(LocalDate(2026, 11, 4), secondsStudied = 600, tasksAnswered = 5))

        assertEquals(OperationResult.Success(Unit), progress.resetProgress())

        assertEquals(emptyList(), (progress.unitProgress() as OperationResult.Success).data)
        assertEquals(emptyList(), (progress.attempts() as OperationResult.Success).data)
        assertEquals(4, (progress.streak() as OperationResult.Success).data!!.currentStreak)
        val days = (progress.activity(LocalDate(2026, 11, 4), LocalDate(2026, 11, 4)) as OperationResult.Success).data
        assertEquals(600, days.single().secondsStudied)
        // the profile remembers when, so a sync can tell which progress is older than the reset
        assertEquals(1_000L, database.profileQueries.selectProfile(localUser.userId()).executeAsOneOrNull()?.progress_reset_at)
    }
}
