package com.madovygabor.maturimate.features.appOnboarding.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madovygabor.maturimate.features.appOnboarding.data.UserPreferencesRepository
import com.madovygabor.maturimate.features.appOnboarding.presentation.profileSetup.ProfileSetupUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ViewModel for managing the state and logic of the onboarding process.
class OnboardingViewModel(
    private val repository: UserPreferencesRepository
) : ViewModel() {

    // UI state for the profile setup screen
    private val _profileSetupUiState = MutableStateFlow(ProfileSetupUiState())
    val profileSetupUiState = _profileSetupUiState.asStateFlow()


    init {
        // Initialization logic if needed
        println("OnboardingViewModel initialized")
    }

    //For debugging purposes
    override fun onCleared() {
        super.onCleared()
        println("OnboardingViewModel cleared")
    }

    // Handlers for user interactions
    fun onLanguageSelected(lang: String) {
        _profileSetupUiState.update { it.copy(selectedLanguage = lang) }
    }

    fun onLevelSelected(level: String) {
        _profileSetupUiState.update { it.copy(examLevel = level) }
    }

    fun onDateSelected(dateMillis: Long?) {
        _profileSetupUiState.update { it.copy(examDate = dateMillis) }
    }

    fun onVpuToggled(enabled: Boolean) {
        _profileSetupUiState.update { it.copy(isVpuEnabled = enabled) }
    }

    fun completeOnboarding(onSuccess: () -> Unit) {
        viewModelScope.launch {

            try {
                val currentState = _profileSetupUiState.value

               // Save onboarding preferences
                repository.saveOnboarding(
                    language = currentState.selectedLanguage,
                    level = currentState.examLevel,
                    isVpuEnabled = currentState.isVpuEnabled,
                    examDate = currentState.examDate ?: 0L
                )

                onSuccess()

            } catch (e: Exception) {
                // Handle error case
                _profileSetupUiState.update {
                    it.copy(
                        //TODO handle error properly
                        //errorMessage = "Hiba történt a mentés során. Próbáld újra!"
                    )
                }
            }
        }
    }
}