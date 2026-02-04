package com.madovygabor.maturimate.features.appOnboarding.presentation

import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.features.appOnboarding.domain.UserPreferencesRepository
import com.madovygabor.maturimate.features.appOnboarding.domain.MaturitaLanguage
import com.madovygabor.maturimate.features.appOnboarding.domain.MaturitaLevel
import com.madovygabor.maturimate.features.appOnboarding.domain.MaturitaValidator
import com.madovygabor.maturimate.features.appOnboarding.presentation.profileSetup.ProfileSetupUiState
import com.madovygabor.maturimate.features.appOnboarding.presentation.profileSetup.error.toUiText
import com.madovygabor.maturimate.features.subjectsAndExams.domain.repository.ExamRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import maturimate.composeapp.generated.resources.Res
import org.koin.compose.koinInject

/**
 * ViewModel responsible for managing the user's onboarding progression.
 * Orchestrates data validation, persistence, and UI feedback via Snackbars.
 * @param repository Repository for persisting user preferences.
 * @param maturitaValidator Validator for onboarding input data.
 * @param snackbarController Controller to show snackbar messages.
 */
class OnboardingViewModel(
    private val repository: UserPreferencesRepository,
    private val maturitaValidator: MaturitaValidator,
    private val snackbarController: SnackbarController,
    private val examRepository: ExamRepository,
) : ViewModel() {


    init {
        syncExams()
    }

    // Internal UI state management
    private val _profileSetupUiState = MutableStateFlow(ProfileSetupUiState())
    val profileSetupUiState = _profileSetupUiState.asStateFlow()

    fun syncExams() {
        viewModelScope.launch {

            try {
                // 1. Beolvassuk a nyers bájtokat a Resources mappából
                val bytes = Res.readBytes("files/HU_MJL_2025.json")
                val jsonString = bytes.decodeToString()

                // 2. Átadjuk a repository-nak szinkronizálásra
                val result = examRepository.syncExams(jsonString)

                // 3. Ellenőrizzük az eredményt a te OperationResult-oddal
                when(result) {
                    is OperationResult.Success -> println("[FILE]✅ Siker! Az adatbázis feltöltve.")
                    is OperationResult.Error -> println("[FILE]❌ Hiba történt: ${result.error}")
                }
            } catch (e: Exception) {
                println("[FILE] Nem sikerült beolvasni a fájlt: ${e.message}")
            }
        }
    }

    /**
     * Updates the selected language in the UI state.
     */
    fun onLanguageSelected(lang: MaturitaLanguage) {
        _profileSetupUiState.update { it.copy(selectedLanguage = lang) }
    }

    /**
     * Updates the target exam level (e.g., B1, B2) in the UI state.
     */
    fun onLevelSelected(level: MaturitaLevel) {
        _profileSetupUiState.update { it.copy(examLevel = level) }
    }

    /**
     * Updates the selected graduation date in milliseconds.
     */
    fun onDateSelected(dateMillis: LocalDate?) {
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
        val dateValidationResult = maturitaValidator.validateDate(currentState.examDate)

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
                    val saveResult = repository.saveUserPreferences(
                        maturitaLanguage = currentState.selectedLanguage,
                        maturitaLevel = currentState.examLevel,
                        isVpuEnabled = currentState.isVpuEnabled,
                        maturitaDate = currentState.examDate ?: LocalDate(2026, 1, 1)
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