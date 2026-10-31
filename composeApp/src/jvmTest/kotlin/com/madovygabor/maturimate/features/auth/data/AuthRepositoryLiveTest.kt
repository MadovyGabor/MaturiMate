package com.madovygabor.maturimate.features.auth.data

import com.madovygabor.maturimate.core.config.SupabaseConfig
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.domain.AuthState
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Signs in to the real Supabase project with an existing, confirmed test account.
 * Skipped unless both variables are set; it creates nothing on the server:
 *
 *   MATURIMATE_TEST_EMAIL=... MATURIMATE_TEST_PASSWORD=... ./gradlew :composeApp:jvmTest --tests "*AuthRepositoryLiveTest"
 */
class AuthRepositoryLiveTest {

    private val email: String? = System.getenv("MATURIMATE_TEST_EMAIL")
    private val password: String? = System.getenv("MATURIMATE_TEST_PASSWORD")

    private fun repository(): AuthRepositoryImpl {
        val client = createSupabaseClient(SupabaseConfig.URL, SupabaseConfig.PUBLISHABLE_KEY) {
            install(Auth) {
                // nothing is written to the machine's session storage
                sessionManager = MemorySessionManager()
                codeVerifierCache = MemoryCodeVerifierCache()
                autoLoadFromStorage = false
                enableLifecycleCallbacks = false
            }
        }
        return AuthRepositoryImpl(client.auth)
    }

    @Test
    fun signInWithTheTestAccountThenSignOut() = runBlocking {
        assumeTrue("MATURIMATE_TEST_EMAIL and MATURIMATE_TEST_PASSWORD are not set", email != null && password != null)
        val repository = repository()

        assertEquals(OperationResult.Success(Unit), repository.signIn(email!!, password!!))
        val signedIn = withTimeout(10_000) { repository.state.first { it is AuthState.SignedIn } }
        assertEquals(email, (signedIn as AuthState.SignedIn).email)

        assertEquals(OperationResult.Success(Unit), repository.signOut())
    }

    @Test
    fun wrongPasswordIsInvalidCredentials() = runBlocking {
        assumeTrue("MATURIMATE_TEST_EMAIL is not set", email != null)

        assertEquals(
            OperationResult.Error(AuthError.INVALID_CREDENTIALS),
            repository().signIn(email!!, "Wrong-password-1"),
        )
    }
}
