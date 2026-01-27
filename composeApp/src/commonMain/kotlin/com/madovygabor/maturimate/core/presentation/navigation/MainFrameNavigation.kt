package com.madovygabor.maturimate.core.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.madovygabor.maturimate.core.presentation.MainFrame
import com.madovygabor.maturimate.features.subjects.presentation.SubjectNavigation

// MainFrameNavigation composable that sets up the main navigation structure of the app.
@Composable
fun MainFrameNavigation(
    modifier: Modifier = Modifier
) {

    // Remember the navigation state with the starting route and top-level routes
    val navigationState = rememberNavigationState(
        startRoute = Route.Main.Subject,
        topLevelRoutes = TOP_LEVEL_NAVBAR_DESTINATIONS.keys
    )
    // Create a navigator to handle navigation actions
    val navigator = remember {
        MainFrameNavigator(navigationState)
    }

    // MainFrame composable that provides the main layout with navigation
    MainFrame(
        selectedKey = navigationState.topLevelRoute,
        onSelectedKey = {
            navigator.navigate(it)
        },
        content = {
            // Display the current navigation entries based on the navigation state
            NavDisplay(
                modifier = Modifier
                    .fillMaxSize(),
                onBack = navigator::goBack,
                entries = navigationState.toEntries(
                    entryProvider {
                        //Entries for the main bottom navigation destinations per feature
                        // Subject feature navigation
                        entry<Route.Main.Subject> {
                            SubjectNavigation()
                        }
                        // History screen placeholder
                        entry<Route.Main.History> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "History Screen")
                            }
                        }
                        // Profile feature navigation
                        entry<Route.Main.Profile> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "Profile Screen")
                            }
                        }
                    }
                )
            )
        }
    )
}