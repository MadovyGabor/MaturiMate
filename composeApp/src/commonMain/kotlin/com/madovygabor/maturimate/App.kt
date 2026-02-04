package com.madovygabor.maturimate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.navigation.NavigationRoute
import com.madovygabor.maturimate.core.presentation.navigation.Route
import com.madovygabor.maturimate.core.presentation.systemPadding
import com.madovygabor.maturimate.core.presentation.utils.snackbar.MainScaffoldWrapper

/**
 * The root Composable of the MaturiMate application.
 * Sets up the global theme, scaffold, and navigation entry point.
 * * @param startRoute The initial screen to display when the app launches
 * (e.g., Splash, Onboarding, or Main Dashboard).
 */
@Composable
fun App(
    startRoute: Route,
) {

    // Apply the application's Material 3 theme
    AppTheme {
        // Global scaffold wrapper that manages Snackbars and system-level UI
        MainScaffoldWrapper {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = MaterialTheme.colorScheme.background)
            ) {
                // Main navigation host that handles transitions between routes
                NavigationRoute(
                    modifier = Modifier.systemPadding(),
                    startRoute = startRoute,
                )
            }
        }
    }
}