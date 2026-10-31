package com.madovygabor.maturimate.features.appOnboarding.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madovygabor.maturimate.core.presentation.navigation.Route
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
        // the onboarding ends before a step that is not built yet
        add(step.route ?: break)
        if (!step.isAnswered(profile)) break
    }
}

/**
 * Gives the onboarding navigation its first back stack from the saved answers, and finishes
 * the onboarding after the last step.
 */
@OptIn(ExperimentalTime::class)
class OnboardingNavigationViewModel(private val repository: UserProfileRepository) : ViewModel() {

    /** Null while the profile is being read. */
    val backStack: StateFlow<List<Route>?> = repository.profile
        .map(::onboardingBackStack)
        .catch { emit(listOf(Route.AppOnboarding.Welcome)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The saved role, for the steps after the role question. */
    val role: StateFlow<UserRole?> = repository.profile
        .map { it?.role }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Marks the onboarding as finished; the app then shows the home screen. */
    fun finish() {
        viewModelScope.launch { repository.completeOnboarding(Clock.System.now()) }
    }
}
