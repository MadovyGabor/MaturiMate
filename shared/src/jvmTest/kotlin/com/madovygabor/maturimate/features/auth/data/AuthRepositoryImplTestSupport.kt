package com.madovygabor.maturimate.features.auth.data

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.auth.domain.AuthError
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
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf

abstract class AuthRepositoryImplTestBase {

    /** Requests the repository sent, as "METHOD /path" and the JSON body. */
    internal val requests = mutableListOf<Pair<String, String>>()

    internal fun repository(
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
    internal lateinit var plugin: Auth

    internal fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    internal fun MockRequestHandleScope.authError(code: String, status: HttpStatusCode = HttpStatusCode.BadRequest) =
        json("""{"code":${status.value},"error_code":"$code","msg":"$code"}""", status)

    internal fun user(identities: String = IDENTITY) = """
        {"id":"$USER_ID","aud":"authenticated","role":"authenticated","email":"$EMAIL",
         "app_metadata":{"provider":"email"},"user_metadata":{},"identities":[$identities],
         "created_at":"2026-10-31T10:00:00Z","updated_at":"2026-10-31T10:00:00Z"}
    """.trimIndent()

    internal fun session(access: String = "access", refresh: String = "refresh") = """
        {"access_token":"$access","token_type":"bearer","expires_in":3600,"expires_at":4102444800,
         "refresh_token":"$refresh","user":${user()}}
    """.trimIndent()

    internal fun HttpRequestData.text() = (body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()

    internal fun <T> OperationResult<T, AuthError>.isSuccess() = this is OperationResult.Success

    internal companion object {
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
