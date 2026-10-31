package com.madovygabor.maturimate

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.madovygabor.maturimate.core.di.initKoin
import com.madovygabor.maturimate.core.presentation.navigation.Route
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.compose.koinInject

/**
 * Entry point for the Desktop (JVM) application.
 * Handles Dependency Injection initialization and initial routing logic.
 */
fun main() {

    // 1. Initialize Koin for shared and platform-specific dependencies
    initKoin()

    application {
        // Inject the repository using the Compose-friendly Koin delegate
        val repository: UserProfileRepository = koinInject()

        /**
         * Determine the start route synchronously before opening the window.
         * On Desktop, this acts as the "Splash" phase to ensure a flicker-free launch.
         */
        val startRoute = runBlocking {
            try {
                val profile = repository.profile.first()
                if (profile?.isOnboardingCompleted == true) Route.Main else Route.AppOnboarding
            } catch (e: Exception) {
                // Fallback to onboarding if the profile cannot be read
                Route.AppOnboarding
            }
        }

        // 2. Launch the main application window
        Window(
            onCloseRequest = ::exitApplication,
            title = "MaturiMate",
            // You could add icon = painterResource(...) here later
        ) {
            // Call the common App entry point
            App(startRoute = startRoute)
        }
    }
}