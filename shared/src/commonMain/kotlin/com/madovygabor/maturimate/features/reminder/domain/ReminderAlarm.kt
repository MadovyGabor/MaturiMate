package com.madovygabor.maturimate.features.reminder.domain

import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalTime

/** How long one alarm may work, under the time the system gives a receiver that went async (about ten seconds). */
const val REMINDER_ALARM_TIMEOUT_MILLIS = 7_000L

/**
 * Handles one alarm of the daily reminder: [readTime] reads the profile (the time when the reminder is on, null when it
 * is off), [show] shows the notification when it is due. Whatever happens (a failed read, an error while showing, the
 * time limit), the next day's alarm is planned, so one bad day does not end the reminder for good. When the time is
 * not known (the read failed or took too long), the next alarm is planned for [fallbackTime], the time of day it is now.
 * A cancellation is passed on, but the planning still happens first.
 */
suspend fun handleReminderAlarm(
    scheduler: ReminderScheduler,
    fallbackTime: LocalTime,
    timeoutMillis: Long = REMINDER_ALARM_TIMEOUT_MILLIS,
    readTime: suspend () -> LocalTime?,
    show: suspend () -> Unit,
) {
    var time: LocalTime? = null
    var read = false
    try {
        withTimeoutOrNull(timeoutMillis) {
            time = readTime()
            read = true
            if (time != null) show()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // nothing to tell the user at this hour; the next day is planned below
    } finally {
        // off (read and null): no next alarm; otherwise the known time, or the fallback when the read did not finish
        val next = if (read) time else fallbackTime
        if (next != null) scheduler.schedule(next)
    }
}

/**
 * The version of the reminder for the lock screen: the same whatever the message, so it shows neither the streak nor the
 * days to the exam. The full message goes in the notification itself, which the phone shows only when it is unlocked.
 */
fun publicReminderMessage(): ReminderMessage = ReminderMessage(ReminderKind.PLAIN, variant = 0, number = null)

/**
 * The language tag of the texts of the notification: the per-app language when the app has one (the language the app
 * itself shows), otherwise the language saved in the profile.
 */
fun reminderLanguageTag(appLocaleTag: String?, profileLanguage: TeachingLanguage): String {
    val app = appLocaleTag?.substringBefore('-')?.lowercase()
    return when {
        app == "hu" || app == "sk" -> app
        profileLanguage == TeachingLanguage.HU -> "hu"
        else -> "sk"
    }
}
