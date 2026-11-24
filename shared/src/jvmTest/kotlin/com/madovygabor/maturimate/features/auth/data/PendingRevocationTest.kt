package com.madovygabor.maturimate.features.auth.data

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class PendingRevocationTest : AccountSettingsImplTestBase() {

    @Test
    fun anOfflineSignOutKeepsTheOldSessionToEndItOnTheServerLater() = runBlocking {
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        val auth = Auth(offline = true)
        val revocation = PendingRevocation(database, auth, dbDispatcher, MemoryTokens())

        assertEquals(OperationResult.Success(Unit), settings(auth, null, revocation).signOut())

        // the phone is signed out and empty, the server not told yet
        assertTrue(auth.localSessionCleared)
        assertNull(profiles.profile.first())
        assertTrue(revocation.hasPending())

        // still offline at the next opening: kept
        revocation.revoke()
        assertTrue(revocation.hasPending())
        // the network is back
        auth.offline = false
        revocation.revoke()
        assertEquals("refresh-1", auth.revoked)
        assertTrue(!revocation.hasPending())
    }

    // A 5xx used to count as "the token is dead", so the pending session was dropped without being ended.
    @Test
    fun aServerFailureKeepsThePendingSessionForALaterTry() = runBlocking {
        val auth = Auth(offline = true)
        val revocation = PendingRevocation(database, auth, dbDispatcher, MemoryTokens())
        assertEquals(OperationResult.Success(Unit), settings(auth, null, revocation).signOut())

        auth.offline = false
        auth.serverFails = true
        revocation.revoke()
        assertTrue(revocation.hasPending())

        auth.serverFails = false
        revocation.revoke()
        assertEquals("refresh-1", auth.revoked)
        assertTrue(!revocation.hasPending())
    }

    // The token used to wait in the user database, which Android backs up and moves to a new phone.
    @Test
    fun thePendingTokenWaitsOutsideTheUserDatabase() = runBlocking {
        val auth = Auth(offline = true)
        val tokens = MemoryTokens()
        val revocation = PendingRevocation(database, auth, dbDispatcher, tokens)

        assertEquals(OperationResult.Success(Unit), settings(auth, null, revocation).signOut())

        assertEquals(listOf("refresh-1"), tokens.tokens)
        assertNull(database.syncQueries.selectPendingRevocation().executeAsOneOrNull())
    }

    @Test
    fun aTokenAnOlderVersionLeftInTheDatabaseIsMovedOnceAndStillEnded() = runBlocking {
        database.syncQueries.savePendingRevocation("refresh-old")
        val auth = Auth(offline = true)
        val tokens = MemoryTokens()
        val revocation = PendingRevocation(database, auth, dbDispatcher, tokens)

        // still offline: moved to the store, gone from the database, kept for the next try
        revocation.revoke()
        assertEquals(listOf("refresh-old"), tokens.tokens)
        assertNull(database.syncQueries.selectPendingRevocation().executeAsOneOrNull())

        auth.offline = false
        revocation.revoke()
        assertEquals("refresh-old", auth.revoked)
        assertEquals(emptyList(), tokens.tokens)
    }

    // A new offline sign-out used to overwrite the token still waiting (one moved from the database, or an earlier one).
    @Test
    fun aSecondOfflineSignOutKeepsTheFirstTokenToo() = runBlocking {
        database.syncQueries.savePendingRevocation("refresh-old")
        val auth = Auth(offline = true)
        val tokens = MemoryTokens()
        val revocation = PendingRevocation(database, auth, dbDispatcher, tokens)

        assertEquals(OperationResult.Success(Unit), settings(auth, null, revocation).signOut())
        assertEquals(listOf("refresh-old", "refresh-1"), tokens.tokens)

        auth.offline = false
        revocation.revoke()
        assertEquals(listOf("refresh-old", "refresh-1"), auth.revokedAll)
        assertTrue(!revocation.hasPending())
    }

    // A 5xx of the sign-out (after supabase-kt had already cleared the session) left the rows and kept nothing to revoke.
    @Test
    fun aServerFailureOfTheSignOutStillSignsOutAndEndsTheSessionLater() = runBlocking {
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        val auth = Auth().apply { signOutFails = true }
        val tokens = MemoryTokens()
        val revocation = PendingRevocation(database, auth, dbDispatcher, tokens)

        assertEquals(OperationResult.Success(Unit), settings(auth, null, revocation).signOut())

        assertTrue(auth.localSessionCleared)
        assertNull(profiles.profile.first())
        assertTrue(localUser.isGuest())
        assertEquals(listOf("refresh-1"), tokens.tokens)
    }
}
