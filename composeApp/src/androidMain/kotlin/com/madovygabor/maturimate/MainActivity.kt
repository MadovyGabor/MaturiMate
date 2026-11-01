package com.madovygabor.maturimate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.madovygabor.maturimate.core.presentation.AppViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * The main entry point for the Android application.
 * This class handles Android-specific initializations like Edge-to-Edge and the Native Splash Screen.
 */
class MainActivity : ComponentActivity() {

    // Passed to App(), so the splash screen and the UI wait for the same first profile read
    private val appViewModel: AppViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        // Initialize the Android SplashScreen API
        val splashScreen = installSplashScreen()

        // Enable immersive edge-to-edge display
        enableEdgeToEdge()

        // Keep the splash screen until the profile has been read: App picks the start screen from it
        splashScreen.setKeepOnScreenCondition { appViewModel.state.value == null }

        setContent {
            App(appViewModel)
        }
    }
}
