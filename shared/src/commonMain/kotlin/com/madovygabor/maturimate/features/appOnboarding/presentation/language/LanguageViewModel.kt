package com.madovygabor.maturimate.features.appOnboarding.presentation.language

import androidx.lifecycle.viewModelScope
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingQuestionViewModel
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The teaching language question. Saving it also sets the app language, which rebuilds the
 * screens; the onboarding then continues from the saved answers.
 */
class LanguageViewModel(
    repository: UserProfileRepository,
    snackbarController: SnackbarController,
) : OnboardingQuestionViewModel<TeachingLanguage>(
    snackbarController,
    load = { repository.profile.first()?.teachingLanguage },
    save = repository::saveTeachingLanguage,
) {
    /** The role saved before, if the user came back here; it sets the number of steps. */
    val role: StateFlow<UserRole?> = repository.profile
        .map { it?.role }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
