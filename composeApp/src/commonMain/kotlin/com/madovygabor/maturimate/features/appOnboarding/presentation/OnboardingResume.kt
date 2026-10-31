package com.madovygabor.maturimate.features.appOnboarding.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madovygabor.maturimate.core.presentation.navigation.Route
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The onboarding screens from the welcome screen up to the first unanswered question, so the
 * onboarding continues where it stopped (after a restart, or when a new app language rebuilds
 * the screens) and Back still walks through the earlier answers.
 */
fun onboardingBackStack(profile: UserProfile?): List<Route> = buildList {
    add(Route.AppOnboarding.Welcome)
    if (profile?.teachingLanguage == null) return@buildList
    add(Route.AppOnboarding.Language)
    // the next questions replace the old setup screen step by step (roadmap 5.6 to 5.12)
    add(Route.AppOnboarding.ProfileSetup)
}

/** Gives the onboarding navigation its first back stack from the saved answers. */
class OnboardingNavigationViewModel(repository: UserProfileRepository) : ViewModel() {

    /** Null while the profile is being read. */
    val backStack: StateFlow<List<Route>?> = repository.profile
        .map(::onboardingBackStack)
        .catch { emit(listOf(Route.AppOnboarding.Welcome)) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
