package com.madovygabor.maturimate.features.appOnboarding.presentation.profileSetup

import com.madovygabor.maturimate.core.presentation.utils.UiText

data class ProfileSetupUiState(
    val onErrorMessage: UiText? = null,
    val selectedLanguage: String = "HU",
    val examLevel: String = "B1",
    val examDate: Long? = null,
    val isVpuEnabled: Boolean = false
)