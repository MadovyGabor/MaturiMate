package com.madovygabor.maturimate.core.presentation

import androidx.compose.runtime.Composable

/**
 * Asks the system to allow notifications and reports whether they are allowed. On Android 13
 * and newer this shows the system dialog once; elsewhere notifications need no permission.
 *
 * @return A function that starts the request; [onResult] gets the answer.
 */
@Composable
expect fun rememberNotificationPermissionRequest(onResult: (granted: Boolean) -> Unit): () -> Unit

/** True where the system asks the user before the app may send notifications. */
expect val notificationsNeedPermission: Boolean
