package com.madovygabor.maturimate.features.appOnboarding.presentation

import com.madovygabor.maturimate.core.presentation.navigation.rememberNavTransitions
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
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
import com.madovygabor.maturimate.features.appOnboarding.presentation.plan.PlanScreen
import com.madovygabor.maturimate.features.auth.domain.SignedInAccount
import com.madovygabor.maturimate.features.auth.presentation.AccountScreen
import com.madovygabor.maturimate.features.auth.presentation.SignInScreen
import com.madovygabor.maturimate.features.appOnboarding.presentation.reminder.ReminderScreen
import com.madovygabor.maturimate.features.appOnboarding.presentation.language.LanguageScreen
import com.madovygabor.maturimate.features.appOnboarding.presentation.role.RoleScreen
import com.madovygabor.maturimate.features.appOnboarding.presentation.subjects.SubjectsScreen
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.koin.compose.viewmodel.koinViewModel

/** The routes of the onboarding, for saving its back stack. */
private val onboardingSerializers = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(Route.AppOnboarding.Welcome::class, Route.AppOnboarding.Welcome.serializer())
            subclass(Route.AppOnboarding.Language::class, Route.AppOnboarding.Language.serializer())
            subclass(Route.AppOnboarding.Role::class, Route.AppOnboarding.Role.serializer())
            subclass(Route.AppOnboarding.Subjects::class, Route.AppOnboarding.Subjects.serializer())
            subclass(Route.AppOnboarding.Date::class, Route.AppOnboarding.Date.serializer())
            subclass(Route.AppOnboarding.Goal::class, Route.AppOnboarding.Goal.serializer())
            subclass(Route.AppOnboarding.Reminder::class, Route.AppOnboarding.Reminder.serializer())
            subclass(Route.AppOnboarding.Plan::class, Route.AppOnboarding.Plan.serializer())
            subclass(Route.AppOnboarding.Account::class, Route.AppOnboarding.Account.serializer())
            subclass(Route.AppOnboarding.SignIn::class, Route.AppOnboarding.SignIn.serializer())
        }
    }
}

/**
 * The back stack of the onboarding, kept by `App` above the rebuild that a new app language causes: the user stays where
 * he or she is, and Back still walks through the screens already seen. [seeded] tells that the stack was filled from the
 * saved answers once (after a restart the onboarding continues where it stopped).
 */
@Stable
class OnboardingNavState(val backStack: NavBackStack<NavKey>, seeded: MutableState<Boolean>) {
    var seeded: Boolean by seeded

    /** The saved role, once known: the headers' step count does not wait for it again after a rebuild. */
    var role: UserRole? by mutableStateOf(null)
}

@Composable
fun rememberOnboardingNavState(): OnboardingNavState {
    val backStack = rememberNavBackStack(configuration = onboardingSerializers, Route.AppOnboarding.Welcome)
    val seeded = rememberSaveable { mutableStateOf(false) }
    return remember(backStack) { OnboardingNavState(backStack, seeded) }
}

/**
 * Navigation graph for the App Onboarding flow.
 * Manages the transitions between the onboarding screens.
 *
 * The onboarding ends by saving its end (`OnboardingNavigationViewModel.finish`); the app then
 * shows the home screen by itself.
 */
@Composable
fun AppOnboardingNavigation(
    /** The language the app was built with (`AppState.language`). */
    appLanguage: TeachingLanguage?,
    /** The back stack, held by the caller above the rebuild after a new language. */
    navState: OnboardingNavState = rememberOnboardingNavState(),
    navigationViewModel: OnboardingNavigationViewModel = koinViewModel(),
    /** Called when the saved answers are read and the first screen is built: the splash screen can go. */
    onReady: () -> Unit = {},
) {
    // The first back stack comes from the saved answers, so the onboarding continues where it
    // stopped; after a new language it waits for the stack of that profile, not the one before.
    // Later rebuilds keep the stack the user has built.
    val resume by navigationViewModel.resume.collectAsState()
    val current = resume
    val onboardingBackStack = navState.backStack
    if (!navState.seeded) {
        if (current == null) {
            OnboardingLoading()
            return
        }
        val initial = current.takeIf { it.matches(appLanguage) }?.backStack
        if (initial == null) {
            OnboardingLoading()
            return
        }
        remember(initial) {
            onboardingBackStack.clear()
            onboardingBackStack.addAll(initial)
            navState.seeded = true
            true
        }
    }
    // The saved role, known with the first stack: the step count of the headers is right from the start. It is kept in
    // [navState], above the rebuild that a new app language causes, so after that rebuild the screen is built at once
    // and does not wait for the profile to be read again (on a slow phone that was a blank screen for seconds).
    current?.let { navState.role = it.role }
    val role = current?.role ?: navState.role
    LaunchedEffect(Unit) { onReady() }

    val transitions = rememberNavTransitions()
    NavDisplay(
        transitionSpec = transitions.forward(),
        popTransitionSpec = transitions.back(),
        predictivePopTransitionSpec = transitions.predictiveBack(),
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
                    onHaveAccount = { onboardingBackStack.add(Route.AppOnboarding.SignIn) },
                )
            }

            // the role saved on step 2, for the steps after it

            // the onboarding steps; the account step finishes the onboarding itself
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

            // 7. Daily reminder
            entry<Route.AppOnboarding.Reminder> {
                ReminderScreen(role = role, onBack = back, onNext = { goNext(OnboardingStep.REMINDER, role) })
            }

            // 8. Your plan summary
            entry<Route.AppOnboarding.Plan> {
                PlanScreen(role = role, onBack = back, onNext = { goNext(OnboardingStep.PLAN, role) })
            }

            // Sign in: an account that finished the onboarding goes home by itself (its
            // saved end rebuilds the app); otherwise the onboarding continues from its answers
            entry<Route.AppOnboarding.SignIn> {
                SignInScreen(
                    onBack = back,
                    // the answers given here are replaced by those of the account: ask first
                    confirmReplace = true,
                    onSignedIn = { account ->
                        stackAfterSignIn(account)?.let { resumed ->
                            onboardingBackStack.clear()
                            onboardingBackStack.addAll(resumed)
                        }
                    },
                )
            }

            // 9. Account creation or continue as guest; both finish the onboarding
            entry<Route.AppOnboarding.Account> {
                AccountScreen(role = role, onBack = back)
            }
        }
    )
}

/**
 * A plain screen, the color of the app, while the saved answers are read: the stack of the onboarding is not known
 * before. It has no spinner: it is on screen for a moment only (after a new app language), and a spinner flashing
 * there looked like a loading screen of its own.
 */
@Composable
private fun OnboardingLoading() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
}

/**
 * Where the sign-in screen goes next after signing in. Null for a set-up account: its saved onboarding end takes the app
 * home by itself. An account that is not set up (no profile, or one without the TERMS consent) must give the consents in
 * the account form; the device is still the guest's, so the guest's answers lead there: straight to the account step
 * when every question is answered, otherwise on from the first open question (the onboarding ends at the account step).
 */
internal fun stackAfterSignIn(account: SignedInAccount): List<Route>? {
    if (account.isSetUp) return null
    val profile = account.profile ?: return onboardingBackStack(null)
    val steps = onboardingSteps(profile.role)
    val questions = steps - OnboardingStep.REMINDER - OnboardingStep.PLAN - OnboardingStep.ACCOUNT
    if (profile.role == null || !questions.all { it.isAnswered(profile) }) return onboardingBackStack(profile)
    return listOf(Route.AppOnboarding.Welcome) + steps.map { it.route }
}
