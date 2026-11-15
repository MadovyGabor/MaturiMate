package com.madovygabor.maturimate.features.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.sync.data.SyncSessionImpl
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Who the sync runs for: only the account this phone belongs to, and only while that account is signed in. */
class SyncSessionTest {

    private val database = UserDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) })
    private val localUser = LocalUserProvider(database, newDeviceId = { "dev" }, newGuestId = { "guest-1" })

    /** An offline Supabase Auth with a session of [userId] (or none). */
    private fun auth(userId: String?): Auth = runBlocking {
        val client = createSupabaseClient("https://test.supabase.co", "test-key") {
            httpEngine = MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }
            install(Auth) {
                sessionManager = MemorySessionManager()
                codeVerifierCache = MemoryCodeVerifierCache()
                autoLoadFromStorage = false
                alwaysAutoRefresh = false
                enableLifecycleCallbacks = false
            }
        }
        client.auth.awaitInitialization()
        if (userId != null) {
            client.auth.importSession(
                UserSession(accessToken = "access", refreshToken = "refresh", expiresIn = 3600, tokenType = "bearer", user = UserInfo(aud = "authenticated", id = userId))
            )
        }
        client.auth
    }

    @Test
    fun thePhonesOwnAccountIsSignedIn() = runBlocking {
        localUser.adoptAccount("account-x")
        assertTrue(SyncSessionImpl(auth("account-x"), localUser, Dispatchers.Unconfined).isSignedIn())
    }

    @Test
    fun aGuestPhoneIsNotSignedInEvenWithASession() = runBlocking {
        localUser.userId()
        assertFalse(SyncSessionImpl(auth("account-y"), localUser, Dispatchers.Unconfined).isSignedIn())
    }

    // N2: the phone belongs to X (its sync failed offline), then a sign-in to Y that is not set up (no adopt): a sync
    // would push X's rows into Y and pull Y's into X.
    @Test
    fun aSessionOfAnotherAccountIsNotThePhonesAndDoesNotSync() = runBlocking {
        localUser.adoptAccount("account-x")
        assertFalse(SyncSessionImpl(auth("account-y"), localUser, Dispatchers.Unconfined).isSignedIn())
    }

    @Test
    fun noSessionIsNotSignedIn() = runBlocking {
        localUser.adoptAccount("account-x")
        assertFalse(SyncSessionImpl(auth(null), localUser, Dispatchers.Unconfined).isSignedIn())
    }
}
