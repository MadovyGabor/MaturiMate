package com.madovygabor.maturimate.core.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
actual fun rememberNotificationPermissionRequest(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission(), onResult)
    return {
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> onResult(true)
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED -> onResult(true)
            else -> launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

actual val notificationsNeedPermission: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
