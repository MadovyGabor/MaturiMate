package com.madovygabor.maturimate.features.learning.domain

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Measures the study time of one lesson or test between the student's moves (a page turned, a task answered).
 * A gap longer than [idleCap] counts as [idleCap] only: a phone put down in the middle of a lesson does not
 * fill the daily goal.
 */
@OptIn(ExperimentalTime::class)
class StudyClock(
    private val idleCap: Int = IDLE_CAP_SECONDS,
    private val now: () -> Instant = { Clock.System.now() },
) {
    private var last = now()

    /** The seconds since the last move (at most [idleCap]); the next gap is counted from now. */
    fun lap(): Int {
        val time = now()
        val seconds = (time - last).inWholeSeconds.coerceIn(0, idleCap.toLong()).toInt()
        last = time
        return seconds
    }

    companion object {
        const val IDLE_CAP_SECONDS = 120
    }
}
