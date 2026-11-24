package com.madovygabor.maturimate.features.auth.data

import com.madovygabor.maturimate.testutil.awaitFirst
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.domain.AuthState
import io.github.jan.supabase.auth.auth
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

class AuthRepositoryImplSignUpTest : AuthRepositoryImplTestBase() {

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
        assertEquals(AuthState.SignedIn(USER_ID, EMAIL), repository.state.awaitFirst { it is AuthState.SignedIn })
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
        assertEquals(AuthState.SignedIn(USER_ID, EMAIL), repository.state.awaitFirst { it is AuthState.SignedIn })
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
}
