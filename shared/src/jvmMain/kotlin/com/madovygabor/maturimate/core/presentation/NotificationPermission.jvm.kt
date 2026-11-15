package com.madovygabor.maturimate.core.presentation

import androidx.compose.runtime.Composable

// Desktop systems let apps notify without asking, and the desktop app sends no reminder.
@Composable
actual fun rememberNotificationPermissionRequest(onResult: (NotificationPermissionResult) -> Unit): () -> Unit =
    { onResult(NotificationPermissionResult.GRANTED) }

@Composable
actual fun rememberOpenNotificationSettings(): () -> Unit = {}

actual val notificationsNeedPermission: Boolean = false

actual val remindersAvailable: Boolean = false
