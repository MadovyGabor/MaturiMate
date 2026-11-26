package com.madovygabor.maturimate

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.madovygabor.maturimate.core.export.ExportPicker
import com.madovygabor.maturimate.core.presentation.AppViewModel
import com.madovygabor.maturimate.core.presentation.locale.reapplyAppLocale
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * The main entry point for the Android application.
 * This class handles Android-specific initializations like Edge-to-Edge and the Native Splash Screen,
 * and keeps the in-app locale applied across system configuration changes and foreground resumes.
 */
class MainActivity : ComponentActivity() {

    // Passed to App(), so the splash screen and the UI wait for the same first profile read
    private val appViewModel: AppViewModel by viewModel()

    // opens the system's "save as" screen for the export of one's data
    private val exportPicker: ExportPicker by inject()

    override fun onCreate(savedInstanceState: Bundle?) {

        // Initialize the Android SplashScreen API (before super: it swaps the launch theme)
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        exportPicker.register(this)

        // Enable immersive edge-to-edge display
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )

        // Keep the splash screen until the profile has been read (App picks the start screen from it) and that start screen
        // is built (the home screen, or the onboarding with its saved answers), so no loading screen of the app follows it.
        // It also goes when it is clear that the profile cannot be read and App shows the error screen.
        splashScreen.setKeepOnScreenCondition { !appViewModel.failed.value && !appViewModel.startReady.value }

        setContent {
            App(appViewModel)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        reapplyAppLocale()
    }

    override fun onResume() {
        super.onResume()
        reapplyAppLocale()
    }
}
