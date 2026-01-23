package com.madovygabor.maturimate.features.appOnboarding.presentation.profileSetup

data class ProfileSetupUiState(
    val selectedLanguage: String = "HU",
    val examLevel: String = "B1",
    val examDate: Long? = null,
    val isVpuEnabled: Boolean = false
)