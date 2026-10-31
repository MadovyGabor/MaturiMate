package com.madovygabor.maturimate.features.appOnboarding.presentation.profileSetup

import com.madovygabor.maturimate.core.presentation.utils.UiText
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import kotlinx.datetime.LocalDate

data class ProfileSetupUiState(
    val onErrorMessage: UiText? = null,
    val selectedLanguage: TeachingLanguage = TeachingLanguage.HU,
    val examDate: LocalDate? = null,
)
