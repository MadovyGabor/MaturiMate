package com.madovygabor.maturimate.core.presentation.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RetryReadsTest {

    @Test
    fun aReadIsRetriedOncePerPause() = runBlocking {
        var attempts = 0
        val values = flow {
            attempts++
            if (attempts < 3) error("busy")
            emit("profile")
        }.retryReads(listOf(1L, 1L)).toList()

        assertEquals(listOf("profile"), values)
        assertEquals(3, attempts)
    }

    @Test
    fun theErrorGoesOnWhenThePausesAreUsedUp() = runBlocking {
        var attempts = 0
        val values = flow<String> {
            attempts++
            error("busy")
        }.retryReads(listOf(1L, 1L)).catch { emit("failed") }.toList()

        assertEquals(listOf("failed"), values)
        assertEquals(3, attempts)
    }

    @Test
    fun aFailedReadIsNull() = runBlocking {
        assertNull(readOrNull<String> { error("cannot read") })
    }

    @Test
    fun aCancelledReadStaysCancelled() {
        assertFailsWith<CancellationException> {
            runBlocking { readOrNull<String> { throw CancellationException("screen closed") } }
        }
    }
}
