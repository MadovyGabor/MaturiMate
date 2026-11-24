package com.madovygabor.maturimate.features.auth.data

import com.madovygabor.maturimate.testutil.awaitFirst
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.auth.domain.AuthState
import io.github.jan.supabase.auth.auth
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthRepositoryImplSignOutTest : AuthRepositoryImplTestBase() {

    @Test
    fun signOutSignsOut() = runBlocking {
        var scope: String? = null
        val repository = repository { request ->
            if (request.url.encodedPath.endsWith("/logout")) {
                scope = request.url.parameters["scope"]
                respond("", HttpStatusCode.NoContent)
            } else json(session())
        }
        repository.signIn(EMAIL, PASSWORD)

        assertTrue(repository.signOut().isSuccess())
        // this device only: the account stays signed in on the user's other devices (decision 16)
        assertEquals("local", scope)

        assertEquals(AuthState.SignedOut, repository.state.awaitFirst { it == AuthState.SignedOut })
    }

    @Test
    fun anOldSessionIsEndedWithoutBecomingThePhonesSession() = runBlocking {
        var logoutAuth: String? = null
        val repository = repository { request ->
            if (request.url.encodedPath.endsWith("/logout")) {
                logoutAuth = request.headers[HttpHeaders.Authorization]
                respond("", HttpStatusCode.NoContent)
            } else json(session())
        }

        assertTrue(repository.revokeSession("old-refresh").isSuccess())

        assertEquals(listOf("POST /auth/v1/token", "POST /auth/v1/logout"), requests.map { it.first })
        assertTrue("old-refresh" in requests[0].second)
        assertEquals("Bearer access", logoutAuth)
        // the phone stays signed out: no session was imported, so the app never saw a sign-in
        assertEquals(null, repository.currentUser())
    }

    // The revocation used to do nothing while a new session was open, and the pending token was then dropped unrevoked.
    @Test
    fun aPendingSessionIsEndedWithItsOwnTokenAfterANewSignIn() = runBlocking {
        var logoutAuth: String? = null
        var logoutScope: String? = null
        val repository = repository { request ->
            when {
                request.url.encodedPath.endsWith("/logout") -> {
                    logoutAuth = request.headers[HttpHeaders.Authorization]
                    logoutScope = request.url.parameters["scope"]
                    respond("", HttpStatusCode.NoContent)
                }
                "old-refresh" in request.text() -> json(session(access = "old-access", refresh = "old-rotated"))
                else -> json(session())
            }
        }
        repository.signIn(EMAIL, PASSWORD)
        requests.clear()

        assertTrue(repository.revokeSession("old-refresh").isSuccess())

        assertEquals(listOf("POST /auth/v1/token", "POST /auth/v1/logout"), requests.map { it.first })
        assertEquals("Bearer old-access", logoutAuth)
        assertEquals("local", logoutScope)
        // the new sign-in is untouched
        assertEquals("refresh", repository.refreshToken())
    }

    @Test
    fun aServerFailureOfTheRevocationIsAnErrorToTryAgain() = runBlocking {
        val repository = repository { request ->
            if (request.url.encodedPath.endsWith("/logout")) respond("", HttpStatusCode.InternalServerError) else json(session())
        }

        assertTrue(repository.revokeSession("old-refresh") is OperationResult.Error)
    }

    @Test
    fun aRefreshTokenTheServerNoLongerKnowsCountsAsEnded() = runBlocking {
        val repository = repository { authError("refresh_token_not_found") }

        assertTrue(repository.revokeSession("old-refresh").isSuccess())
        // nothing to log out
        assertEquals(listOf("POST /auth/v1/token"), requests.map { it.first })
    }

    // Every 4xx of the refresh used to drop the token, also a 401 for a wrong API key, which says nothing about the token.
    @Test
    fun aRefreshRefusedForAnotherReasonKeepsTheToken() = runBlocking {
        val repository = repository { json("""{"message":"Invalid API key"}""", HttpStatusCode.Unauthorized) }

        assertTrue(repository.revokeSession("old-refresh") is OperationResult.Error)
        assertEquals(listOf("POST /auth/v1/token"), requests.map { it.first })
    }
}
