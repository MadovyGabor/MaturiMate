package com.madovygabor.maturimate.features.auth.data

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.domain.AuthState
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthRepositoryImplTest {

    /** Requests the repository sent, as "METHOD /path" and the JSON body. */
    private val requests = mutableListOf<Pair<String, String>>()

    private fun repository(
        appLanguage: String? = null,
        respond: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): AuthRepositoryImpl {
        val client = createSupabaseClient("https://test.supabase.co", "test-key") {
            httpEngine = MockEngine { request ->
                val body = (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()
                requests += "${request.method.value} ${request.url.encodedPath}" to body
                respond(request)
            }
            install(Auth) {
                sessionManager = MemorySessionManager()
                codeVerifierCache = MemoryCodeVerifierCache()
                autoLoadFromStorage = false
                alwaysAutoRefresh = false
                enableLifecycleCallbacks = false
            }
        }
        plugin = client.auth
        return AuthRepositoryImpl(client.auth, appLanguage = { appLanguage })
    }

    /** The Auth plugin of the last [repository]: a test can put a session there as another sign-in (Google) would. */
    private lateinit var plugin: Auth

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun MockRequestHandleScope.authError(code: String, status: HttpStatusCode = HttpStatusCode.BadRequest) =
        json("""{"code":${status.value},"error_code":"$code","msg":"$code"}""", status)

    private fun user(identities: String = IDENTITY) = """
        {"id":"$USER_ID","aud":"authenticated","role":"authenticated","email":"$EMAIL",
         "app_metadata":{"provider":"email"},"user_metadata":{},"identities":[$identities],
         "created_at":"2026-10-31T10:00:00Z","updated_at":"2026-10-31T10:00:00Z"}
    """.trimIndent()

    private fun session(access: String = "access", refresh: String = "refresh") = """
        {"access_token":"$access","token_type":"bearer","expires_in":3600,"expires_at":4102444800,
         "refresh_token":"$refresh","user":${user()}}
    """.trimIndent()

    private fun HttpRequestData.text() = (body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()

    private fun <T> OperationResult<T, AuthError>.isSuccess() = this is OperationResult.Success

    @Test
    fun signUpSendsEmailAndPassword() = runBlocking {
        val repository = repository { json(user()) }

        assertTrue(repository.signUp(EMAIL, PASSWORD).isSuccess())

        val (call, body) = requests.single()
        assertEquals("POST /auth/v1/signup", call)
        assertTrue(EMAIL in body && PASSWORD in body)
    }

    @Test
    fun signUpSendsTheAppLanguageForTheEmail() = runBlocking {
        val repository = repository(appLanguage = "hu") { json(user()) }

        assertTrue(repository.signUp(EMAIL, PASSWORD).isSuccess())

        // user metadata: the e-mail template reads {{ .Data.lang }}
        val body = Json.parseToJsonElement(requests.single().second).jsonObject
        assertEquals("hu", body["data"]?.jsonObject?.get("lang")?.jsonPrimitive?.content, body.toString())
    }

    @Test
    fun signUpWithAnExistingAddressIsEmailTaken() = runBlocking {
        // Supabase answers with a user without identities instead of an error
        val repository = repository { json(user(identities = "")) }

        assertEquals(OperationResult.Error(AuthError.EMAIL_TAKEN), repository.signUp(EMAIL, PASSWORD))
    }

    @Test
    fun weakPasswordIsReported() = runBlocking {
        val repository = repository {
            json(
                """{"code":422,"error_code":"weak_password","msg":"weak","weak_password":{"reasons":["characters"]}}""",
                HttpStatusCode.UnprocessableEntity,
            )
        }

        assertEquals(OperationResult.Error(AuthError.WEAK_PASSWORD), repository.signUp(EMAIL, "short"))
    }

    @Test
    fun confirmSignUpVerifiesTheCodeAndSignsIn() = runBlocking {
        val repository = repository { json(session()) }

        assertTrue(repository.confirmSignUp(EMAIL, "123456").isSuccess())

        val (call, body) = requests.single()
        assertEquals("POST /auth/v1/verify", call)
        assertTrue("\"signup\"" in body && "123456" in body)
        assertEquals(AuthState.SignedIn(USER_ID, EMAIL), withTimeout(5_000) { repository.state.first { it is AuthState.SignedIn } })
    }

    @Test
    fun wrongCodeIsInvalidCode() = runBlocking {
        val repository = repository { authError("otp_expired", HttpStatusCode.Forbidden) }

        assertEquals(OperationResult.Error(AuthError.INVALID_CODE), repository.confirmSignUp(EMAIL, "000000"))
    }

    @Test
    fun signInSignsIn() = runBlocking {
        val repository = repository { json(session()) }

        assertTrue(repository.signIn(EMAIL, PASSWORD).isSuccess())

        assertEquals("POST /auth/v1/token", requests.single().first)
        assertEquals(AuthState.SignedIn(USER_ID, EMAIL), withTimeout(5_000) { repository.state.first { it is AuthState.SignedIn } })
    }

    @Test
    fun signInErrorsAreMapped() = runBlocking {
        val cases = mapOf(
            "invalid_credentials" to AuthError.INVALID_CREDENTIALS,
            "email_not_confirmed" to AuthError.EMAIL_NOT_CONFIRMED,
            "over_request_rate_limit" to AuthError.TOO_MANY_REQUESTS,
            "unexpected_failure" to AuthError.UNKNOWN,
        )
        for ((code, expected) in cases) {
            val repository = repository { authError(code) }
            assertEquals(OperationResult.Error(expected), repository.signIn(EMAIL, PASSWORD), code)
        }
    }

    @Test
    fun noConnectionIsNetworkError() = runBlocking {
        val repository = repository { throw IOException("offline") }

        assertEquals(OperationResult.Error(AuthError.NETWORK), repository.signIn(EMAIL, PASSWORD))
    }

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

        assertEquals(AuthState.SignedOut, withTimeout(5_000) { repository.state.first { it == AuthState.SignedOut } })
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

    private companion object {
        const val OTHER_ID = "0b0b0b0b-9ca7-4c64-9a1e-6b1c9a7f1e22"
        const val USER_ID = "8d0fd2b3-9ca7-4c64-9a1e-6b1c9a7f1e11"
        const val EMAIL = "anna@example.com"
        const val PASSWORD = "Passw0rdA"
        val IDENTITY = """
            {"id":"$USER_ID","identity_id":"4f1c3e2a-1111-4c64-9a1e-6b1c9a7f1e11","user_id":"$USER_ID",
             "identity_data":{"email":"$EMAIL","sub":"$USER_ID"},"provider":"email",
             "last_sign_in_at":"2026-10-31T10:00:00Z","created_at":"2026-10-31T10:00:00Z","updated_at":"2026-10-31T10:00:00Z"}
        """.trimIndent()
    }
}
