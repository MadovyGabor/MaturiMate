package com.madovygabor.maturimate.features.auth.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.auth.domain.AccountSession
import com.madovygabor.maturimate.features.auth.domain.AccountUser
import com.madovygabor.maturimate.features.auth.domain.SignInsInFlight
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import com.madovygabor.maturimate.features.auth.domain.ExportSaver
import com.madovygabor.maturimate.features.auth.domain.RemoteProfile
import com.madovygabor.maturimate.features.auth.domain.ConsentType
import com.madovygabor.maturimate.features.auth.domain.AccountRepository
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.domain.AuthRepository
import com.madovygabor.maturimate.features.auth.domain.AuthState
import com.madovygabor.maturimate.features.auth.domain.DeletionProof
import com.madovygabor.maturimate.features.auth.domain.FreshSignInCheck
import com.madovygabor.maturimate.features.sync.domain.SyncOutcome
import com.madovygabor.maturimate.features.sync.domain.SyncReason
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
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
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class AccountSettingsTest {

    // the app's setup: one thread runs the database calls (AppModul); with Unconfined a ViewModel on the main thread and the
    // test could use the one connection at the same time
    private val dbDispatcher = Dispatchers.IO.limitedParallelism(1)

    private companion object {
        const val EXPORT = """{"profile":{"first_name":"Anna","last_name":"Kovács"},"subjects":[{"subject_id":1}]}"""
    }

    private val database = UserDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) })
    private var guests = 0
    private val localUser = LocalUserProvider(database) { "guest-${++guests}" }
    private val profiles = UserProfileRepositoryImpl(database, localUser, dbDispatcher)
    private val progress = ProgressRepositoryImpl(database, localUser, dbDispatcher, now = { Instant.fromEpochMilliseconds(1_000) })

    // the settings act on the account this phone belongs to; the guest phone with a session is set up in its own tests
    init {
        localUser.adoptAccount("account-1")
    }

    /** A guest phone (this phone's rows were never the account's) while a Supabase session is open: a sign-in of an account that is not set up. */
    private fun guestPhone() {
        localUser.removeUser()
        check(localUser.isGuest())
    }

    private class Auth(private val password: String = "Old12345", offline: Boolean = false) : AuthRepository {
        var newPassword: String? = null
        var codesSent = 0
        var signIns = 0
        var user: AccountUser? = AccountUser("account-1", "anna@example.com", "Anna", "Kovács")
        var signedOut = false
        var offline = offline
        var localSessionCleared = false
        var revoked: String? = null
        val revokedAll = mutableListOf<String>()
        /** The server answers the sign-out with a 5xx; supabase-kt has cleared the session by then. */
        var signOutFails = false
        /** The server answers the revocation with an error (a 5xx): neither done nor known to be gone. */
        var serverFails = false
        override fun refreshToken() = "refresh-1"
        override suspend fun clearLocalSession() { localSessionCleared = true }
        override suspend fun revokeSession(refreshToken: String): OperationResult<Unit, AuthError> = when {
            offline -> OperationResult.Error(AuthError.NETWORK)
            serverFails -> OperationResult.Error(AuthError.UNKNOWN)
            else -> ok.also { revoked = refreshToken; revokedAll += refreshToken }
        }
        override val state: Flow<AuthState> = emptyFlow()
        override fun currentUser() = user
        private val ok: OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
        override suspend fun signUp(email: String, password: String) = ok
        override suspend fun confirmSignUp(email: String, code: String) = ok
        override suspend fun resendSignUpCode(email: String) = ok
        override suspend fun signIn(email: String, password: String): OperationResult<Unit, AuthError> {
            signIns++
            return if (password == this.password) ok else OperationResult.Error(AuthError.INVALID_CREDENTIALS)
        }
        override suspend fun signOut(): OperationResult<Unit, AuthError> = when {
            offline -> OperationResult.Error(AuthError.NETWORK)
            signOutFails -> OperationResult.Error(AuthError.UNKNOWN)
            else -> ok.also { signedOut = true }
        }
        override suspend fun sendPasswordResetCode(email: String) = ok
        override suspend fun verifyResetCode(email: String, code: String) = ok
        override suspend fun setNewPassword(newPassword: String) = ok.also { this.newPassword = newPassword }
        override suspend fun sendReauthenticationCode() = ok.also { codesSent++ }
        /** The server checks the code: "123456" is the one it sent. */
        /** The current password the change carried to the server. */
        var sentCurrentPassword: String? = null
        override suspend fun changePasswordWithCode(code: String, newPassword: String, currentPassword: String?): OperationResult<Unit, AuthError> {
            sentCurrentPassword = currentPassword
            return when {
                code != "123456" -> OperationResult.Error(AuthError.INVALID_CODE)
                newPassword == password -> OperationResult.Error(AuthError.SAME_PASSWORD)
                else -> ok.also { this.newPassword = newPassword }
            }
        }

        var passwordChecks = 0
        var rateLimited = false
        /** The refresh token of a check whose session could not be ended (the server's logout failed). */
        var leftover: String? = null
        override suspend fun verifyPassword(password: String, whileOpen: (suspend (accessToken: String) -> Unit)?): OperationResult<String?, AuthError> {
            passwordChecks++
            return when {
                user == null -> OperationResult.Error(AuthError.SESSION_EXPIRED)
                rateLimited -> OperationResult.Error(AuthError.TOO_MANY_REQUESTS)
                password != this.password -> OperationResult.Error(AuthError.INVALID_CREDENTIALS)
                // the check's own fresh session, lent out before it ends
                else -> OperationResult.Success<String?, AuthError>(leftover).also { whileOpen?.invoke("check-access") }
            }
        }

        /** The phone's session: after a Google proof, the fresh one of the same account. */
        var phoneSession: AccountSession? = AccountSession("account-1", "phone-access")
        override fun currentSession() = phoneSession
        /** What the phone's session was when the cancel of a proof compared the sessions. */
        var sessionAtCancel: AccountSession? = null

        var marked = 0
        var markResult: OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
        var freshSignIn: OperationResult<Unit, AuthError> = OperationResult.Error(AuthError.PROOF_NEEDED)
        /** Sessions the check or the cancel could not end on the server. */
        var unended: List<String> = emptyList()
        var cancels = 0
        override suspend fun markSessionBeforeProof(): OperationResult<Unit, AuthError> { marked++; return markResult }
        override suspend fun checkFreshSignIn() = FreshSignInCheck(freshSignIn, unended)
        override suspend fun cancelFreshSignIn(): FreshSignInCheck { cancels++; sessionAtCancel = phoneSession; return FreshSignInCheck(OperationResult.Success(Unit), unended) }
    }

    /** The pending token's store in memory (the app's is the session's settings store). */
    private class MemoryTokens : PendingTokenStore {
        var tokens: List<String> = emptyList()
        override fun read() = tokens
        override fun write(tokens: List<String>) { this.tokens = tokens }
    }

    private fun settings(auth: Auth = Auth(), sync: SyncEngine? = null, revocation: PendingRevocation? = null) =
        AccountSettingsImpl(auth, profiles, localUser, dbDispatcher, sync, revocation)

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
    fun thePasswordChangesWithTheEmailedCodeAndAStrongNewOne() = runBlocking {
        val auth = Auth()
        val settings = settings(auth)
        assertEquals(OperationResult.Success(Unit), settings.sendPasswordCode())
        assertEquals(1, auth.codesSent)

        assertEquals(OperationResult.Error(AuthError.INVALID_CODE), settings.changePassword("Old12345", "000000", "New12345"))
        assertEquals(OperationResult.Error(AuthError.INVALID_CODE), settings.changePassword("Old12345", "  ", "New12345"))
        assertEquals(OperationResult.Error(AuthError.WEAK_PASSWORD), settings.changePassword("Old12345", "123456", "short"))
        assertEquals(OperationResult.Error(AuthError.SAME_PASSWORD), settings.changePassword("Old12345", "123456", "Old12345"))
        assertNull(auth.newPassword)
        assertEquals(OperationResult.Success(Unit), settings.changePassword("Old12345", " 123456 ", "New12345"))
        assertEquals("New12345", auth.newPassword)
        // the old password is checked by the server, never as a sign-in of this phone: the session stays as it is
        assertEquals(0, auth.signIns)
    }

    // Card 11: GoTrue checks the code only on sessions older than 24 hours, so on a fresh session any code changed the
    // password. The old password, checked by the server, is the other half of the proof.
    @Test
    fun thePasswordChangeNeedsTheOldPasswordToo() = runBlocking {
        val auth = Auth()
        val settings = settings(auth)

        assertEquals(OperationResult.Error(AuthError.INVALID_CREDENTIALS), settings.changePassword("Wrong123", "123456", "New12345"))
        assertEquals(OperationResult.Error(AuthError.INVALID_CREDENTIALS), settings.changePassword("  ", "123456", "New12345"))
        assertNull(auth.newPassword)
        // both are needed: the right old password with a wrong code changes nothing either
        assertEquals(OperationResult.Error(AuthError.INVALID_CODE), settings.changePassword("Old12345", "000000", "New12345"))
        assertNull(auth.newPassword)

        auth.rateLimited = true
        assertEquals(OperationResult.Error(AuthError.TOO_MANY_REQUESTS), settings.changePassword("Old12345", "123456", "New12345"))
        assertNull(auth.newPassword)
    }

    @Test
    fun aPasswordCheckWhoseSessionLivesOnIsEndedLater() = runBlocking {
        val auth = Auth().apply { leftover = "check-refresh" }
        val tokens = MemoryTokens()

        assertEquals(OperationResult.Success(Unit), settings(auth, revocation = PendingRevocation(database, auth, dbDispatcher, tokens)).changePassword("Old12345", "123456", "New12345"))

        // the change went through, and the check's session waits to be ended at the next opening
        assertEquals("New12345", auth.newPassword)
        assertEquals(listOf("check-refresh"), tokens.tokens)
    }

    @Test
    fun anAccountWithoutAPasswordHasNoPasswordToChange() = runBlocking {
        val auth = Auth().apply { user = AccountUser("account-1", "anna@example.com", "Anna", "Kovács", hasPassword = false) }

        assertEquals(OperationResult.Error(AuthError.UNKNOWN), settings(auth).changePassword("Old12345", "123456", "New12345"))
        assertEquals(0, auth.passwordChecks)
        assertNull(auth.newPassword)
    }

    // A session without an e-mail address used to say "the old password is wrong".
    @Test
    fun aMissingSessionOrEmailIsNeverAWrongPassword() = runBlocking {
        val auth = Auth().apply { user = null }
        assertEquals(OperationResult.Error(AuthError.SESSION_EXPIRED), settings(auth).sendPasswordCode())
        assertEquals(OperationResult.Error(AuthError.SESSION_EXPIRED), settings(auth).changePassword("Old12345", "123456", "New12345"))
        auth.user = AccountUser("account-1", null, null, null)
        assertEquals(OperationResult.Error(AuthError.UNKNOWN), settings(auth).sendPasswordCode())
        assertEquals(0, auth.codesSent)
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

    // N1: on a guest phone the session is not this phone's account; the sign-out ends the session and keeps the guest's
    // data, which was never synced anywhere.
    @Test
    fun aSignOutOnAGuestPhoneEndsTheSessionAndKeepsTheGuestsData() = runBlocking {
        guestPhone()
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        progress.saveUnitProgress(UnitProgress(50, UnitProgressStatus.COMPLETED, Instant.fromEpochMilliseconds(1), Instant.fromEpochMilliseconds(1)))
        val guest = localUser.userId()
        val auth = Auth()

        assertEquals(OperationResult.Success(Unit), settings(auth).signOut())

        assertTrue(auth.signedOut)
        assertEquals(guest, localUser.userId())
        assertEquals(TeachingLanguage.HU, profiles.profile.first()!!.teachingLanguage)
        assertEquals(1, (progress.unitProgress() as OperationResult.Success).data.size)
    }

    @Test
    fun deletingTheAccountOnAGuestPhoneKeepsTheGuestsData() = runBlocking {
        guestPhone()
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        val guest = localUser.userId()
        val auth = Auth()
        val server = Server()

        assertEquals(OperationResult.Success(Unit), AccountSettingsImpl(auth, profiles, localUser, dbDispatcher, account = server).deleteAccount(DeletionProof.Password("Old12345")))

        assertTrue(server.deleted)
        assertTrue(auth.localSessionCleared)
        assertEquals(guest, localUser.userId())
        assertEquals(TeachingLanguage.HU, profiles.profile.first()!!.teachingLanguage)
    }

    @Test
    fun theSettingsKnowAGuestPhone() = runBlocking {
        assertTrue(!settings().isGuestPhone())
        guestPhone()
        assertTrue(settings().isGuestPhone())
    }

    private class Repo(var result: OperationResult<SyncResponse, SyncError>) : SyncRepository {
        override suspend fun sync(cursor: String?, deviceId: String, guestMerge: Boolean, changes: SyncChanges) = result
    }

    private val store = SyncStoreImpl(database, localUser, dbDispatcher)
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
        val revocation = PendingRevocation(database, auth, dbDispatcher, MemoryTokens())

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

    // A 5xx used to count as "the token is dead", so the pending session was dropped without being ended.
    @Test
    fun aServerFailureKeepsThePendingSessionForALaterTry() = runBlocking {
        val auth = Auth(offline = true)
        val revocation = PendingRevocation(database, auth, dbDispatcher, MemoryTokens())
        assertEquals(OperationResult.Success(Unit), settings(auth, null, revocation).signOut())

        auth.offline = false
        auth.serverFails = true
        revocation.revoke()
        assertTrue(revocation.hasPending())

        auth.serverFails = false
        revocation.revoke()
        assertEquals("refresh-1", auth.revoked)
        assertTrue(!revocation.hasPending())
    }

    // The token used to wait in the user database, which Android backs up and moves to a new phone.
    @Test
    fun thePendingTokenWaitsOutsideTheUserDatabase() = runBlocking {
        val auth = Auth(offline = true)
        val tokens = MemoryTokens()
        val revocation = PendingRevocation(database, auth, dbDispatcher, tokens)

        assertEquals(OperationResult.Success(Unit), settings(auth, null, revocation).signOut())

        assertEquals(listOf("refresh-1"), tokens.tokens)
        assertNull(database.syncQueries.selectPendingRevocation().executeAsOneOrNull())
    }

    @Test
    fun aTokenAnOlderVersionLeftInTheDatabaseIsMovedOnceAndStillEnded() = runBlocking {
        database.syncQueries.savePendingRevocation("refresh-old")
        val auth = Auth(offline = true)
        val tokens = MemoryTokens()
        val revocation = PendingRevocation(database, auth, dbDispatcher, tokens)

        // still offline: moved to the store, gone from the database, kept for the next try
        revocation.revoke()
        assertEquals(listOf("refresh-old"), tokens.tokens)
        assertNull(database.syncQueries.selectPendingRevocation().executeAsOneOrNull())

        auth.offline = false
        revocation.revoke()
        assertEquals("refresh-old", auth.revoked)
        assertEquals(emptyList(), tokens.tokens)
    }

    // A new offline sign-out used to overwrite the token still waiting (one moved from the database, or an earlier one).
    @Test
    fun aSecondOfflineSignOutKeepsTheFirstTokenToo() = runBlocking {
        database.syncQueries.savePendingRevocation("refresh-old")
        val auth = Auth(offline = true)
        val tokens = MemoryTokens()
        val revocation = PendingRevocation(database, auth, dbDispatcher, tokens)

        assertEquals(OperationResult.Success(Unit), settings(auth, null, revocation).signOut())
        assertEquals(listOf("refresh-old", "refresh-1"), tokens.tokens)

        auth.offline = false
        revocation.revoke()
        assertEquals(listOf("refresh-old", "refresh-1"), auth.revokedAll)
        assertTrue(!revocation.hasPending())
    }

    // A 5xx of the sign-out (after supabase-kt had already cleared the session) left the rows and kept nothing to revoke.
    @Test
    fun aServerFailureOfTheSignOutStillSignsOutAndEndsTheSessionLater() = runBlocking {
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        val auth = Auth().apply { signOutFails = true }
        val tokens = MemoryTokens()
        val revocation = PendingRevocation(database, auth, dbDispatcher, tokens)

        assertEquals(OperationResult.Success(Unit), settings(auth, null, revocation).signOut())

        assertTrue(auth.localSessionCleared)
        assertNull(profiles.profile.first())
        assertTrue(localUser.isGuest())
        assertEquals(listOf("refresh-1"), tokens.tokens)
    }

    @Test
    fun thePasswordAndTheEmailNeverTouchTheSync() = runBlocking {
        // the sync has no field for the account itself: its shapes hold the profile, the settings and the learning data only
        val fields = (0 until SyncProfile.serializer().descriptor.elementsCount).map { SyncProfile.serializer().descriptor.getElementName(it) }.toSet() +
            (0 until SyncSettings.serializer().descriptor.elementsCount).map { SyncSettings.serializer().descriptor.getElementName(it) }.toSet()
        assertTrue(fields.none { it.contains("password") || it.contains("email") || it.contains("token") }, fields.toString())
        val repo = Repo(OperationResult.Error(SyncError.NETWORK))
        val auth = Auth()
        assertEquals(OperationResult.Success(Unit), settings(auth, engine(repo)).changePassword("Old12345", "123456", "New12345"))
        assertEquals("New12345", auth.newPassword)
    }

    private class Server(private val fails: Boolean = false, private val refusal: AuthError? = null) : AccountRepository {
        var deleted = false
        /** The tokens the deletions went out with. */
        val tokens = mutableListOf<String>()
        override suspend fun saveSignUp(profile: UserProfile, firstName: String, lastName: String, consents: Set<ConsentType>, finishedAt: Instant):
            OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
        override suspend fun saveNames(firstName: String, lastName: String): OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
        override suspend fun downloadProfile(): OperationResult<RemoteProfile?, AuthError> = OperationResult.Success(null)
        override suspend fun deleteAccount(accessToken: String): OperationResult<Unit, AuthError> {
            tokens += accessToken
            return when {
                fails -> OperationResult.Error(AuthError.NETWORK)
                refusal != null -> OperationResult.Error(refusal)
                else -> OperationResult.Success<Unit, AuthError>(Unit).also { deleted = true }
            }
        }
        override suspend fun exportMyData(): OperationResult<String, AuthError> =
            if (fails) OperationResult.Error(AuthError.NETWORK) else OperationResult.Success(EXPORT)
    }

    @Test
    fun aDeletedAccountLeavesNothingOnThisPhone() = runBlocking {
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        val auth = Auth()
        val server = Server()
        val settings = AccountSettingsImpl(auth, profiles, localUser, dbDispatcher, account = server)

        assertEquals(OperationResult.Success(Unit), settings.deleteAccount(DeletionProof.Password("Old12345")))

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
        val settings = AccountSettingsImpl(Auth(), profiles, localUser, dbDispatcher, account = Server(fails = true))

        assertEquals(OperationResult.Error(AuthError.NETWORK), settings.deleteAccount(DeletionProof.Password("Old12345")))
        assertEquals(TeachingLanguage.HU, profiles.profile.first()!!.teachingLanguage)
    }

    // Card 11: the deletion asked only for a typed word; a phone left open was enough to delete the account for good.
    @Test
    fun aDeletionWithoutTheRightPasswordDeletesNothing() = runBlocking {
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        val server = Server()
        val auth = Auth()
        val settings = AccountSettingsImpl(auth, profiles, localUser, dbDispatcher, account = server)

        assertEquals(OperationResult.Error(AuthError.INVALID_CREDENTIALS), settings.deleteAccount(DeletionProof.Password("Wrong123")))
        assertEquals(OperationResult.Error(AuthError.INVALID_CREDENTIALS), settings.deleteAccount(DeletionProof.Password(" ")))
        // an account with a password cannot use the Google proof instead
        assertEquals(OperationResult.Error(AuthError.PROOF_NEEDED), settings.deleteAccount(DeletionProof.FreshSignIn))
        auth.rateLimited = true
        assertEquals(OperationResult.Error(AuthError.TOO_MANY_REQUESTS), settings.deleteAccount(DeletionProof.Password("Old12345")))

        assertTrue(!server.deleted)
        assertEquals(TeachingLanguage.HU, profiles.profile.first()!!.teachingLanguage)
        assertTrue(!localUser.isGuest())
    }

    private fun googleAccount() = Auth().apply { user = AccountUser("account-1", "anna@example.com", "Anna", "Kovács", hasPassword = false) }

    @Test
    fun aGoogleAccountIsDeletedOnlyAfterAFreshSignInOfTheSameAccount() = runBlocking {
        val server = Server()
        val auth = googleAccount()
        val settings = AccountSettingsImpl(auth, profiles, localUser, dbDispatcher, account = server)

        // no proof yet, and a password is no proof for an account without one
        assertEquals(OperationResult.Error(AuthError.PROOF_NEEDED), settings.deleteAccount(DeletionProof.FreshSignIn))
        assertEquals(OperationResult.Error(AuthError.PROOF_NEEDED), settings.deleteAccount(DeletionProof.Password("Old12345")))
        assertTrue(!server.deleted)

        settings.startFreshSignInProof()
        assertEquals(1, auth.marked)
        auth.freshSignIn = OperationResult.Success(Unit)
        assertEquals(OperationResult.Success(Unit), settings.finishFreshSignInProof())
        assertEquals(OperationResult.Success(Unit), settings.deleteAccount(DeletionProof.FreshSignIn))
        assertTrue(server.deleted)
    }

    @Test
    fun aSignInOfAnotherAccountIsNoProofAndLeavesThePhoneAsItWas() = runBlocking {
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        val server = Server()
        val auth = googleAccount().apply { freshSignIn = OperationResult.Error(AuthError.WRONG_ACCOUNT) }
        val settings = AccountSettingsImpl(auth, profiles, localUser, dbDispatcher, account = server)

        settings.startFreshSignInProof()
        assertEquals(OperationResult.Error(AuthError.WRONG_ACCOUNT), settings.finishFreshSignInProof())
        assertEquals(OperationResult.Error(AuthError.PROOF_NEEDED), settings.deleteAccount(DeletionProof.FreshSignIn))

        assertTrue(!server.deleted)
        assertEquals("account-1", localUser.userId())
        assertEquals(TeachingLanguage.HU, profiles.profile.first()!!.teachingLanguage)
    }

    @Test
    fun theFreshSignInProofIsUsedOnceAndRunsOut() = runBlocking {
        // N-6: a monotonic time source (the wall clock can be set back by the user)
        val time = TestTimeSource()
        val server = Server(fails = true)
        val auth = googleAccount().apply { freshSignIn = OperationResult.Success(Unit) }
        val settings = AccountSettingsImpl(auth, profiles, localUser, dbDispatcher, account = server, timeSource = time)

        settings.startFreshSignInProof()
        settings.finishFreshSignInProof()
        // used by a deletion that failed: a new try needs a new sign-in
        assertEquals(OperationResult.Error(AuthError.NETWORK), settings.deleteAccount(DeletionProof.FreshSignIn))
        assertEquals(OperationResult.Error(AuthError.PROOF_NEEDED), settings.deleteAccount(DeletionProof.FreshSignIn))

        settings.startFreshSignInProof()
        settings.finishFreshSignInProof()
        time += 5.minutes + 1.seconds
        assertEquals(OperationResult.Error(AuthError.PROOF_NEEDED), settings.deleteAccount(DeletionProof.FreshSignIn))
    }

    // ---- card 12 (N-8): the server checks the proof itself; the deletion goes out with the proof's own session ----

    @Test
    fun aPasswordDeletionGoesOutWithTheChecksOwnFreshSession() = runBlocking {
        val server = Server()
        val settings = AccountSettingsImpl(Auth(), profiles, localUser, dbDispatcher, account = server)

        assertEquals(OperationResult.Success(Unit), settings.deleteAccount(DeletionProof.Password("Old12345")))

        // not the phone's session, which may be days old: the server wants a sign-in of the last 5 minutes
        assertEquals(listOf("check-access"), server.tokens)
    }

    @Test
    fun aGoogleDeletionGoesOutWithTheFreshSignInsSession() = runBlocking {
        val server = Server()
        val auth = googleAccount().apply { freshSignIn = OperationResult.Success(Unit); phoneSession = AccountSession("account-1", "fresh-google-access") }
        val settings = AccountSettingsImpl(auth, profiles, localUser, dbDispatcher, account = server)

        settings.startFreshSignInProof()
        settings.finishFreshSignInProof()
        assertEquals(OperationResult.Success(Unit), settings.deleteAccount(DeletionProof.FreshSignIn))

        assertEquals(listOf("fresh-google-access"), server.tokens)
    }

    @Test
    fun aDeletionTheServerFindsUnprovenDeletesNothingHere() = runBlocking {
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        val auth = Auth()
        val settings = AccountSettingsImpl(auth, profiles, localUser, dbDispatcher, account = Server(refusal = AuthError.PROOF_NEEDED))

        assertEquals(OperationResult.Error(AuthError.PROOF_NEEDED), settings.deleteAccount(DeletionProof.Password("Old12345")))

        assertTrue(!auth.localSessionCleared)
        assertEquals(TeachingLanguage.HU, profiles.profile.first()!!.teachingLanguage)
        assertTrue(!localUser.isGuest())
    }

    @Test
    fun aGoogleProofWithoutASessionLeftDeletesNothing() = runBlocking {
        val server = Server()
        val auth = googleAccount().apply { freshSignIn = OperationResult.Success(Unit) }
        val settings = AccountSettingsImpl(auth, profiles, localUser, dbDispatcher, account = server)
        settings.startFreshSignInProof()
        settings.finishFreshSignInProof()
        auth.phoneSession = null

        assertEquals(OperationResult.Error(AuthError.SESSION_EXPIRED), settings.deleteAccount(DeletionProof.FreshSignIn))
        assertTrue(server.tokens.isEmpty())
    }

    // Card 12 review F3: the user check and the token come from one snapshot of the session
    @Test
    fun aGoogleDeletionWithAnotherAccountsSessionInPlaceDeletesNothing() = runBlocking {
        val server = Server()
        val auth = googleAccount().apply { freshSignIn = OperationResult.Success(Unit) }
        val settings = AccountSettingsImpl(auth, profiles, localUser, dbDispatcher, account = server)
        settings.startFreshSignInProof()
        settings.finishFreshSignInProof()
        // the session changed after the proof (another account's sign-in came in)
        auth.phoneSession = AccountSession("account-2", "other-access")

        assertEquals(OperationResult.Error(AuthError.PROOF_NEEDED), settings.deleteAccount(DeletionProof.FreshSignIn))
        assertTrue(server.tokens.isEmpty())
    }

    // Card 12 review F2: a desktop exchange that ends after the proof was cancelled saved its session after the cancel
    // had compared the sessions: the cancel now waits for the sign-ins in flight
    @Test
    fun aCancelledProofWaitsForASignInStillInFlight() = runBlocking {
        val signIns = SignInsInFlight()
        val auth = googleAccount()
        val settings = AccountSettingsImpl(auth, profiles, localUser, dbDispatcher, account = Server(), signIns = signIns)
        settings.startFreshSignInProof()
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val late = launch(Dispatchers.Default) {
            signIns.track {
                started.complete(Unit)
                kotlinx.coroutines.delay(300)
                // the late exchange saves another account's session
                auth.phoneSession = AccountSession("account-2", "late-access")
            }
        }
        started.await()

        settings.cancelFreshSignInProof()

        assertEquals(AccountSession("account-2", "late-access"), auth.sessionAtCancel)
        late.join()
    }

    // ---- card 11, security round: the sync during a proof, a cancelled proof, a foreign session ----

    /** A sync whose server answers every call (so a run shows as a call). */
    private class CountingRepo : SyncRepository {
        var calls = 0
        override suspend fun sync(cursor: String?, deviceId: String, guestMerge: Boolean, changes: SyncChanges): OperationResult<SyncResponse, SyncError> {
            calls++
            return OperationResult.Success(SyncResponse(serverTime = 1_000, cursor = "c", more = false))
        }
    }

    // S1-1: the proof's Google sign-in puts another account's session in place for a moment; no sync may run meanwhile.
    @Test
    fun theSyncWaitsDuringTheProofAndRunsAfterIt() = runBlocking {
        val repo = CountingRepo()
        val engine = SyncEngine(repo, store, signedIn, now = { 1_000 })
        val auth = googleAccount().apply { freshSignIn = OperationResult.Success(Unit) }
        val settings = AccountSettingsImpl(auth, profiles, localUser, dbDispatcher, sync = engine, account = Server())

        settings.startFreshSignInProof()
        assertEquals(SyncOutcome.Queued, engine.sync(SyncReason.ROUND_END))
        assertEquals(0, repo.calls)

        settings.finishFreshSignInProof()
        // released: the run asked for meanwhile went now
        assertEquals(1, repo.calls)
    }

    @Test
    fun everyEndOfTheProofReleasesTheSync() = runBlocking {
        val repo = CountingRepo()
        val engine = SyncEngine(repo, store, signedIn, now = { 1_000 })
        val auth = googleAccount()
        val settings = AccountSettingsImpl(auth, profiles, localUser, dbDispatcher, sync = engine, account = Server())

        // a failed or wrong-account check
        settings.startFreshSignInProof()
        auth.freshSignIn = OperationResult.Error(AuthError.WRONG_ACCOUNT)
        settings.finishFreshSignInProof()
        assertTrue(engine.sync(SyncReason.ROUND_END) is SyncOutcome.Done)

        // a cancel (Google failed or closed)
        settings.startFreshSignInProof()
        settings.cancelFreshSignInProof()
        assertEquals(1, auth.cancels)
        assertTrue(engine.sync(SyncReason.ROUND_END) is SyncOutcome.Done)

        // a start that could not remember the session: Google is not opened and nothing stays held
        auth.markResult = OperationResult.Error(AuthError.NETWORK)
        assertEquals(OperationResult.Error(AuthError.NETWORK), settings.startFreshSignInProof())
        assertTrue(engine.sync(SyncReason.ROUND_END) is SyncOutcome.Done)
    }

    // N-2: the sessions a proof could not end are ended later, not forgotten.
    @Test
    fun sessionsAProofCouldNotEndWaitForTheRevocation() = runBlocking {
        val tokens = MemoryTokens()
        val auth = googleAccount().apply { freshSignIn = OperationResult.Error(AuthError.WRONG_ACCOUNT); unended = listOf("other-refresh") }
        val settings = AccountSettingsImpl(auth, profiles, localUser, dbDispatcher, revocation = PendingRevocation(database, auth, dbDispatcher, tokens), account = Server())

        settings.startFreshSignInProof()
        settings.finishFreshSignInProof()
        assertEquals(listOf("other-refresh"), tokens.tokens)

        auth.unended = listOf("cancel-refresh")
        settings.startFreshSignInProof()
        settings.cancelFreshSignInProof()
        assertEquals(listOf("other-refresh", "cancel-refresh"), tokens.tokens)
    }

    // S2-1: an account's phone left with another account's session (a proof that never finished) ends that session only.
    @Test
    fun aForeignSessionOnAnAccountsPhoneIsEndedAndTheDataStays() = runBlocking {
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        val auth = Auth().apply { user = AccountUser("account-2", "bea@example.com", "Bea", "Nagy") }
        val settings = settings(auth)

        assertTrue(settings.endForeignSession())

        assertTrue(auth.signedOut)
        assertEquals("account-1", localUser.userId())
        assertEquals(TeachingLanguage.HU, profiles.profile.first()!!.teachingLanguage)
    }

    @Test
    fun theOwnSessionAGuestsSessionAndARunningProofAreNotForeign() = runBlocking {
        // the phone's own account
        val own = Auth()
        assertFalse(settings(own).endForeignSession())
        assertFalse(own.signedOut)

        // during a proof another account's session is expected for a moment
        val other = googleAccount().apply { user = AccountUser("account-2", "bea@example.com", "Bea", "Nagy", hasPassword = false) }
        val proving = settings(other)
        proving.startFreshSignInProof()
        assertFalse(proving.endForeignSession())
        assertFalse(other.signedOut)

        // a guest phone with a session: an account that is not set up, the phone's to use
        guestPhone()
        val guest = Auth().apply { user = AccountUser("account-2", "bea@example.com", "Bea", "Nagy") }
        assertFalse(settings(guest).endForeignSession())
        assertFalse(guest.signedOut)
    }

    @Test
    fun aForeignSessionOfflineIsForgottenHereAndEndedLater() = runBlocking {
        val tokens = MemoryTokens()
        val auth = Auth(offline = true).apply { user = AccountUser("account-2", "bea@example.com", "Bea", "Nagy") }

        assertTrue(settings(auth, revocation = PendingRevocation(database, auth, dbDispatcher, tokens)).endForeignSession())

        assertTrue(auth.localSessionCleared)
        assertEquals(listOf("refresh-1"), tokens.tokens)
        assertEquals("account-1", localUser.userId())
    }

    // S2-2: the current password goes with the change, for the server's own check
    @Test
    fun theChangeCarriesTheCurrentPasswordToTheServer() = runBlocking {
        val auth = Auth()
        assertEquals(OperationResult.Success(Unit), settings(auth).changePassword("Old12345", "123456", "New12345"))
        assertEquals("Old12345", auth.sentCurrentPassword)
    }

    @Test
    fun theExportIsHandedOverWithTheDateInItsName() = runBlocking {
        var saved: Pair<String, String>? = null
        val saver = ExportSaver { name, text -> saved = name to text; "/home/anna/$name" }
        val settings = AccountSettingsImpl(Auth(), profiles, localUser, dbDispatcher, account = Server(), exportSaver = saver, today = { "2026-11-12" })

        assertEquals(OperationResult.Success("/home/anna/maturimate-adataim-2026-11-12.json"), settings.exportData())
        assertEquals("maturimate-adataim-2026-11-12.json", saved?.first)
        // the same data, one field per line, so a person can read the file
        val text = saved!!.second
        assertEquals(Json.parseToJsonElement(EXPORT), Json.parseToJsonElement(text))
        assertTrue("\n    \"profile\": {\n        \"first_name\": \"Anna\"" in text, text)
        val offline = AccountSettingsImpl(Auth(), profiles, localUser, dbDispatcher, account = Server(fails = true), exportSaver = saver)
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
