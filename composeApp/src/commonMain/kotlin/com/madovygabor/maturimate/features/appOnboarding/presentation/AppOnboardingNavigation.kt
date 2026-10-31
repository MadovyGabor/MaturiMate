package com.madovygabor.maturimate.features.appOnboarding.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.madovygabor.maturimate.core.presentation.navigation.Route
import com.madovygabor.maturimate.features.appOnboarding.presentation.language.LanguageScreen
import com.madovygabor.maturimate.features.appOnboarding.presentation.profileSetup.ProfileSetupScreen
import com.madovygabor.maturimate.features.appOnboarding.presentation.role.RoleScreen
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.koin.compose.viewmodel.koinViewModel

/**
 * Navigation graph for the App Onboarding flow.
 * Manages the transitions between the onboarding screens.
 *
 * @param onAppOnboardingComplete Callback triggered when the entire onboarding is finished.
 */
@Composable
fun AppOnboardingNavigation(
    onAppOnboardingComplete: () -> Unit,
    navigationViewModel: OnboardingNavigationViewModel = koinViewModel(),
) {
    // The first back stack comes from the saved answers, so the onboarding continues where it stopped
    val startStack by navigationViewModel.backStack.collectAsState()
    val initialStack = startStack ?: return

    // Initialize the backstack with serialization support for Type-Safe routing
    val onboardingBackStack = rememberNavBackStack(
        configuration = SavedStateConfiguration {
            serializersModule = SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(Route.AppOnboarding.Welcome::class, Route.AppOnboarding.Welcome.serializer())
                    subclass(Route.AppOnboarding.Language::class, Route.AppOnboarding.Language.serializer())
                    subclass(Route.AppOnboarding.Role::class, Route.AppOnboarding.Role.serializer())
                    subclass(Route.AppOnboarding.ProfileSetup::class, Route.AppOnboarding.ProfileSetup.serializer())
                }
            }
        },
        *initialStack.toTypedArray(),
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
                    onStart = {
                        onboardingBackStack.add(Route.AppOnboarding.Language)
                    },
                    // hidden until the sign-in screen exists (roadmap step 5.12)
                    onHaveAccount = null,
                )
            }

            // 2. Teaching language (step 1)
            entry<Route.AppOnboarding.Language> {
                LanguageScreen(
                    onBack = { onboardingBackStack.removeLastOrNull() },
                    onNext = { onboardingBackStack.add(Route.AppOnboarding.Role) },
                )
            }

            // 3. Role (step 2); learners and teachers finish here for now (RoleViewModel)
            entry<Route.AppOnboarding.Role> {
                RoleScreen(
                    onBack = { onboardingBackStack.removeLastOrNull() },
                    // the old setup screen asks the student's remaining questions until the new steps replace it
                    onStudentNext = { onboardingBackStack.add(Route.AppOnboarding.ProfileSetup) },
                )
            }

            // 4. Profile Setup Screen (old; replaced step by step)
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