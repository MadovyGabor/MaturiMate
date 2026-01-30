package com.madovygabor.maturimate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.madovygabor.maturimate.core.presentation.navigation.Route
import com.madovygabor.maturimate.features.appOnboarding.domain.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * The main entry point for the Android application.
 * This class handles Android-specific initializations like Edge-to-Edge and the Native Splash Screen.
 */
class MainActivity : ComponentActivity() {



    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        // Inject the repository to check onboarding status before UI launch
        val repository: UserPreferencesRepository by inject()

        // Initialize the Android SplashScreen API
        val splashScreen = installSplashScreen()

        // Enable immersive edge-to-edge display
        enableEdgeToEdge()

        // State variables to control splash screen visibility and initial routing
        var isLoading by mutableStateOf(true)
        var startRoute by mutableStateOf<Route?>(null)

        // Keep the splash screen visible until the start route is determined
        splashScreen.setKeepOnScreenCondition { isLoading }

        // Determine the entry point based on user preferences
        lifecycleScope.launch {
            try {
                // Fetch the first emission of preferences to decide the route
                val prefs = repository.preferences.first()
                startRoute = if (prefs.isSetupCompleted) Route.Main else Route.AppOnboarding
            } catch (e: Exception) {
                // Fallback to Onboarding in case of unexpected errors
                startRoute = Route.AppOnboarding
            } finally {
                // Stop loading once the destination is set
                isLoading = false
            }
        }

        setContent {
            // Only start the App composable once we know where to go
            startRoute?.let { route ->
                App(startRoute = route)
            }
        }
    }
}