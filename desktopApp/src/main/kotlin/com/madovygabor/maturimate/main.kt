package com.madovygabor.maturimate

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.madovygabor.maturimate.core.di.initKoin
import com.madovygabor.maturimate.features.sync.domain.SyncTriggers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * Entry point for the Desktop (JVM) application.
 * Initializes dependency injection and opens the window.
 */
fun main() {

    // 1. Initialize Koin for shared and platform-specific dependencies
    initKoin()

    // uploads a settings change a few seconds after it was made (the same as MyApplication on Android)
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    appScope.launch { GlobalContext.get().get<SyncTriggers>().run() }

    application {
        // 2. Launch the main application window; App reads the profile and picks the start screen
        Window(
            onCloseRequest = ::exitApplication,
            title = "MaturiMate",
            // You could add icon = painterResource(...) here later
        ) {
            // Call the common App entry point
            App()
        }
    }
}
