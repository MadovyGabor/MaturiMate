package com.madovygabor.maturimate.features.appOnboarding.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.core.presentation.navigation.Route
import com.madovygabor.maturimate.core.presentation.utils.retryReads
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.features.appOnboarding.presentation.profileSetup.error.toUiText
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The onboarding screens from the welcome screen up to the first unanswered question, so the
 * onboarding continues where it stopped (after a restart, or when a new app language rebuilds
 * the screens) and Back still walks through the earlier answers.
 */
fun onboardingBackStack(profile: UserProfile?): List<Route> = buildList {
    add(Route.AppOnboarding.Welcome)
    if (profile?.teachingLanguage == null) return@buildList
    for (step in onboardingSteps(profile.role)) {
        add(step.route)
        if (!step.isAnswered(profile)) break
    }
}

/**
 * The first back stack of the onboarding, with the app language of the profile it was computed
 * from (null before a teaching language is saved, as in `AppState`).
 */
data class OnboardingResumeState(
    val backStack: List<Route>,
    val language: TeachingLanguage?,
    /** True when the profile could not be read: the welcome screen, whatever the language. */
    val fallback: Boolean = false,
) {
    /** Whether this stack belongs to the profile the app was built with, in [appLanguage]. */
    fun matches(appLanguage: TeachingLanguage?): Boolean = fallback || language == appLanguage
}

/**
 * Gives the onboarding navigation its first back stack from the saved answers, and finishes
 * the onboarding after the last step.
 */
@OptIn(ExperimentalTime::class)
class OnboardingNavigationViewModel(
    private val repository: UserProfileRepository,
    private val snackbarController: SnackbarController,
) : ViewModel() {

    /**
     * Null while the profile is being read; the welcome screen when it cannot be read. This
     * ViewModel outlives the rebuild after a new app language, so the screen checks [OnboardingResumeState.matches]
     * before it uses the stack: it may still come from the profile before the change.
     */
    val resume: StateFlow<OnboardingResumeState?> = repository.profile
        .map { profile ->
            OnboardingResumeState(
                backStack = onboardingBackStack(profile),
                language = profile?.uiLanguage?.takeIf { profile.teachingLanguage != null },
            )
        }
        .retryReads()
        .catch { emit(OnboardingResumeState(listOf(Route.AppOnboarding.Welcome), language = null, fallback = true)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The saved role, for the steps after the role question. */
    val role: StateFlow<UserRole?> = repository.profile
        .map { it?.role }
        .retryReads()
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Marks the onboarding as finished; the app then shows the home screen. A failed save says so. */
    fun finish() {
        viewModelScope.launch {
            val result = repository.completeOnboarding(Clock.System.now())
            if (result is OperationResult.Error) snackbarController.showSnackbar(message = result.error.toUiText())
        }
    }
}
