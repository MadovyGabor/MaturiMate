package com.madovygabor.maturimate.features.appOnboarding.presentation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.madovygabor.maturimate.core.presentation.navigation.Route
import com.madovygabor.maturimate.features.appOnboarding.presentation.profileSetup.ProfileSetupScreen
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.koin.compose.viewmodel.koinViewModel

/**
 * Navigation graph for the App Onboarding flow.
 * Manages the transition between Welcome and Profile Setup screens.
 *
 * @param onAppOnboardingComplete Callback triggered when the entire onboarding is finished.
 */
@Composable
fun AppOnboardingNavigation(
    onAppOnboardingComplete: () -> Unit
) {
    // Initialize the backstack with serialization support for Type-Safe routing
    val onboardingBackStack = rememberNavBackStack(
        configuration = SavedStateConfiguration {
            serializersModule = SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(Route.AppOnboarding.Welcome::class, Route.AppOnboarding.Welcome.serializer())
                    subclass(Route.AppOnboarding.ProfileSetup::class, Route.AppOnboarding.ProfileSetup.serializer())
                }
            }
        },
        // Starting destination
        Route.AppOnboarding.Welcome
    )

    NavDisplay(
        backStack = onboardingBackStack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator()
        ),
        entryProvider = entryProvider {
            // Shared ViewModel for the entire onboarding flow, scoped to the backstack
            val onboardingViewModel = koinViewModel<OnboardingViewModel>()

            // 1. Welcome Screen
            entry<Route.AppOnboarding.Welcome> {
                WelcomeScreen(
                    onNavigateToProfileSetup = {
                        onboardingBackStack.add(Route.AppOnboarding.ProfileSetup)
                    }
                )
            }

            // 2. Profile Setup Screen
            entry<Route.AppOnboarding.ProfileSetup> {
                ProfileSetupScreen(
                    viewModel = onboardingViewModel,
                    onProfileSetupComplete = {
                        onAppOnboardingComplete()
                    }
                )
            }
        }
    )
}