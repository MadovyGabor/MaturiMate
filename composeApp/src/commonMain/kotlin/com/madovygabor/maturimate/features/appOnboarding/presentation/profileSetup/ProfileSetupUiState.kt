package com.madovygabor.maturimate.features.appOnboarding.presentation.profileSetup

import com.madovygabor.maturimate.core.presentation.utils.UiText
import com.madovygabor.maturimate.features.appOnboarding.domain.MaturitaLanguage
import com.madovygabor.maturimate.features.appOnboarding.domain.MaturitaLevel
import kotlinx.datetime.LocalDate

data class ProfileSetupUiState(
    val onErrorMessage: UiText? = null,
    val selectedLanguage: MaturitaLanguage = MaturitaLanguage.HU,
    val examLevel: MaturitaLevel = MaturitaLevel.B2,
    val examDate: LocalDate? = null,
    val isVpuEnabled: Boolean = false,
)