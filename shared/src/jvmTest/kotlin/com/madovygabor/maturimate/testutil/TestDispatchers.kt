package com.madovygabor.maturimate.testutil

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
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
 * Lets everything already queued on the main (UI) dispatcher run before the test continues. Use it instead of a
 * `delay` to wait for a ViewModel's `viewModelScope` work that was posted to `Dispatchers.Main`.
 */
suspend fun flushMain() = withContext(Dispatchers.Main) {}
