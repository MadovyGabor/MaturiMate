package com.madovygabor.maturimate.features.reminder.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Plans the daily reminder with the operating system. One reminder at a time: scheduling again replaces the
 * earlier one. The system calls back at the time, and the platform code then decides whether to show it
 * ([nextReminder] gives the next time to plan).
 */
interface ReminderScheduler {
    /** Plans the next reminder at [time] of the day. */
    fun schedule(time: LocalTime)

    /** Removes the planned reminder. */
    fun cancel()
}

/** For platforms without a reminder (the desktop app): does nothing. */
object NoReminderScheduler : ReminderScheduler {
    override fun schedule(time: LocalTime) = Unit
    override fun cancel() = Unit
}

/** The first moment after [now] at which the clock in [zone] shows [time]: today when it is still ahead, otherwise tomorrow. */
@OptIn(ExperimentalTime::class)
fun nextReminder(time: LocalTime, now: Instant, zone: TimeZone): Instant {
    val today: LocalDate = now.toLocalDateTime(zone).date
    val candidate = today.atTime(time).toInstant(zone)
    return if (candidate > now) candidate else today.plus(DatePeriod(days = 1)).atTime(time).toInstant(zone)
}
