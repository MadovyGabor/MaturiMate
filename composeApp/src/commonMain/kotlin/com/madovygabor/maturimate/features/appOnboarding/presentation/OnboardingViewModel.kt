package com.madovygabor.maturimate.features.appOnboarding.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.features.appOnboarding.domain.MaturitaValidator
import com.madovygabor.maturimate.features.appOnboarding.presentation.profileSetup.ProfileSetupUiState
import com.madovygabor.maturimate.features.appOnboarding.presentation.profileSetup.error.toUiText
import com.madovygabor.maturimate.features.userProfile.domain.DataError
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * ViewModel responsible for managing the user's onboarding progression.
 * Orchestrates data validation, persistence, and UI feedback via Snackbars.
 * @param repository Repository of the user's profile and settings.
 * @param maturitaValidator Validator for onboarding input data.
 * @param snackbarController Controller to show snackbar messages.
 */
class OnboardingViewModel(
    private val repository: UserProfileRepository,
    private val maturitaValidator: MaturitaValidator,
    private val snackbarController: SnackbarController,
) : ViewModel() {

    // Internal UI state management
    private val _profileSetupUiState = MutableStateFlow(ProfileSetupUiState())
    val profileSetupUiState = _profileSetupUiState.asStateFlow()

    init {
        // start from the answers saved on the new screens (the teaching language from step 1)
        viewModelScope.launch {
            val saved = runCatching { repository.profile.first() }.getOrNull() ?: return@launch
            _profileSetupUiState.update { state ->
                state.copy(
                    selectedLanguage = saved.teachingLanguage ?: state.selectedLanguage,
                    examDate = state.examDate ?: saved.graduationDate,
                )
            }
        }
    }

    /**
     * Updates the selected language in the UI state.
     */
    fun onLanguageSelected(lang: TeachingLanguage) {
        _profileSetupUiState.update { it.copy(selectedLanguage = lang) }
    }

    /**
     * Updates the selected graduation date.
     */
    fun onDateSelected(date: LocalDate?) {
        _profileSetupUiState.update { it.copy(examDate = date) }
    }

    /**
     * Orchestrates the completion of the onboarding process.
     * Validates inputs and saves them to the user database.
     * @param onSuccess Callback triggered after successful data persistence.
     */
    @OptIn(ExperimentalTime::class)
    fun completeOnboarding(onSuccess: () -> Unit) {
        val currentState = _profileSetupUiState.value

        // 1. Validate inputs (currently focusing on the graduation date)
        when (val dateValidationResult = maturitaValidator.validateDate(currentState.examDate)) {
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
                    // 2. Save the answers, then mark the onboarding as finished
                    val saveResult = firstError(
                        { repository.saveTeachingLanguage(currentState.selectedLanguage) },
                        { repository.saveGraduationDate(currentState.examDate!!) },
                        { repository.completeOnboarding(Clock.System.now()) },
                    )

                    // 3. Handle persistence result
                    when (saveResult) {
                        null -> onSuccess()
                        // Show storage-related errors (e.g., IO error, disk full)
                        else -> snackbarController.showSnackbar(message = saveResult.toUiText())
                    }
                }
            }
        }
    }

    /** Runs the saves in order and stops at the first failure; null when all succeeded. */
    private suspend fun firstError(vararg saves: suspend () -> OperationResult<Unit, DataError>): DataError? {
        for (save in saves) {
            val result = save()
            if (result is OperationResult.Error) return result.error
        }
        return null
    }
}
