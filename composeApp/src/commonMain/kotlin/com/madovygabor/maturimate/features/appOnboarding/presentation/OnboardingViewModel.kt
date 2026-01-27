package com.madovygabor.maturimate.features.appOnboarding.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madovygabor.maturimate.core.error.OperationResult
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.features.appOnboarding.data.UserPreferencesRepository
import com.madovygabor.maturimate.features.appOnboarding.domain.OnBoardingDataValidator
import com.madovygabor.maturimate.features.appOnboarding.presentation.profileSetup.ProfileSetupUiState
import com.madovygabor.maturimate.features.appOnboarding.presentation.profileSetup.error.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel responsible for managing the user's onboarding progression.
 * Orchestrates data validation, persistence, and UI feedback via Snackbars.
 * @param repository Repository for persisting user preferences.
 * @param onBoardingDataValidator Validator for onboarding input data.
 * @param snackbarController Controller to show snackbar messages.
 */
class OnboardingViewModel(
    private val repository: UserPreferencesRepository,
    private val onBoardingDataValidator: OnBoardingDataValidator,
    private val snackbarController: SnackbarController,
) : ViewModel() {

    // Internal UI state management
    private val _profileSetupUiState = MutableStateFlow(ProfileSetupUiState())
    val profileSetupUiState = _profileSetupUiState.asStateFlow()

    /**
     * Updates the selected language in the UI state.
     */
    fun onLanguageSelected(lang: String) {
        _profileSetupUiState.update { it.copy(selectedLanguage = lang) }
    }

    /**
     * Updates the target exam level (e.g., B1, B2) in the UI state.
     */
    fun onLevelSelected(level: String) {
        _profileSetupUiState.update { it.copy(examLevel = level) }
    }

    /**
     * Updates the selected graduation date in milliseconds.
     */
    fun onDateSelected(dateMillis: Long?) {
        _profileSetupUiState.update { it.copy(examDate = dateMillis) }
    }

    /**
     * Toggles the VPUS (Special Educational Needs) support status.
     */
    fun onVpuToggled(enabled: Boolean) {
        _profileSetupUiState.update { it.copy(isVpuEnabled = enabled) }
    }

    /**
     * Orchestrates the completion of the onboarding process.
     * Validates inputs and persists data to local storage.
     * * @param onSuccess Callback triggered after successful data persistence.
     */
    fun completeOnboarding(onSuccess: () -> Unit) {
        val currentState = _profileSetupUiState.value

        // 1. Validate inputs (currently focusing on the graduation date)
        val dateValidationResult = onBoardingDataValidator.validateMaturitaDate(currentState.examDate)

        when (dateValidationResult) {
            is OperationResult.Error -> {
                // Handle validation failure by showing a localized error message
                viewModelScope.launch {
                    snackbarController.showSnackbar(
                        message = dateValidationResult.error.toUiText()
                    )
                }
            }
            is OperationResult.Success -> {
                // Clear any previous error messages and proceed to save
                _profileSetupUiState.update { it.copy(onErrorMessage = null) }

                viewModelScope.launch {
                    // 2. Persist onboarding data to DataStore
                    val saveResult = repository.saveOnboarding(
                        language = currentState.selectedLanguage,
                        level = currentState.examLevel,
                        isVpuEnabled = currentState.isVpuEnabled,
                        examDate = currentState.examDate ?: 0L
                    )

                    // 3. Handle persistence result
                    when (saveResult) {
                        is OperationResult.Success -> {
                            onSuccess()
                        }
                        is OperationResult.Error -> {
                            // Show storage-related errors (e.g., IO error, disk full)
                            snackbarController.showSnackbar(
                                message = saveResult.error.toUiText()
                            )
                        }
                    }
                }
            }
        }
    }
}