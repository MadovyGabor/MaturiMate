package com.madovygabor.maturimate.features.sync.domain

import com.madovygabor.maturimate.core.domain.error.OperationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

/** Who is signed in on this phone, as the sync needs to know it. */
interface SyncSession {
    /** True when an account is signed in; a guest has nothing to sync. */
    suspend fun isSignedIn(): Boolean

    /** The id of this installation (`local_device`). */
    suspend fun deviceId(): String
}

/** Why a run started (the manual run and the one before a sign-out upload everything). */
enum class SyncReason { OPENING, FOREGROUND, ROUND_END, LESSON_END, SETTINGS, MANUAL, SIGN_OUT, AFTER_SIGN_IN }

sealed interface SyncOutcome {
    /** A guest, or nobody signed in: nothing to do. */
    data object NotApplicable : SyncOutcome

    /** Another run was in progress; one more will follow it. */
    data object Queued : SyncOutcome

    data class Done(val contentReleases: List<ContentRelease>) : SyncOutcome

    data class Failed(val error: SyncError) : SyncOutcome
}

/**
 * One run of the sync: send what changed, apply what the server returns, move the cursor. One run at a time; a request that
 * comes during a run sets one more run after it. Errors are quiet: they go into the state, nothing is thrown.
 */
class SyncEngine(
    private val repository: SyncRepository,
    private val store: SyncStore,
    private val session: SyncSession,
    private val now: () -> Long,
    private val maxRounds: Int = DEFAULT_BATCH_ROUNDS,
) {
    private val mutex = Mutex()
    private var rerun = false
    private var batchRounds = maxRounds

    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    /** Runs the sync, or queues one more run when one is in progress. */
    suspend fun sync(reason: SyncReason): SyncOutcome {
        if (!session.isSignedIn()) {
            _state.value = SyncState()
            return SyncOutcome.NotApplicable
        }
        if (!mutex.tryLock()) {
            rerun = true
            return SyncOutcome.Queued
        }
        try {
            var outcome: SyncOutcome
            var next = reason
            do {
                rerun = false
                outcome = runOnce(next)
                next = SyncReason.FOREGROUND
            } while (rerun && outcome is SyncOutcome.Done)
            return outcome
        } finally {
            mutex.unlock()
        }
    }

    /** The run before a sign-out: it waits for a run in progress and uploads everything. */
    suspend fun syncBeforeSignOut(): SyncOutcome {
        if (!session.isSignedIn()) return SyncOutcome.NotApplicable
        return mutex.withLock { runOnce(SyncReason.SIGN_OUT) }
    }

    /** Reads the state again (a round finished, the clock moved). */
    suspend fun refresh() {
        if (!session.isSignedIn()) {
            _state.value = SyncState()
            return
        }
        publish(running = _state.value.running)
    }

    private suspend fun publish(running: Boolean, error: SyncError? = _state.value.lastError, clearError: Boolean = false) {
        val stored = store.state()
        val (rounds, oldest) = store.pendingSummary()
        _state.value = SyncState(
            lastSuccessAt = stored.lastSuccessAt,
            pendingRounds = rounds,
            oldestPendingAt = oldest,
            lastError = if (clearError) null else error,
            running = running,
            rejectedCount = stored.rejectedCount,
            signedIn = true,
        )
    }

    private suspend fun runOnce(reason: SyncReason): SyncOutcome {
        publish(running = true)
        val deviceId = session.deviceId()
        val stored = store.state()
        var full = stored.fullPushPending || reason == SyncReason.MANUAL || reason == SyncReason.SIGN_OUT
        var since: Long? = if (full) null else stored.lastPushAt
        var cursor = stored.cursor
        val guestMerge = stored.guestMergePending
        var releases = emptyList<ContentRelease>()
        // the pages after the first one of a long pull send nothing
        var pullOnly = false
        var retriedLarge = false
        var retriedCursor = false

        while (true) {
            val pending = if (pullOnly) PendingChanges(SyncChanges(), null, false) else store.pending(since, full, batchRounds)
            when (val result = repository.sync(cursor, deviceId, guestMerge, pending.changes)) {
                is OperationResult.Error -> {
                    when {
                        result.error == SyncError.TOO_LARGE && !retriedLarge -> {
                            // the next call carries half as many rounds
                            retriedLarge = true
                            batchRounds = maxOf(1, batchRounds / 2)
                            continue
                        }
                        result.error == SyncError.CURSOR_TOO_OLD && !retriedCursor -> {
                            // everything again
                            retriedCursor = true
                            cursor = null
                            continue
                        }
                    }
                    store.markFailed(result.error)
                    publish(running = false, error = result.error)
                    return SyncOutcome.Failed(result.error)
                }
                is OperationResult.Success -> {
                    val response = result.data
                    if (abs(response.serverTime - now()) > CLOCK_SKEW_MS) store.requestFullPush()
                    if (pending.moreRounds) {
                        // only the push of this call counts; the pull comes with the last call
                        store.markPage(pending.pushedUpTo)
                        since = pending.pushedUpTo
                        full = false
                        continue
                    }
                    store.apply(response, guestMerge, pending.pushedUpTo, now())
                    releases = response.contentReleases
                    if (!response.more) break
                    // the next page of the pull: nothing to send
                    cursor = response.cursor
                    pullOnly = true
                }
            }
        }
        publish(running = false, clearError = true)
        return SyncOutcome.Done(releases)
    }

    companion object {
        const val DEFAULT_BATCH_ROUNDS = 200
        const val CLOCK_SKEW_MS = 5L * 60 * 1000
    }
}
