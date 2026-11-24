package com.madovygabor.maturimate.core.presentation

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
actual fun rememberNotificationPermissionRequest(onResult: (NotificationPermissionResult) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // after a refusal the system still offers its dialog only while it says the rationale should be shown; a
        // request that returns at once (denied for good) ends here with that answer false too
        val canAskAgain = context.findActivity()?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.POST_NOTIFICATIONS)
        } ?: true
        onResult(notificationPermissionResult(granted, canAskAgain))
    }
    return {
        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> onResult(NotificationPermissionResult.GRANTED)
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED -> onResult(NotificationPermissionResult.GRANTED)
            else -> launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
actual fun rememberOpenNotificationSettings(): () -> Unit {
    val context = LocalContext.current
    return {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // a phone without that screen: the page of the app itself
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}

actual val notificationsNeedPermission: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

actual val remindersAvailable: Boolean = true
