package com.madovygabor.maturimate.features.auth.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.testutil.testDbDispatcher
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The pending session against a real HTTP answer of the server (N8): a 5xx is a failure to try again, not "the token is dead". */
class PendingRevocationServerFailureTest : AuthRepositoryImplTestBase() {

    private val database = UserDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) })
    private val tokens = AccountSettingsImplTestBase.MemoryTokens()

    private val fiveHundreds = listOf(HttpStatusCode.InternalServerError, HttpStatusCode.BadGateway, HttpStatusCode.ServiceUnavailable)

    @Test
    fun aFiveHundredOfTheLogoutKeepsThePendingTokenUntilTheServerAnswers() = runBlocking {
        var logoutStatus = HttpStatusCode.NoContent
        val repository = repository { request ->
            if (request.url.encodedPath.endsWith("/logout")) respond("", logoutStatus) else json(session())
        }
        val revocation = PendingRevocation(database, repository, testDbDispatcher(), tokens)
        revocation.save("old-refresh")

        for (status in fiveHundreds) {
            logoutStatus = status
            revocation.revoke()
            assertTrue(revocation.hasPending(), "the token was dropped after a ${status.value}")
        }

        logoutStatus = HttpStatusCode.NoContent
        revocation.revoke()
        assertFalse(revocation.hasPending())
    }

    @Test
    fun aFiveHundredOfTheRefreshKeepsThePendingTokenUntilTheServerAnswers() = runBlocking {
        var tokenStatus = HttpStatusCode.OK
        val repository = repository { request ->
            when {
                request.url.encodedPath.endsWith("/logout") -> respond("", HttpStatusCode.NoContent)
                tokenStatus.value >= 500 -> json("""{"message":"server failure"}""", tokenStatus)
                else -> json(session())
            }
        }
        val revocation = PendingRevocation(database, repository, testDbDispatcher(), tokens)
        revocation.save("old-refresh")

        for (status in fiveHundreds) {
            tokenStatus = status
            revocation.revoke()
            assertTrue(revocation.hasPending(), "the token was dropped after a ${status.value}")
        }
        assertEquals(listOf("old-refresh"), tokens.tokens)

        tokenStatus = HttpStatusCode.OK
        revocation.revoke()
        assertFalse(revocation.hasPending())
    }
}
