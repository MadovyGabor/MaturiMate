package com.madovygabor.maturimate.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Alarms are lost when the phone restarts and when the app is updated. Starting the process is enough: the application class starts
 * [com.madovygabor.maturimate.features.reminder.domain.ReminderSync], which plans the reminder again.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
