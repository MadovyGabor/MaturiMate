package com.madovygabor.maturimate.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.madovygabor.maturimate.features.reminder.domain.ReminderScheduler
import com.madovygabor.maturimate.features.reminder.domain.nextReminder
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Plans the reminder as one inexact alarm (a few minutes late is fine, and it needs no special permission).
 * [ReminderReceiver] shows the notification and plans the next day's alarm.
 */
@OptIn(ExperimentalTime::class)
class AndroidReminderScheduler(private val context: Context) : ReminderScheduler {

    private val alarms get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pendingIntent() = PendingIntent.getBroadcast(
        context, 0, Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    override fun schedule(time: LocalTime) {
        val at = nextReminder(time, Clock.System.now(), TimeZone.currentSystemDefault())
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilliseconds(), pendingIntent())
    }

    override fun cancel() = alarms.cancel(pendingIntent())
}
