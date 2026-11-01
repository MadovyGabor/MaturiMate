package com.madovygabor.maturimate.features.auth.data

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.domain.AuthState
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthRepositoryImplTest {

    /** Requests the repository sent, as "METHOD /path" and the JSON body. */
    private val requests = mutableListOf<Pair<String, String>>()

    private fun repository(
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
        return AuthRepositoryImpl(client.auth)
    }

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun MockRequestHandleScope.authError(code: String, status: HttpStatusCode = HttpStatusCode.BadRequest) =
        json("""{"code":${status.value},"error_code":"$code","msg":"$code"}""", status)

    private fun user(identities: String = IDENTITY) = """
        {"id":"$USER_ID","aud":"authenticated","role":"authenticated","email":"$EMAIL",
         "app_metadata":{"provider":"email"},"user_metadata":{},"identities":[$identities],
         "created_at":"2026-10-31T10:00:00Z","updated_at":"2026-10-31T10:00:00Z"}
    """.trimIndent()

    private fun session() = """
        {"access_token":"access","token_type":"bearer","expires_in":3600,"expires_at":4102444800,
         "refresh_token":"refresh","user":${user()}}
    """.trimIndent()

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
        val repository = repository { request ->
            if (request.url.encodedPath.endsWith("/logout")) respond("", HttpStatusCode.NoContent) else json(session())
        }
        repository.signIn(EMAIL, PASSWORD)

        assertTrue(repository.signOut().isSuccess())

        assertEquals(AuthState.SignedOut, withTimeout(5_000) { repository.state.first { it == AuthState.SignedOut } })
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
        assertTrue(repository.resetPassword(EMAIL, "123456", "NewPassw0rd").isSuccess())

        assertEquals(
            listOf("POST /auth/v1/recover", "POST /auth/v1/verify", "PUT /auth/v1/user"),
            requests.map { it.first },
        )
        assertTrue("\"recovery\"" in requests[1].second)
        assertTrue("NewPassw0rd" in requests[2].second)
    }

    @Test
    fun samePasswordIsReported() = runBlocking {
        val repository = repository { request ->
            if (request.method == HttpMethod.Put) authError("same_password", HttpStatusCode.UnprocessableEntity) else json(session())
        }

        assertEquals(
            OperationResult.Error(AuthError.SAME_PASSWORD),
            repository.resetPassword(EMAIL, "123456", PASSWORD),
        )
    }

    private companion object {
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
