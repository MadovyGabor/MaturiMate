package com.madovygabor.maturimate.features.learning.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class StudyClockTest {

    private var millis = 0L
    private val clock = StudyClock(idleCap = 120) { Instant.fromEpochMilliseconds(millis) }

    @Test
    fun lapIsTheSecondsSinceTheLastMove() {
        millis = 45_000
        assertEquals(45, clock.lap())
        millis = 50_500
        assertEquals(5, clock.lap())
    }

    @Test
    fun aLongGapCountsAsTheCapOnly() {
        millis = 3_600_000
        assertEquals(120, clock.lap())
        millis += 10_000
        assertEquals(10, clock.lap())
    }
}
