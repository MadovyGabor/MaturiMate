package com.madovygabor.maturimate.features.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.sync.data.SupabaseSyncRepository
import com.madovygabor.maturimate.features.sync.domain.SyncChanges
import com.madovygabor.maturimate.features.sync.domain.SyncError
import io.github.jan.supabase.auth.Auth
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Card 12 (S1-1 server part): every sync call names the account this phone belongs to, and the server's refusal is read. */
class SupabaseSyncRepositoryTest {

    private val database = UserDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) })
    private val localUser = LocalUserProvider(database, newDeviceId = { "dev" }, newGuestId = { "guest-1" })

    /** The JSON bodies of the calls. */
    private val bodies = mutableListOf<String>()

    private fun repository(status: HttpStatusCode = HttpStatusCode.OK, answer: String = OK): SupabaseSyncRepository = runBlocking {
        val client = createSupabaseClient("https://test.supabase.co", "test-key") {
            httpEngine = MockEngine { request ->
                bodies += (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()
                respond(answer, status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
            install(Auth) {
                sessionManager = MemorySessionManager()
                codeVerifierCache = MemoryCodeVerifierCache()
                autoLoadFromStorage = false
                alwaysAutoRefresh = false
                enableLifecycleCallbacks = false
            }
            install(Postgrest)
        }
        client.auth.awaitInitialization()
        client.auth.importSession(
            UserSession(accessToken = "access", refreshToken = "refresh", expiresIn = 3600, tokenType = "bearer", user = UserInfo(aud = "authenticated", id = "account-x"))
        )
        SupabaseSyncRepository(client.auth, client.postgrest, localUser, com.madovygabor.maturimate.testutil.testDbDispatcher())
    }

    @Test
    fun everyCallNamesThePhonesAccount() = runBlocking {
        localUser.adoptAccount("account-x")

        assertIs<OperationResult.Success<*, *>>(repository().sync(null, "dev", false, SyncChanges()))

        val sent = Json.parseToJsonElement(bodies.single()).jsonObject
        assertEquals("account-x", sent["p_expected_user"]?.jsonPrimitive?.content)
    }

    // Card 12 review F4: a guest's id ("guest-" and a uuid) is no account id; such a call never goes out
    @Test
    fun aGuestPhoneNeverCalls() = runBlocking {
        localUser.userId()

        assertEquals(OperationResult.Error(SyncError.NOT_SIGNED_IN), repository().sync(null, "dev", false, SyncChanges()))
        assertTrue(bodies.isEmpty())
    }

    @Test
    fun theServersRefusalForAnotherAccountIsWrongAccount() = runBlocking {
        localUser.adoptAccount("account-x")
        val refused = repository(HttpStatusCode.Conflict, """{"code":"PT409","details":null,"hint":null,"message":"user_mismatch"}""")

        assertEquals(OperationResult.Error(SyncError.WRONG_ACCOUNT), refused.sync(null, "dev", false, SyncChanges()))
    }

    private companion object {
        const val OK = """{"server_time":1000,"cursor":"c","more":false}"""
    }
}
