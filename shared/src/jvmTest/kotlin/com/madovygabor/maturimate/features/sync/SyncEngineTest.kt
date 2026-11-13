package com.madovygabor.maturimate.features.sync

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.sync.domain.PendingChanges
import com.madovygabor.maturimate.features.sync.domain.StoredSyncState
import com.madovygabor.maturimate.features.sync.domain.SyncAttempt
import com.madovygabor.maturimate.features.sync.domain.SyncChanges
import com.madovygabor.maturimate.features.sync.domain.SyncEngine
import com.madovygabor.maturimate.features.sync.domain.SyncError
import com.madovygabor.maturimate.features.sync.domain.SyncOutcome
import com.madovygabor.maturimate.features.sync.domain.SyncReason
import com.madovygabor.maturimate.features.sync.domain.SyncRepository
import com.madovygabor.maturimate.features.sync.domain.SyncResponse
import com.madovygabor.maturimate.features.sync.domain.SyncSession
import com.madovygabor.maturimate.features.sync.domain.SyncState
import com.madovygabor.maturimate.features.sync.domain.SyncStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SyncEngineTest {

    private class FakeSession(var signedIn: Boolean = true) : SyncSession {
        override suspend fun isSignedIn() = signedIn
        override suspend fun deviceId() = "dev"
    }

    private class FakeStore : SyncStore {
        var state = StoredSyncState("u", null, null, null, fullPushPending = true, guestMergePending = false, rejectedCount = 0)
        val pendingCalls = mutableListOf<Triple<Long?, Boolean, Int>>()
        val applied = mutableListOf<SyncResponse>()
        var failed: SyncError? = null
        var fullPushRequested = false
        var pushedPages = mutableListOf<Long?>()
        var rounds: List<PendingChanges> = emptyList()
        var rounds_i = 0
        var pendingRoundsCount = 0 to (null as Long?)

        override suspend fun state() = state
        override suspend fun pending(since: Long?, full: Boolean, maxRounds: Int): PendingChanges {
            pendingCalls += Triple(since, full, maxRounds)
            return if (rounds.isEmpty()) PendingChanges(SyncChanges(), null, false) else rounds[minOf(rounds_i++, rounds.lastIndex)]
        }
        override suspend fun apply(response: SyncResponse, guestMerge: Boolean, pushedUpTo: Long?, now: Long) {
            applied += response
            state = state.copy(cursor = response.cursor, lastSuccessAt = now, lastPushAt = pushedUpTo ?: state.lastPushAt, fullPushPending = false)
        }
        override suspend fun markPage(pushedUpTo: Long?) { pushedPages += pushedUpTo }
        override suspend fun markFailed(error: SyncError) { failed = error }
        override suspend fun requestFullPush() { fullPushRequested = true }
        override suspend fun pendingSummary() = pendingRoundsCount
        override suspend fun clear() {}
        override fun changes(): Flow<Unit> = emptyFlow()
    }

    private class FakeRepository : SyncRepository {
        val calls = mutableListOf<Triple<String?, Boolean, SyncChanges>>()
        val answers = ArrayDeque<OperationResult<SyncResponse, SyncError>>()
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun sync(cursor: String?, deviceId: String, guestMerge: Boolean, changes: SyncChanges): OperationResult<SyncResponse, SyncError> {
            calls += Triple(cursor, guestMerge, changes)
            gate?.await()
            return answers.removeFirstOrNull() ?: OperationResult.Success(response())
        }
    }

    private companion object {
        fun response(cursor: String = "c", more: Boolean = false, serverTime: Long = 1_000) = SyncResponse(serverTime = serverTime, cursor = cursor, more = more)
        fun ok(r: SyncResponse) = OperationResult.Success<SyncResponse, SyncError>(r)
        fun err(e: SyncError) = OperationResult.Error<SyncResponse, SyncError>(e)
    }

    private val session = FakeSession()
    private val store = FakeStore()
    private val repository = FakeRepository()
    private val engine = SyncEngine(repository, store, session, now = { 1_000 })

    @Test
    fun aGuestDoesNothing() = runBlocking {
        session.signedIn = false

        assertEquals(SyncOutcome.NotApplicable, engine.sync(SyncReason.OPENING))
        assertTrue(repository.calls.isEmpty())
        assertFalse(engine.state.value.signedIn)
    }

    @Test
    fun theFirstRunPushesEverythingAndStoresTheCursor() = runBlocking {
        repository.answers += ok(response(cursor = "c1"))

        assertIs<SyncOutcome.Done>(engine.sync(SyncReason.OPENING))

        assertEquals(Triple(null as Long?, true, 200), store.pendingCalls.single())
        assertEquals("c1", store.state.cursor)
        assertTrue(engine.state.value.signedIn)
        assertEquals(1_000L, engine.state.value.lastSuccessAt)
    }

    @Test
    fun laterRunsSendOnlyWhatChangedAfterThePushMark() = runBlocking {
        store.state = store.state.copy(fullPushPending = false, lastPushAt = 500, cursor = "old")

        engine.sync(SyncReason.ROUND_END)

        assertEquals(Triple(500L as Long?, false, 200), store.pendingCalls.single())
        assertEquals("old", repository.calls.single().first)
    }

    @Test
    fun theManualRunAndTheRunBeforeSignOutPushEverything() = runBlocking {
        store.state = store.state.copy(fullPushPending = false, lastPushAt = 500)

        engine.sync(SyncReason.MANUAL)
        engine.syncBeforeSignOut()

        assertEquals(listOf(true, true), store.pendingCalls.map { it.second })
        assertEquals(listOf<Long?>(null, null), store.pendingCalls.map { it.first })
    }

    @Test
    fun aLongPullIsFetchedPageByPageWithNothingToSendAfterTheFirst() = runBlocking {
        store.rounds = listOf(PendingChanges(SyncChanges(attempts = listOf(SyncAttempt("a", "UNIT_ROUND", null, 1, "FINISHED", 1))), 10, false))
        repository.answers += ok(response("p1", more = true))
        repository.answers += ok(response("p2", more = true))
        repository.answers += ok(response("p3", more = false))

        engine.sync(SyncReason.OPENING)

        assertEquals(listOf<String?>(null, "p1", "p2"), repository.calls.map { it.first })
        assertEquals(1, repository.calls[0].third.attempts.size)
        assertTrue(repository.calls[1].third.isEmpty && repository.calls[2].third.isEmpty)
        assertEquals(3, store.applied.size)
        assertEquals("p3", store.state.cursor)
    }

    @Test
    fun aBacklogOfRoundsIsSentInBatchesBeforeTheLastPullIsApplied() = runBlocking {
        store.rounds = listOf(
            PendingChanges(SyncChanges(), pushedUpTo = 100, moreRounds = true),
            PendingChanges(SyncChanges(), pushedUpTo = 200, moreRounds = false),
        )

        engine.sync(SyncReason.OPENING)

        assertEquals(2, repository.calls.size)
        assertEquals(listOf<Long?>(100), store.pushedPages)
        assertEquals(1, store.applied.size)
        // the second batch continues after the first one's mark
        assertEquals(100L, store.pendingCalls[1].first)
        assertFalse(store.pendingCalls[1].second)
    }

    @Test
    fun aFailureIsQuietAndKeptInTheState() = runBlocking {
        repository.answers += err(SyncError.NETWORK)

        val outcome = engine.sync(SyncReason.OPENING)

        assertEquals(SyncOutcome.Failed(SyncError.NETWORK), outcome)
        assertEquals(SyncError.NETWORK, store.failed)
        assertEquals(SyncError.NETWORK, engine.state.value.lastError)
        assertFalse(engine.state.value.showBadge(now = 1_000))
        // a server error shows the badge at once
        repository.answers += err(SyncError.SERVER)
        engine.sync(SyncReason.MANUAL)
        assertTrue(engine.state.value.showBadge(now = 1_000))
        // and the next good run clears it
        engine.sync(SyncReason.MANUAL)
        assertEquals(null, engine.state.value.lastError)
    }

    @Test
    fun aCallThatIsTooLargeIsRepeatedWithHalfTheRounds() = runBlocking {
        repository.answers += err(SyncError.TOO_LARGE)

        assertIs<SyncOutcome.Done>(engine.sync(SyncReason.OPENING))

        assertEquals(listOf(200, 100), store.pendingCalls.map { it.third })
    }

    @Test
    fun aTooOldCursorStartsTheWholePullAgain() = runBlocking {
        store.state = store.state.copy(cursor = "ancient", fullPushPending = false, lastPushAt = 5)
        repository.answers += err(SyncError.CURSOR_TOO_OLD)

        assertIs<SyncOutcome.Done>(engine.sync(SyncReason.OPENING))

        assertEquals(listOf<String?>("ancient", null), repository.calls.map { it.first })
    }

    @Test
    fun aClockFarFromTheServersAsksForAFullPush() = runBlocking {
        repository.answers += ok(response(serverTime = 1_000 + 10 * 60 * 1000))

        engine.sync(SyncReason.OPENING)

        assertTrue(store.fullPushRequested)
    }

    @Test
    fun aRequestDuringARunQueuesOneMoreRun() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate

        val first = launch { engine.sync(SyncReason.OPENING) }
        // let the first run reach the call
        while (repository.calls.isEmpty()) kotlinx.coroutines.yield()
        assertEquals(SyncOutcome.Queued, engine.sync(SyncReason.ROUND_END))
        assertEquals(SyncOutcome.Queued, engine.sync(SyncReason.ROUND_END))
        gate.complete(Unit)
        first.join()

        // one run for the first request, one for all the queued ones
        assertEquals(2, repository.calls.size)
    }

    @Test
    fun theStateShowsTheRoundsThatWaitForTheUpload() = runBlocking {
        store.pendingRoundsCount = 2 to 700L
        engine.refresh()

        assertEquals(2, engine.state.value.pendingRounds)
        assertEquals(700L, engine.state.value.oldestPendingAt)
        assertTrue(engine.state.value.showBadge(now = 700L + SyncState.DAY_MS + 1))
        assertFalse(engine.state.value.showBadge(now = 700L + 1000))
    }
}
