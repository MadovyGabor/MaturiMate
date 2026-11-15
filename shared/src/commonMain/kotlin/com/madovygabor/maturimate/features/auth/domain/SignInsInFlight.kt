package com.madovygabor.maturimate.features.auth.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

/**
 * The browser sign-ins that are still running (desktop Google: from the start of the wait until its receiver has stopped,
 * a code exchange included). A sign-in that ends after the screen went away can still save a session; whoever compares
 * the sessions after such an end (the cancel of the deletion proof) waits here first, so it sees that session.
 */
class SignInsInFlight {
    private val running = MutableStateFlow(0)

    /** Runs [block] as one sign-in in flight. */
    suspend fun <T> track(block: suspend () -> T): T {
        running.update { it + 1 }
        try {
            return block()
        } finally {
            running.update { it - 1 }
        }
    }

    /** Waits until no sign-in runs, at most [limit]; true when none runs. */
    suspend fun awaitNone(limit: Duration): Boolean = withTimeoutOrNull(limit) { running.first { it == 0 } } != null
}
