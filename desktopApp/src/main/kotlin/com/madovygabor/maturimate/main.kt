package com.madovygabor.maturimate

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.madovygabor.maturimate.core.di.initKoin

/**
 * Entry point for the Desktop (JVM) application.
 * Initializes dependency injection and opens the window.
 */
fun main() {

    // 1. Initialize Koin for shared and platform-specific dependencies
    initKoin()

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
