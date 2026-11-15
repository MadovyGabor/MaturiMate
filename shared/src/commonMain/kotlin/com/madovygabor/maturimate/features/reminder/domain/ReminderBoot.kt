package com.madovygabor.maturimate.features.reminder.domain

private const val BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED"
private const val MY_PACKAGE_REPLACED = "android.intent.action.MY_PACKAGE_REPLACED"

/**
 * Whether a broadcast to the boot receiver means the alarms are gone (the phone restarted, or the app was updated) and the
 * reminder has to be planned again. The receiver is exported, so any other action (or none) must do nothing.
 */
fun replansReminder(action: String?): Boolean = action == BOOT_COMPLETED || action == MY_PACKAGE_REPLACED
