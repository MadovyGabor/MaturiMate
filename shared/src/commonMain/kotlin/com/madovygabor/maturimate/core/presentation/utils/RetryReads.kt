package com.madovygabor.maturimate.core.presentation.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen

/** Pauses before the retries of a failed read: an error that passes (a busy database) gets three more tries. */
val READ_RETRY_DELAYS_MILLIS = listOf(100L, 500L, 2_000L)

/**
 * Starts a failed read again after each pause in [delaysMillis]; when they are used up, the error
 * goes on to the collector. Without it a single error would end the flow for good.
 */
fun <T> Flow<T>.retryReads(delaysMillis: List<Long> = READ_RETRY_DELAYS_MILLIS): Flow<T> =
    retryWhen { _, attempt ->
        val pause = delaysMillis.getOrNull(attempt.toInt()) ?: return@retryWhen false
        delay(pause)
        true
    }

/**
 * The result of [read], or null when it fails: for a saved answer that only preselects a choice.
 * Unlike `runCatching`, a cancelled read stays cancelled.
 */
suspend fun <T> readOrNull(read: suspend () -> T): T? =
    try {
        read()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
