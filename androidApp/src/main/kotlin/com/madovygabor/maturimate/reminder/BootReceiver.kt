package com.madovygabor.maturimate.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.madovygabor.maturimate.features.reminder.domain.ReminderSync
import com.madovygabor.maturimate.features.reminder.domain.replansReminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Alarms are lost when the phone restarts and when the app is updated: the reminder is planned again. The receiver is
 * exported (the system must reach it), so every other action does nothing.
 */
class BootReceiver : BroadcastReceiver(), KoinComponent {
    override fun onReceive(context: Context, intent: Intent) {
        if (!replansReminder(intent.action)) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                get<ReminderSync>().planNow()
            } catch (_: Exception) {
                // the application class plans it again when the app starts
            } finally {
                pending.finish()
            }
        }
    }
}
