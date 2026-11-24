package com.madovygabor.maturimate.features.auth.data

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.auth.domain.AccountSession
import com.madovygabor.maturimate.features.auth.domain.AccountUser
import com.madovygabor.maturimate.features.auth.domain.SignInsInFlight
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.domain.DeletionProof
import com.madovygabor.maturimate.features.sync.domain.SyncOutcome
import com.madovygabor.maturimate.features.sync.domain.SyncReason
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource
import com.madovygabor.maturimate.features.sync.domain.SyncChanges
import com.madovygabor.maturimate.features.sync.domain.SyncEngine
import com.madovygabor.maturimate.features.sync.domain.SyncError
import com.madovygabor.maturimate.features.sync.domain.SyncRepository
import com.madovygabor.maturimate.features.sync.domain.SyncResponse
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import io.github.jan.supabase.auth.Auth as SupabaseAuth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class DeletionProofTest : AccountSettingsImplTestBase() {

    private val requests = mutableListOf<Pair<String, String>>()

    /** The Authorization header of each call (never printed by a test). */
    private val authorizations = mutableListOf<String?>()

    /** A Supabase client signed in as [USER_ID], answering every Postgrest call from [rows]. */
    private fun remote(status: HttpStatusCode = HttpStatusCode.OK, signedIn: Boolean = true, rows: (String) -> String = { "[]" }): SupabaseAccountRepository = runBlocking {
        val client = createSupabaseClient("https://test.supabase.co", "test-key") {
            httpEngine = MockEngine { request ->
                val body = (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()
                val table = request.url.encodedPath.substringAfterLast('/')
                requests += "${request.method.value} $table" to body
                authorizations += request.headers[HttpHeaders.Authorization]
                respond(rows(table), status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
            install(SupabaseAuth) {
                sessionManager = MemorySessionManager()
                codeVerifierCache = MemoryCodeVerifierCache()
                autoLoadFromStorage = false
                alwaysAutoRefresh = false
                enableLifecycleCallbacks = false
            }
            install(Postgrest)
        }
        // the stored-session check must be over, or it could end after the import and drop it
        client.auth.awaitInitialization()
        if (signedIn) client.auth.importSession(
            UserSession(accessToken = "access", refreshToken = "refresh", expiresIn = 3600, tokenType = "bearer", user = UserInfo(aud = "authenticated", id = USER_ID))
        )
        SupabaseAccountRepository(client.auth, client.postgrest)
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

    // Card 12 (N-8): the deletion goes out with the proof's own fresh session, not the phone's, so the server can check
    // the proof itself (a sign-in of the last 5 minutes in the token, migration 20261128120000)
    @Test
    fun theDeletionCarriesTheProofsTokenNotThePhonesSession() = runBlocking {
        assertEquals(OperationResult.Success(Unit), remote().deleteAccount("proof-token"))

        assertEquals("POST delete_my_account", requests.single().first)
        assertEquals("Bearer proof-token", authorizations.single())
    }

    @Test
    fun aDeletionTheServerRefusesForAnOldSignInNeedsANewProof() = runBlocking {
        val stale = remote(status = HttpStatusCode.Forbidden) { """{"code":"42501","details":null,"hint":null,"message":"recent_login_required"}""" }
        assertEquals(OperationResult.Error(AuthError.PROOF_NEEDED), stale.deleteAccount("old-token"))

        val expired = remote(status = HttpStatusCode.Unauthorized) { """{"code":"PGRST301","details":null,"hint":null,"message":"JWT expired"}""" }
        assertEquals(OperationResult.Error(AuthError.SESSION_EXPIRED), expired.deleteAccount("old-token"))

        val broken = remote(status = HttpStatusCode.InternalServerError) { """{"code":"P0001","details":null,"hint":null,"message":"consent_hmac_key is not set: the account is not deleted"}""" }
        assertEquals(OperationResult.Error(AuthError.UNKNOWN), broken.deleteAccount("proof-token"))
    }

    private companion object {
        const val USER_ID = "8d0fd2b3-9ca7-4c64-9a1e-6b1c9a7f1e11"
    }
}
