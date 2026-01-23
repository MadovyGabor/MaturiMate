package com.madovygabor.maturimate.features.appOnboarding.presentation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.madovygabor.maturimate.core.ui.navigation.Route
import com.madovygabor.maturimate.features.appOnboarding.presentation.profileSetup.ProfileSetupScreen
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AppOnboardingNavigation(
    onAppOnboardingComplete: () -> Unit
) {

    val onboardingBackStack = rememberNavBackStack(
        configuration = SavedStateConfiguration{
            serializersModule = SerializersModule {
                polymorphic(NavKey::class) {
                    // Root level routes
                    subclass(Route.AppOnboarding.Welcome::class, Route.AppOnboarding.Welcome.serializer())
                    subclass(Route.AppOnboarding.ProfileSetup::class, Route.AppOnboarding.ProfileSetup.serializer())
                }
            }
        },

        Route.AppOnboarding.Welcome, // TODO: Change to Route.InitialSetup to test the onboarding flow first
    )

    NavDisplay(
        //modifier = modifier,
        backStack = onboardingBackStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {

            val onBoardingViewModel = koinViewModel<OnboardingViewModel>()

            // Welcome Screen for first-time users
            entry<Route.AppOnboarding.Welcome> {

                WelcomeScreen(
                    onNavigateToProfileSetup = {
                        onboardingBackStack.add(Route.AppOnboarding.ProfileSetup)
                    }
                )
            }

            // Profile Setup Screen
            entry<Route.AppOnboarding.ProfileSetup> {

                ProfileSetupScreen(
                    viewModel = onBoardingViewModel,
                    onProfileSetupComplete = {
                        onAppOnboardingComplete()
                    }
                )

            }

        }
    )
}