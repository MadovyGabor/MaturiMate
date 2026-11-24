package com.madovygabor.maturimate.testutil

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** The default upper bound for waiting on something in a test. It is a limit for a slow machine, not a delay. */
val TEST_TIMEOUT: Duration = 10.seconds

/** Returns the first value of the flow that matches [predicate]; fails with a timeout after [timeout]. */
suspend fun <T> Flow<T>.awaitFirst(timeout: Duration = TEST_TIMEOUT, predicate: (T) -> Boolean = { true }): T =
    withTimeout(timeout) { first(predicate) }

/** Returns the result of this [Deferred]; fails with a timeout after [timeout]. */
suspend fun <T> Deferred<T>.awaitWithin(timeout: Duration = TEST_TIMEOUT): T = withTimeout(timeout) { await() }

/**
 * Checks [condition] repeatedly (every [pollEvery]) until it is true; fails with a timeout after [timeout].
 * For state that has no flow to wait on.
 */
suspend fun awaitCondition(
    timeout: Duration = TEST_TIMEOUT,
    pollEvery: Duration = 10.milliseconds,
    condition: () -> Boolean,
) {
    withTimeout(timeout) {
        while (!condition()) delay(pollEvery)
    }
}

/**
 * True when the flow emits no value matching [predicate] within [window]. The window is how long a missing event is
 * waited for, so keep it short; a value emitted earlier and already buffered is seen at once.
 */
suspend fun <T> Flow<T>.emitsNothingWithin(window: Duration = 300.milliseconds, predicate: (T) -> Boolean = { true }): Boolean =
    withTimeoutOrNull(window) { first(predicate) } == null
