package com.madovygabor.maturimate.core.presentation

import androidx.compose.runtime.Composable

/** How a request for the notification permission ended. */
enum class NotificationPermissionResult {
    GRANTED,

    /** The user said no this time; the system will show its dialog again on the next request. */
    DENIED,

    /** The system will not show its dialog any more (denied for good): only the system settings can allow it. */
    BLOCKED,
}

/**
 * The outcome of a request that ended without the permission: [canAskAgain] is what the system says after the answer
 * (Android: `shouldShowRequestPermissionRationale`). It is false after the second "no", and after "don't ask again".
 */
fun notificationPermissionResult(granted: Boolean, canAskAgain: Boolean): NotificationPermissionResult = when {
    granted -> NotificationPermissionResult.GRANTED
    canAskAgain -> NotificationPermissionResult.DENIED
    else -> NotificationPermissionResult.BLOCKED
}

/**
 * Asks the system to allow notifications and reports the answer. On Android 13 and newer this shows the system
 * dialog (not any more once it was refused for good: the result is then [NotificationPermissionResult.BLOCKED]);
 * elsewhere notifications need no permission.
 *
 * @return A function that starts the request; [onResult] gets the answer.
 */
@Composable
expect fun rememberNotificationPermissionRequest(onResult: (NotificationPermissionResult) -> Unit): () -> Unit

/** A function that opens the notification settings of the app in the system settings (does nothing where there are none). */
@Composable
expect fun rememberOpenNotificationSettings(): () -> Unit

/** True where the system asks the user before the app may send notifications. */
expect val notificationsNeedPermission: Boolean

/** True where the app can send the daily reminder (Android); the desktop app has no reminder, so it has no reminder step either. */
expect val remindersAvailable: Boolean
