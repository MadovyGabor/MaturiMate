package com.madovygabor.maturimate.features.auth.data

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.auth.domain.AuthError
import io.github.jan.supabase.auth.auth
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthRepositoryImplPasswordTest : AuthRepositoryImplTestBase() {

    @Test
    fun passwordResetSendsCodeThenSetsTheNewPassword() = runBlocking {
        val repository = repository { request ->
            when (request.url.encodedPath) {
                "/auth/v1/recover" -> json("{}")
                "/auth/v1/verify" -> json(session())
                else -> json(user())
            }
        }

        assertTrue(repository.sendPasswordResetCode(EMAIL).isSuccess())
        assertTrue(repository.verifyResetCode(EMAIL, "123456").isSuccess())
        assertTrue(repository.setNewPassword("NewPassw0rd").isSuccess())

        assertEquals(
            listOf("POST /auth/v1/recover", "POST /auth/v1/verify", "PUT /auth/v1/user"),
            requests.map { it.first },
        )
        assertTrue("\"recovery\"" in requests[1].second)
        assertTrue("NewPassw0rd" in requests[2].second)
    }

    @Test
    fun thePasswordChangeSendsACodeThenSetsThePasswordWithIt() = runBlocking {
        val repository = repository { request ->
            when (request.url.encodedPath) {
                "/auth/v1/token" -> json(session())
                "/auth/v1/reauthenticate" -> json("{}")
                else -> json(user())
            }
        }
        repository.signIn(EMAIL, PASSWORD)
        requests.clear()

        assertTrue(repository.sendReauthenticationCode().isSuccess())
        assertTrue(repository.changePasswordWithCode("123456", "NewPassw0rd").isSuccess())

        assertEquals(listOf("GET /auth/v1/reauthenticate", "PUT /auth/v1/user"), requests.map { it.first })
        val body = Json.parseToJsonElement(requests[1].second).jsonObject
        assertEquals("NewPassw0rd", body["password"]?.jsonPrimitive?.content)
        assertEquals("123456", body["nonce"]?.jsonPrimitive?.content)
        // the old session stays: no second sign-in
        assertEquals("refresh", repository.refreshToken())
    }

    @Test
    fun aWrongOrExpiredReauthenticationCodeIsInvalidCode() = runBlocking {
        for (code in listOf("reauthentication_not_valid", "reauth_nonce_missing")) {
            val repository = repository { request ->
                if (request.method == HttpMethod.Put) authError(code) else json(session())
            }
            repository.signIn(EMAIL, PASSWORD)
            assertEquals(OperationResult.Error(AuthError.INVALID_CODE), repository.changePasswordWithCode("000000", "NewPassw0rd"), code)
        }
        val needsReauth = repository { request ->
            if (request.method == HttpMethod.Put) authError("reauthentication_needed") else json(session())
        }
        needsReauth.signIn(EMAIL, PASSWORD)
        assertEquals(OperationResult.Error(AuthError.REAUTHENTICATION_REQUIRED), needsReauth.changePasswordWithCode(null, "NewPassw0rd"))
    }

    @Test
    fun aFreshSessionOmitsNonceWhenCodeIsNull() = runBlocking {
        val repository = repository { request -> if (request.method == HttpMethod.Put) json(user()) else json(session()) }
        repository.signIn(EMAIL, PASSWORD)
        requests.clear()

        assertTrue(repository.changePasswordWithCode(null, "NewPassw0rd", currentPassword = PASSWORD).isSuccess())

        val body = Json.parseToJsonElement(requests.single().second).jsonObject
        assertEquals(null, body["nonce"])
        assertEquals(PASSWORD, body["current_password"]?.jsonPrimitive?.content)
        assertEquals("NewPassw0rd", body["password"]?.jsonPrimitive?.content)
    }

    @Test
    fun samePasswordIsReported() = runBlocking {
        val repository = repository { request ->
            if (request.method == HttpMethod.Put) authError("same_password", HttpStatusCode.UnprocessableEntity) else json(session())
        }

        repository.verifyResetCode(EMAIL, "123456")
        assertEquals(
            OperationResult.Error(AuthError.SAME_PASSWORD),
            repository.setNewPassword(PASSWORD),
        )
    }

    // ---- the server-checked old password (card 11): no second session on this phone, none left on the server ----
    /** Signed in with "access"/"refresh"; the password check's own sign-in answers with "check-access"/"check-refresh". */
    private fun signedInForCheck(
        check: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
        logout: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { respond("", HttpStatusCode.NoContent) },
    ): AuthRepositoryImpl {
        var signedIn = false
        val repository = repository { request ->
            when {
                request.url.encodedPath.endsWith("/logout") -> logout(request)
                !signedIn -> { signedIn = true; json(session()) }
                else -> check(request)
            }
        }
        runBlocking { repository.signIn(EMAIL, PASSWORD) }
        requests.clear()
        return repository
    }

    @Test
    fun theOldPasswordIsCheckedByTheServerAndItsSessionEndsAtOnce() = runBlocking {
        var logoutAuth: String? = null
        var logoutScope: String? = null
        var grantType: String? = null
        var apiKey: String? = null
        var authorization: String? = null
        var url = ""
        val repository = signedInForCheck(
            check = { request ->
                grantType = request.url.parameters["grant_type"]
                apiKey = request.headers["apikey"]
                authorization = request.headers[HttpHeaders.Authorization]
                url = request.url.toString()
                json(session(access = "check-access", refresh = "check-refresh"))
            },
            logout = { request ->
                logoutAuth = request.headers[HttpHeaders.Authorization]
                logoutScope = request.url.parameters["scope"]
                respond("", HttpStatusCode.NoContent)
            },
        )

        assertEquals(OperationResult.Success(null), repository.verifyPassword(PASSWORD))

        assertEquals(listOf("POST /auth/v1/token", "POST /auth/v1/logout"), requests.map { it.first })
        assertEquals("password", grantType)
        // the project's API key only: the phone's session is not sent with it
        assertEquals("test-key", apiKey)
        assertEquals(null, authorization)
        // the password goes in the JSON body, never in the address
        val body = Json.parseToJsonElement(requests[0].second).jsonObject
        assertTrue(body["password"]?.jsonPrimitive?.content == PASSWORD && body["email"]?.jsonPrimitive?.content == EMAIL)
        assertTrue(PASSWORD !in url)
        // the check's own session is ended at once, on this device's scope only
        assertEquals("Bearer check-access", logoutAuth)
        assertEquals("local", logoutScope)
        // the phone's session is the one it was: nothing was imported
        assertEquals("refresh", repository.refreshToken())
        assertEquals("access", plugin.currentSessionOrNull()?.accessToken)
    }

    // Card 12 (N-8): the account deletion goes out with the check's own fresh session (its token says "password, now"),
    // lent out after the server checked the password and before that session ends
    @Test
    fun theChecksSessionIsLentOutBeforeItEnds() = runBlocking {
        val repository = signedInForCheck(check = { json(session(access = "check-access", refresh = "check-refresh")) })
        val lent = mutableListOf<String>()

        val result = repository.verifyPassword(PASSWORD) { token ->
            lent += token
            requests += "LENT" to ""
        }

        assertEquals(OperationResult.Success(null), result)
        assertEquals(listOf("check-access"), lent)
        assertEquals(listOf("POST /auth/v1/token", "LENT", "POST /auth/v1/logout"), requests.map { it.first })
        // the phone's own session stays as it was
        assertEquals("access", plugin.currentSessionOrNull()?.accessToken)
    }

    @Test
    fun aWrongPasswordLendsNothing() = runBlocking {
        val repository = signedInForCheck(check = { authError("invalid_credentials") })
        var lent = false

        assertEquals(OperationResult.Error(AuthError.INVALID_CREDENTIALS), repository.verifyPassword("Wrong1234") { lent = true })
        assertTrue(!lent)
    }

    @Test
    fun theSessionOfThePhoneIsOneSnapshotOfUserAndToken() = runBlocking {
        val repository = signedInForCheck(check = { json(session()) })
        assertEquals(com.madovygabor.maturimate.features.auth.domain.AccountSession(USER_ID, "access"), repository.currentSession())
        assertEquals(null, repository { json(session()) }.currentSession())
    }

    @Test
    fun aWrongOldPasswordIsInvalidCredentialsAndChangesNothing() = runBlocking {
        val repository = signedInForCheck(check = { authError("invalid_credentials") })

        assertEquals(OperationResult.Error(AuthError.INVALID_CREDENTIALS), repository.verifyPassword("Wrong1234"))

        // no session was made, so none is ended
        assertEquals(listOf("POST /auth/v1/token"), requests.map { it.first })
        assertEquals("refresh", repository.refreshToken())
    }

    @Test
    fun tooManyPasswordChecksAreTheirOwnError() = runBlocking {
        val repository = signedInForCheck(check = { authError("over_request_rate_limit", HttpStatusCode.TooManyRequests) })

        assertEquals(OperationResult.Error(AuthError.TOO_MANY_REQUESTS), repository.verifyPassword(PASSWORD))
        // a 429 without GoTrue's error code counts too
        val bare = signedInForCheck(check = { respond("", HttpStatusCode.TooManyRequests) })
        assertEquals(OperationResult.Error(AuthError.TOO_MANY_REQUESTS), bare.verifyPassword(PASSWORD))
    }

    @Test
    fun aCheckWhoseSessionCannotBeEndedStillSucceedsAndHandsTheTokenBack() = runBlocking {
        val repository = signedInForCheck(
            check = { json(session(access = "check-access", refresh = "check-refresh")) },
            logout = { respond("", HttpStatusCode.InternalServerError) },
        )

        // the caller keeps the token and ends that session later
        assertEquals(OperationResult.Success("check-refresh"), repository.verifyPassword(PASSWORD))
        assertEquals("refresh", repository.refreshToken())
    }

    @Test
    fun thePasswordCheckWithoutASessionOrANetworkSaysSo() = runBlocking {
        val signedOut = repository { json(session()) }
        assertEquals(OperationResult.Error(AuthError.SESSION_EXPIRED), signedOut.verifyPassword(PASSWORD))
        assertTrue(requests.isEmpty())

        val offline = signedInForCheck(check = { throw IOException("offline") })
        assertEquals(OperationResult.Error(AuthError.NETWORK), offline.verifyPassword(PASSWORD))
    }

    // ---- S2-2: the current password goes with the change too (GoTrue's own check) ----
    @Test
    fun thePasswordChangeCarriesTheCurrentPassword() = runBlocking {
        val repository = repository { request -> if (request.method == HttpMethod.Put) json(user()) else json(session()) }
        repository.signIn(EMAIL, PASSWORD)
        requests.clear()

        assertTrue(repository.changePasswordWithCode("123456", "NewPassw0rd", currentPassword = PASSWORD).isSuccess())

        val (call, text) = requests.single()
        assertEquals("PUT /auth/v1/user", call)
        val body = Json.parseToJsonElement(text).jsonObject
        // GoTrue's field name; supabase-kt's own builder would send "currentPassword", which GoTrue ignores
        assertEquals(PASSWORD, body["current_password"]?.jsonPrimitive?.content)
        assertEquals("123456", body["nonce"]?.jsonPrimitive?.content)
        assertEquals("NewPassw0rd", body["password"]?.jsonPrimitive?.content)
    }

    @Test
    fun aWrongCurrentPasswordAtTheChangeIsInvalidCredentials() = runBlocking {
        for (code in listOf("current_password_mismatch", "current_password_required")) {
            val repository = repository { request -> if (request.method == HttpMethod.Put) authError(code) else json(session()) }
            repository.signIn(EMAIL, PASSWORD)
            assertEquals(OperationResult.Error(AuthError.INVALID_CREDENTIALS), repository.changePasswordWithCode("123456", "NewPassw0rd", "Wrong1234"), code)
        }
    }

    @Test
    fun anExpiredAccessTokenIsRenewedOnceForTheChange() = runBlocking {
        var puts = 0
        val repository = repository { request ->
            when {
                request.method == HttpMethod.Put -> if (puts++ == 0) json("""{"message":"JWT expired"}""", HttpStatusCode.Unauthorized) else json(user())
                else -> json(session())
            }
        }
        repository.signIn(EMAIL, PASSWORD)
        requests.clear()

        assertTrue(repository.changePasswordWithCode("123456", "NewPassw0rd", PASSWORD).isSuccess())
        assertEquals(listOf("PUT /auth/v1/user", "POST /auth/v1/token", "PUT /auth/v1/user"), requests.map { it.first })
    }
}
