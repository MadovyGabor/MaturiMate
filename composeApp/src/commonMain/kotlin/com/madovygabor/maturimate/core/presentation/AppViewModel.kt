package com.madovygabor.maturimate.core.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What the whole app depends on: the language of its texts and where it starts. */
data class AppState(
    /** Null until the user picks a teaching language: the phone's language is used. */
    val language: TeachingLanguage?,
    val onboardingCompleted: Boolean,
)

/** Follows the saved profile, so a new language or a finished onboarding takes effect at once. */
class AppViewModel(repository: UserProfileRepository) : ViewModel() {

    /** Null while the profile is being read for the first time. */
    val state: StateFlow<AppState?> = repository.profile
        .map { profile ->
            AppState(
                language = profile?.uiLanguage?.takeIf { profile.teachingLanguage != null },
                onboardingCompleted = profile?.isOnboardingCompleted == true,
            )
        }
        // an unreadable profile starts the onboarding in the phone's language
        .catch { emit(AppState(language = null, onboardingCompleted = false)) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
