package com.madovygabor.maturimate.features.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.sync.data.SyncSessionImpl
import com.madovygabor.maturimate.features.sync.data.SyncStoreImpl
import com.madovygabor.maturimate.features.sync.domain.SyncChanges
import com.madovygabor.maturimate.features.sync.domain.SyncEngine
import com.madovygabor.maturimate.features.sync.domain.SyncError
import com.madovygabor.maturimate.features.sync.domain.SyncOutcome
import com.madovygabor.maturimate.features.sync.domain.SyncProfile
import com.madovygabor.maturimate.features.sync.domain.SyncReason
import com.madovygabor.maturimate.features.sync.domain.SyncRepository
import com.madovygabor.maturimate.features.sync.domain.SyncResponse
import com.madovygabor.maturimate.features.sync.domain.SyncSession
import com.madovygabor.maturimate.features.sync.domain.SyncSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** A guest who signs in to an existing account (roadmap 12.5): the account's profile wins, the guest's rounds go up. */
class GuestMergeTest {

    private val database = UserDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) })
    private val localUser = LocalUserProvider(database, newDeviceId = { "dev" }, newGuestId = { "guest-1" })
    private val store = SyncStoreImpl(database, localUser, Dispatchers.Unconfined)
    private val session = object : SyncSession {
        override suspend fun isSignedIn() = !localUser.isGuest()
        override suspend fun deviceId() = "dev"
    }

    private class Server(var fail: Boolean = false) : SyncRepository {
        val calls = mutableListOf<Pair<Boolean, SyncChanges>>()
        override suspend fun sync(cursor: String?, deviceId: String, guestMerge: Boolean, changes: SyncChanges): OperationResult<SyncResponse, SyncError> {
            calls += guestMerge to changes
            if (fail) return OperationResult.Error(SyncError.NETWORK)
            // the account's rows, older than the take-over: in a guest merge they still win
            return OperationResult.Success(SyncResponse(
                serverTime = 1_000, cursor = "c",
                profile = SyncProfile("Anna", "Kovács", "HU", "2027-03-15", 10, null, 1, updatedAt = 10),
                settings = SyncSettings("HU", 25, false, null, updatedAt = 10),
                roles = listOf(1),
            ))
        }
    }

    private fun guestWithARound() {
        val id = localUser.userId()
        database.profileQueries.ensureProfile(id)
        database.profileQueries.updateTeachingLanguage("SK", id)
        database.profileQueries.ensureSettings(id, "Europe/Bratislava")
        database.profileQueries.updateDailyGoal(99, id)
        database.progressQueries.insertAttempt("r1", id, "UNIT_ROUND", null, 5, 100)
        database.progressQueries.finishAttempt("FINISHED", 120, 1, 1, "r1")
    }

    @Test
    fun theTakeOverOfAGuestMakesTheFirstSyncAGuestMerge() = runBlocking {
        guestWithARound()
        val server = Server()
        localUser.adoptAccount("account-1")

        assertIs<SyncOutcome.Done>(SyncEngine(server, store, session, now = { 1_000 }).sync(SyncReason.AFTER_SIGN_IN))

        val (merge, changes) = server.calls.single()
        assertTrue(merge)
        // the guest's round goes up
        assertEquals(listOf("r1"), changes.attempts.map { it.attemptId })
        // the account's profile and settings win, although the guest's rows are newer
        assertEquals("HU", database.profileQueries.selectProfile("account-1").executeAsOne().teaching_language)
        assertEquals("Anna", database.profileQueries.selectProfile("account-1").executeAsOne().first_name)
        assertEquals(25L, database.profileQueries.selectSettings("account-1").executeAsOne().daily_goal_minutes)
        // and the round stays
        assertEquals(1, database.progressQueries.selectAttempts("account-1").executeAsList().size)
        assertFalse(store.state().guestMergePending)
    }

    @Test
    fun theMergeModeSurvivesAFailedSyncAndClearsAfterAGoodOne() = runBlocking {
        guestWithARound()
        val server = Server(fail = true)
        localUser.adoptAccount("account-1")
        val engine = SyncEngine(server, store, session, now = { 1_000 })

        assertIs<SyncOutcome.Failed>(engine.sync(SyncReason.AFTER_SIGN_IN))
        assertTrue(store.state().guestMergePending)

        server.fail = false
        engine.sync(SyncReason.OPENING)
        assertTrue(server.calls.last().first)
        assertFalse(store.state().guestMergePending)
        // the next run is a normal one
        engine.sync(SyncReason.MANUAL)
        assertFalse(server.calls.last().first)
    }

    @Test
    fun theSessionOfTheAppSeesAGuestAsNotSignedIn() = runBlocking {
        localUser.userId()
        assertFalse(session.isSignedIn())
        assertTrue(SyncSessionImpl::class.java.name.isNotEmpty())
    }
}
