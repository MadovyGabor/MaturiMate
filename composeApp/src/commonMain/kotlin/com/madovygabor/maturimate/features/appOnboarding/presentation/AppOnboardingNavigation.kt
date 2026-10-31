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
import com.madovygabor.maturimate.features.appOnboarding.presentation.date.DateScreen
import com.madovygabor.maturimate.features.appOnboarding.presentation.goal.GoalScreen
import com.madovygabor.maturimate.features.appOnboarding.presentation.language.LanguageScreen
import com.madovygabor.maturimate.features.appOnboarding.presentation.role.RoleScreen
import com.madovygabor.maturimate.features.appOnboarding.presentation.subjects.SubjectsScreen
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.koin.compose.viewmodel.koinViewModel

/**
 * Navigation graph for the App Onboarding flow.
 * Manages the transitions between the onboarding screens.
 *
 * The onboarding ends by saving its end (`OnboardingNavigationViewModel.finish`); the app then
 * shows the home screen by itself.
 */
@Composable
fun AppOnboardingNavigation(
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
                    subclass(Route.AppOnboarding.Subjects::class, Route.AppOnboarding.Subjects.serializer())
                    subclass(Route.AppOnboarding.Date::class, Route.AppOnboarding.Date.serializer())
                    subclass(Route.AppOnboarding.Goal::class, Route.AppOnboarding.Goal.serializer())
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

            // the role saved on step 2, for the steps after it
            val role = navigationViewModel.role.value

            // the onboarding steps; after the last one built so far the onboarding ends
            fun goNext(step: OnboardingStep, role: UserRole?) {
                val next = nextRoute(step, role)
                if (next == null) navigationViewModel.finish() else onboardingBackStack.add(next)
            }
            val back: () -> Unit = { onboardingBackStack.removeLastOrNull() }

            // 2. Teaching language (step 1)
            entry<Route.AppOnboarding.Language> {
                LanguageScreen(onBack = back, onNext = { role -> goNext(OnboardingStep.LANGUAGE, role) })
            }

            // 3. Role (step 2)
            entry<Route.AppOnboarding.Role> {
                RoleScreen(onBack = back, onNext = { role -> goNext(OnboardingStep.ROLE, role) })
            }

            // 4. Subjects and the English level (step 3)
            entry<Route.AppOnboarding.Subjects> {
                SubjectsScreen(onBack = back, onNext = { role -> goNext(OnboardingStep.SUBJECTS, role) })
            }

            // 5. Exam date (step 4, students only)
            entry<Route.AppOnboarding.Date> {
                DateScreen(onBack = back, onNext = { goNext(OnboardingStep.DATE, UserRole.STUDENT) })
            }

            // 6. Daily goal (students and learners)
            entry<Route.AppOnboarding.Goal> {
                GoalScreen(role = role, onBack = back, onNext = { goNext(OnboardingStep.GOAL, role) })
            }
        }
    )
}