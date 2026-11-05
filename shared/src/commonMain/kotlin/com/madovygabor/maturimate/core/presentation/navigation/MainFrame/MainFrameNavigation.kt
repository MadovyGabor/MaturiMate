package com.madovygabor.maturimate.core.presentation.navigation.MainFrame

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.madovygabor.maturimate.core.presentation.MainFrame
import com.madovygabor.maturimate.core.presentation.navigation.Route
import com.madovygabor.maturimate.features.practice.presentation.PracticeScreen
import com.madovygabor.maturimate.features.profileDetails.presentation.ProfileNavigation
import com.madovygabor.maturimate.features.learning.presentation.HomeScreen
import com.madovygabor.maturimate.features.learning.presentation.LearningDeepLink
import com.madovygabor.maturimate.features.learning.presentation.LearningNavigation
import org.koin.compose.viewmodel.koinViewModel

// MainFrameNavigation composable that sets up the main navigation structure of the app.
@Composable
fun MainFrameNavigation(
    mainTab: MutableState<NavKey>,
    /** The page the profile tab opens on, kept over a rebuild like [mainTab]. */
    profilePage: MutableState<NavKey>,
    modifier: Modifier = Modifier,
    /** Opens a lesson over the whole screen. */
    onOpenLesson: (Long) -> Unit = {},
    /** Opens a test over the whole screen. */
    onOpenTest: (Long) -> Unit = {},
    /** Opens the practice picker, full screen. */
    onOpenPracticePicker: () -> Unit = {},
) {

    // Remember the navigation state with the starting route and top-level routes
    val navigationState = rememberNavigationState(
        startRoute = Route.Main.Home,
        topLevelRoutes = TOP_LEVEL_NAVBAR_DESTINATIONS.keys
    )
    // reopen the tab that was open before a rebuild (a language change in the profile); read once,
    // so the state and the navigator below stay the same objects
    remember(navigationState) { navigationState.topLevelRoute = mainTab.value }
    LaunchedEffect(navigationState.topLevelRoute) { mainTab.value = navigationState.topLevelRoute }

    // Where the "Folytatás" button of the home screen leads: the subjects tab opens with the subject, the part and
    // the topics on the way already in its back stack, as if the student had walked there; back goes up one level
    val deepLink = remember { mutableStateOf<LearningDeepLink?>(null) }

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
                        // Home: what to do today
                        entry<Route.Main.Home> {
                            HomeScreen(onContinue = { target ->
                                deepLink.value = LearningDeepLink(target.subjectId, target.topicIds)
                                navigator.navigate(Route.Main.Subject)
                            })
                        }
                        // Subject feature navigation
                        entry<Route.Main.Subject> {
                            LearningNavigation(
                                onOpenLesson = onOpenLesson,
                                onOpenTest = onOpenTest,
                                deepLink = deepLink.value,
                                onDeepLinkHandled = { deepLink.value = null },
                            )
                        }
                        // Practice: the free practice, and what comes next (Milestone 9a)
                        entry<Route.Main.Practice> {
                            PracticeScreen(onOpenPicker = onOpenPracticePicker)
                        }
                        // Profile feature navigation: the profile, the full history, the settings
                        entry<Route.Main.Profile> {
                            ProfileNavigation(openPage = profilePage)
                        }
                    }
                )
            )
        }
    )
}