package com.madovygabor.maturimate.features.auth.data

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.auth.domain.AuthError
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthRepositoryImplProofTest : AuthRepositoryImplTestBase() {

    // ---- a fresh sign-in as the proof of an account without a password (card 11) ----
    private fun otherUsersSession(access: String) = UserSession(
        accessToken = access, refreshToken = "$access-refresh", expiresIn = 3600, tokenType = "bearer",
        user = UserInfo(aud = "authenticated", id = OTHER_ID),
    )

    private fun sameUsersSession(access: String) = UserSession(
        accessToken = access, refreshToken = "$access-refresh", expiresIn = 3600, tokenType = "bearer",
        user = UserInfo(aud = "authenticated", id = USER_ID),
    )

    /**
     * Signed in ("access"/"refresh"); the mark renews to "marked-…"; a later renewal of "marked-refresh" (to end it, or to
     * put it back) gives "back-…". [logout] answers every logout, [renewBack] the renewal of the remembered session.
     */
    private fun signedInForProof(
        logouts: MutableList<String?>,
        logout: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { respond("", HttpStatusCode.NoContent) },
        renewBack: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { json(session(access = "back-access", refresh = "back-refresh")) },
    ): AuthRepositoryImpl {
        val repository = repository { request ->
            when {
                request.url.encodedPath.endsWith("/logout") -> { logouts += request.headers[HttpHeaders.Authorization]; logout(request) }
                "\"marked-refresh\"" in request.text() -> renewBack(request)
                "\"refresh\"" in request.text() -> json(session(access = "marked-access", refresh = "marked-refresh"))
                else -> json(session())
            }
        }
        runBlocking {
            repository.signIn(EMAIL, PASSWORD)
            assertEquals(OperationResult.Success(Unit), repository.markSessionBeforeProof())
        }
        return repository
    }

    @Test
    fun aNewSessionOfTheSameAccountIsTheProofAndTheOldOneEnds() = runBlocking {
        val logouts = mutableListOf<String?>()
        val repository = signedInForProof(logouts)
        plugin.importSession(sameUsersSession("google-access"))

        val check = repository.checkFreshSignIn()

        assertEquals(OperationResult.Success(Unit), check.result)
        assertTrue(check.unended.isEmpty())
        assertEquals("google-access", plugin.currentSessionOrNull()?.accessToken)
        // the remembered (renewed) session ended with its own token
        assertEquals(listOf<String?>("Bearer back-access"), logouts)
    }

    @Test
    fun anotherAccountIsRejectedSignedOutAndThePhonesSessionComesBack() = runBlocking {
        val logouts = mutableListOf<String?>()
        val repository = signedInForProof(logouts)
        plugin.importSession(otherUsersSession("other-access"))

        val check = repository.checkFreshSignIn()

        assertEquals(OperationResult.Error(AuthError.WRONG_ACCOUNT), check.result)
        // the other account's session ended (this device's scope), the phone's own is back and renewed
        assertEquals(listOf<String?>("Bearer other-access"), logouts)
        assertEquals(USER_ID, repository.currentUser()?.id)
        assertEquals("back-refresh", repository.refreshToken())
        assertTrue(check.unended.isEmpty())
    }

    // N-2: a session the check could not end was dropped, and lived on on the server.
    @Test
    fun sessionsTheCheckCouldNotEndAreHandedBack() = runBlocking {
        val failing: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { respond("", HttpStatusCode.InternalServerError) }
        val wrong = signedInForProof(mutableListOf(), logout = failing)
        plugin.importSession(otherUsersSession("other-access"))
        assertEquals(listOf("other-access-refresh"), wrong.checkFreshSignIn().unended)

        val same = signedInForProof(mutableListOf(), logout = failing)
        plugin.importSession(sameUsersSession("google-access"))
        val check = same.checkFreshSignIn()
        assertEquals(OperationResult.Success(Unit), check.result)
        assertEquals(listOf("marked-refresh"), check.unended)
    }

    // N-3: a remembered session the server no longer knows was imported anyway and failed later, somewhere else.
    @Test
    fun aRememberedSessionThatIsGoneEndsCleanlyAsAnExpiredSession() = runBlocking {
        val logouts = mutableListOf<String?>()
        val repository = signedInForProof(logouts, renewBack = { authError("refresh_token_already_used") })
        plugin.importSession(otherUsersSession("other-access"))

        val check = repository.checkFreshSignIn()

        assertEquals(OperationResult.Error(AuthError.SESSION_EXPIRED), check.result)
        // signed out cleanly, the other account's session still ended
        assertEquals(null, repository.currentUser())
        assertEquals(listOf<String?>("Bearer other-access"), logouts)
    }

    @Test
    fun theMarkRenewsTheSessionAndSaysWhenItCannot() = runBlocking {
        val signedOut = repository { json(session()) }
        assertEquals(OperationResult.Error(AuthError.SESSION_EXPIRED), signedOut.markSessionBeforeProof())

        val logouts = mutableListOf<String?>()
        signedInForProof(logouts)
        // renewed at the mark: the remembered copy is the live session
        assertEquals("marked-access", plugin.currentSessionOrNull()?.accessToken)

        var offline = false
        val repository = repository { request -> if (offline) throw IOException("offline") else json(session()) }
        repository.signIn(EMAIL, PASSWORD)
        offline = true
        assertEquals(OperationResult.Error(AuthError.NETWORK), repository.markSessionBeforeProof())
        // nothing remembered: a check afterwards has nothing to compare with
        assertEquals(OperationResult.Error(AuthError.SESSION_EXPIRED), repository.checkFreshSignIn().result)
    }

    // S2-1: a Google flow that failed or was closed after another account's session came in left that session open.
    @Test
    fun aCancelledProofPutsThePhonesSessionBackFromAnotherAccount() = runBlocking {
        val logouts = mutableListOf<String?>()
        val repository = signedInForProof(logouts)
        plugin.importSession(otherUsersSession("other-access"))

        repository.cancelFreshSignIn()

        assertEquals(USER_ID, repository.currentUser()?.id)
        assertEquals(listOf<String?>("Bearer other-access"), logouts)
        // forgotten: a later check has nothing to compare with
        assertEquals(OperationResult.Error(AuthError.SESSION_EXPIRED), repository.checkFreshSignIn().result)
    }

    @Test
    fun aCancelledProofWithTheSameSessionChangesNothing() = runBlocking {
        val logouts = mutableListOf<String?>()
        val repository = signedInForProof(logouts)
        requests.clear()

        assertEquals(OperationResult.Success(Unit), repository.cancelFreshSignIn().result)

        assertTrue(requests.isEmpty())
        assertEquals("marked-access", plugin.currentSessionOrNull()?.accessToken)
    }

    @Test
    fun noNewSessionIsNoProof() = runBlocking {
        val repository = signedInForProof(mutableListOf())

        // marked, but the sign-in never happened: the same session is no proof
        assertEquals(OperationResult.Error(AuthError.PROOF_NEEDED), repository.checkFreshSignIn().result)
        // nothing marked any more
        assertEquals(OperationResult.Error(AuthError.SESSION_EXPIRED), repository.checkFreshSignIn().result)
        assertEquals("marked-refresh", repository.refreshToken())
    }
}
