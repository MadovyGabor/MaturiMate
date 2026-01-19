package com.madovygabor.maturimate.core.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.madovygabor.maturimate.core.ui.navigation.Route
import com.madovygabor.maturimate.features.appOnboarding.InitialSetupScreen
import com.madovygabor.maturimate.features.examTaking.ExamWriteScreen
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic

// Root navigation composable that manages top-level navigation routes.
@Composable
fun NavigationRoute(){

    val rootBackStack = rememberNavBackStack(
        configuration = SavedStateConfiguration{
            serializersModule = SerializersModule {
                polymorphic(NavKey::class) {
                    // Root level routes
                    subclass(Route.Main::class, Route.Main.serializer())
                    subclass(Route.InitialSetup::class, Route.InitialSetup.serializer())
                    subclass(Route.ExamWrite::class, Route.ExamWrite.serializer())
                }
            }
        },

        Route.InitialSetup // TODO: Change to Route.InitialSetup to test the onboarding flow first
    )

    NavDisplay(
        backStack = rootBackStack,
        entryProvider = entryProvider {
            //The main application content with bottom navigation
            entry<Route.Main> {
                MainFrameNavigation()
            }

            //Initial Setup Screen for first-time users
            entry<Route.InitialSetup> {
                InitialSetupScreen(
                    onInitialSetupComplete = {
                        rootBackStack.add(Route.Main)
                    }
                )
            }
            //Fullscreen exam writing screen
            entry<Route.ExamWrite> {
                ExamWriteScreen()
            }
        }
    )
}