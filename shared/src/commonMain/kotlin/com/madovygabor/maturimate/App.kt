package com.madovygabor.maturimate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavKey
import androidx.compose.ui.Modifier
import com.madovygabor.maturimate.core.presentation.AppViewModel
import com.madovygabor.maturimate.core.presentation.locale.LocalAppLocale
import com.madovygabor.maturimate.core.presentation.navigation.NavigationRoute
import com.madovygabor.maturimate.core.presentation.navigation.Route
import com.madovygabor.maturimate.core.presentation.systemPadding
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.utils.snackbar.MainScaffoldWrapper
import org.koin.compose.viewmodel.koinViewModel

/**
 * The root Composable of the MaturiMate application.
 * Sets up the global theme, scaffold, the app language and the navigation entry point.
 *
 * The language and the start screen come from the saved profile. When either changes
 * (a teaching language is picked, the onboarding is finished), the screens are built
 * again in the new language, and the onboarding continues from the saved answers.
 */
@Composable
fun App(viewModel: AppViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()

    // Apply the application's Material 3 theme
    AppTheme {
        // Global scaffold wrapper that manages Snackbars and system-level UI
        MainScaffoldWrapper {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = MaterialTheme.colorScheme.background)
            ) {
                // nothing to show until the profile has been read once
                val appState = state ?: return@Box
                // the open tab outlives the rebuild, so a language changed in the profile stays there
                val mainTab = remember { mutableStateOf<NavKey>(Route.Main.Subject) }
                CompositionLocalProvider(LocalAppLocale provides appState.language?.name?.lowercase()) {
                    key(appState.language, appState.onboardingCompleted) {
                        // Main navigation host that handles transitions between routes
                        NavigationRoute(
                            modifier = Modifier.systemPadding(),
                            startRoute = if (appState.onboardingCompleted) Route.Main else Route.AppOnboarding,
                            mainTab = mainTab,
                        )
                    }
                }
            }
        }
    }
}
