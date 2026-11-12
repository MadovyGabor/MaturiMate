package com.madovygabor.maturimate.features.sync.domain

import kotlinx.coroutines.flow.Flow

/** The state of the sync on this phone, as the settings row and the profile badge show it. */
data class SyncState(
    val lastSuccessAt: Long? = null,
    val pendingRounds: Int = 0,
    val oldestPendingAt: Long? = null,
    val lastError: SyncError? = null,
    val running: Boolean = false,
    val rejectedCount: Int = 0,
    val signedIn: Boolean = false,
) {
    /**
     * The small quiet sign in the profile header: something has been waiting for more than a day, or the last try
     * failed for a reason other than "no network".
     */
    fun showBadge(now: Long): Boolean =
        signedIn && ((oldestPendingAt != null && oldestPendingAt < now - DAY_MS) || (lastError != null && lastError != SyncError.NETWORK))

    companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

/** What a [SyncStore.pending] call collected, with the mark to remember after it was sent. */
data class PendingChanges(
    val changes: SyncChanges,
    /** `last_push_at` after a successful call: the newest time among the rows sent; null when nothing was sent. */
    val pushedUpTo: Long?,
    /** More rounds wait than this call carries. */
    val moreRounds: Boolean,
)

/** The saved state of the sync of the current user. */
data class StoredSyncState(
    val userId: String,
    val cursor: String?,
    val lastPushAt: Long?,
    val lastSuccessAt: Long?,
    val fullPushPending: Boolean,
    val guestMergePending: Boolean,
    val rejectedCount: Int,
)

/** The phone side of the sync: what to send, and the application of the answer. The SQLDelight classes stay behind it. */
interface SyncStore {
    /** The state of the current user's sync, created on the first call. */
    suspend fun state(): StoredSyncState

    /** What to send: the rows changed since [since] (everything when [full]); at most [maxRounds] rounds, the oldest first. */
    suspend fun pending(since: Long?, full: Boolean, maxRounds: Int): PendingChanges

    /** Applies the answer in one transaction (the cursor moves in the same one), as a guest merge when [guestMerge]. */
    suspend fun apply(response: SyncResponse, guestMerge: Boolean, pushedUpTo: Long?, now: Long)

    /** Records that a call with only a part of the waiting rounds went through: the push mark moves, the cursor stays. */
    suspend fun markPage(pushedUpTo: Long?)

    suspend fun markFailed(error: SyncError)

    /** Sets or clears the flag that the next full push is due (a clock jump, rejected rows). */
    suspend fun requestFullPush()

    /** The rounds that wait for the next upload, and the time the oldest finished. */
    suspend fun pendingSummary(): Pair<Int, Long?>

    /** Everything that is not a change of the data: the state row is removed (sign-out). */
    suspend fun clear()

    /** Emits when the stored state changes. */
    fun changes(): Flow<Unit>
}

/** The server side of the sync, one call. */
interface SyncRepository {
    suspend fun sync(cursor: String?, deviceId: String, guestMerge: Boolean, changes: SyncChanges): com.madovygabor.maturimate.core.domain.error.OperationResult<SyncResponse, SyncError>
}
