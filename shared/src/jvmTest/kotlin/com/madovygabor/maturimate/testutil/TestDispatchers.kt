package com.madovygabor.maturimate.testutil

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * A single-threaded dispatcher for the database calls of the code under test, like the app's `USER_DATABASE` dispatcher.
 *
 * Why one thread: an in-memory `JdbcSqliteDriver` shares ONE connection across threads, so two threads using it at the
 * same time corrupt its state. Running the repository on this dispatcher keeps every database call on one thread at a
 * time (see commit ff3de83). Do not replace it with `Dispatchers.Unconfined` or a multi-threaded dispatcher.
 */
fun testDbDispatcher(): CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)

/**
 * Runs a test body on the main (UI) thread, the thread from which the app calls a ViewModel. Use it for a test that
 * calls ViewModel methods while the ViewModel's own `viewModelScope` work may still run: called from the test thread,
 * a method such as `toggle()` races with that work (two threads on the ViewModel's plain fields; see
 * llm/outputs/practice-picker-flaky-test.md). On the main thread they take turns, as in the app. Waiting inside
 * ([awaitFirst], `withContext`) suspends and frees the main thread, so do not block in the body (no nested `runBlocking`).
 */
fun runOnMain(block: suspend CoroutineScope.() -> Unit) = runBlocking(Dispatchers.Main, block)

/**
 * Lets everything already queued on the main (UI) dispatcher run before the test continues. Use it instead of a
 * `delay` to wait for a ViewModel's `viewModelScope` work that was posted to `Dispatchers.Main`.
 */
suspend fun flushMain() = withContext(Dispatchers.Main) {}
