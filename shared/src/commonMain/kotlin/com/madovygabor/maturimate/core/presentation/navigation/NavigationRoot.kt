package com.madovygabor.maturimate.core.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.runtime.MutableState
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.madovygabor.maturimate.core.presentation.navigation.MainFrame.MainFrameNavigation
import com.madovygabor.maturimate.features.appOnboarding.presentation.AppOnboardingNavigation
import com.madovygabor.maturimate.features.examTaking.ExamWriteScreen
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

// Root navigation composable that manages top-level navigation routes.
@Composable
fun NavigationRoute(
    modifier: Modifier,
    startRoute: Route,
    /** The tab the main screen opens on; it follows the user's choice. */
    mainTab: MutableState<NavKey>,
){

    val rootBackStack = rememberNavBackStack(
        configuration = SavedStateConfiguration{
            serializersModule = SerializersModule {
                polymorphic(NavKey::class) {
                    // Root level routes
                    subclass(Route.Main::class, Route.Main.serializer())
                    subclass(Route.AppOnboarding::class, Route.AppOnboarding.serializer())
                    subclass(Route.ExamWrite::class, Route.ExamWrite.serializer())
                }
            }
        },

        startRoute,
    )

    NavDisplay(
        modifier = modifier,
        backStack = rootBackStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            //The main application content with bottom navigation
            entry<Route.Main> {
                MainFrameNavigation(mainTab = mainTab)
            }

            //Initial Setup Screen for first-time users
            entry<Route.AppOnboarding> {
                // finishing it saves the onboarding end; App then starts on Main
                AppOnboardingNavigation()
            }
            //Fullscreen exam writing screen
            entry<Route.ExamWrite> {
                ExamWriteScreen()
            }
        }
    )
}