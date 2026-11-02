package com.madovygabor.maturimate.core.presentation

import androidx.compose.runtime.Composable

// Desktop systems let apps notify without asking.
@Composable
actual fun rememberNotificationPermissionRequest(onResult: (granted: Boolean) -> Unit): () -> Unit = { onResult(true) }

actual val notificationsNeedPermission: Boolean = false
