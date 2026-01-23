package com.madovygabor.maturimate

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.madovygabor.maturimate.core.di.initKoin
import com.madovygabor.maturimate.features.appOnboarding.data.DATA_STORE_FILE_NAME
import com.madovygabor.maturimate.features.appOnboarding.data.createDataStore

fun main() {

    // Initialize Koin for dependency injection
    initKoin()

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "MaturiMate",
        ) {
            App()
        }
    }
}