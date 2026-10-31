package com.madovygabor.maturimate.features.profileDetails.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.core.presentation.utils.SubjectStaticData
import com.madovygabor.maturimate.core.presentation.utils.UiText
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.appOnboarding.domain.MaturitaValidator
import com.madovygabor.maturimate.features.appOnboarding.presentation.profileSetup.error.toUiText
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * ViewModel responsible for managing the user's profile details and statistics.
 * It coordinates data flow between the [UserProfileRepository] and the UI,
 * implementing a draft-state pattern for safe data editing.
 *
 * @property repository The repository of the user's profile and settings.
 * @property validator Validator for domain-specific business rules (e.g., exam dates).
 * @property snackbarController Controller for displaying asynchronous UI notifications.
 */
class ProfileViewModel(
    private val repository: UserProfileRepository,
    private val validator: MaturitaValidator,
    private val snackbarController: SnackbarController,
) : ViewModel() {

    private val _profileUiState = MutableStateFlow(ProfileUiState())

    /** Reactive stream of the current [ProfileUiState] observed by the UI layer. */
    val profileUiState = _profileUiState.asStateFlow()

    init {
        observeProfileData()
        loadMockSubjectStats()
    }

    /**
     * Collects the user's profile from the repository and synchronizes both
     * real and draft values in the UI state.
     */
    private fun observeProfileData() {
        viewModelScope.launch {
            repository.profile.filterNotNull().collect { profile ->
                val language = profile.teachingLanguage ?: TeachingLanguage.HU
                _profileUiState.update { it.copy(
                    maturitaDate = profile.graduationDate,
                    draftMaturitaDate = profile.graduationDate,
                    maturitaLanguage = language,
                    draftMaturitaLanguage = language,
                    daysLeft = calculateDaysLeft(profile.graduationDate)
                ) }
            }
        }
    }

    /**
     * Updates the current [EditType] to trigger the visibility of specific edit dialogs.
     * @param goal The type of information the user intendeds to edit.
     */
    fun onEditing(goal: EditType) {
        _profileUiState.update { it.copy(editType = goal) }
    }

    /** Resets the [EditType] to [EditType.NONE], effectively closing any active dialogs. */
    fun dismissDialog() {
        _profileUiState.update { it.copy(editType = EditType.NONE) }
    }

    /** Updates the temporary draft date without affecting the persisted data. */
    fun onDraftDateChanged(date: LocalDate?) {
        _profileUiState.update { it.copy(draftMaturitaDate = date) }
    }

    /** Updates the temporary draft language/tagozat. */
    fun onDraftLanguageChanged(lang: TeachingLanguage) {
        _profileUiState.update { it.copy(draftMaturitaLanguage = lang) }
    }

    /**
     * Performs business logic validation and saves the change to the user database.
     * Uses selective saving logic to only update the data relevant to the current [EditType].
     */
    fun saveChanges() {
        val currentState = _profileUiState.value
        val editType = currentState.editType

        // 1. Context-specific validation
        if (editType == EditType.DATE) {
            val validation = validator.validateDate(currentState.draftMaturitaDate)
            if (validation is OperationResult.Error) {
                showError(validation.error.toUiText())
                return
            }
        }

        // 2. Save only the field of the open dialog
        viewModelScope.launch {
            val saveResult = when (editType) {
                EditType.DATE -> repository.saveGraduationDate(currentState.draftMaturitaDate!!)
                EditType.LANGUAGE -> repository.saveTeachingLanguage(currentState.draftMaturitaLanguage)
                EditType.NONE -> return@launch
            }

            when (saveResult) {
                is OperationResult.Success -> {
                    // Reset edit type to close dialog on success
                    _profileUiState.update { it.copy(editType = EditType.NONE) }
                }
                is OperationResult.Error -> {
                    showError(saveResult.error.toUiText())
                }
            }
        }
    }

    /**
     * Calculates the remaining days from today until the given date.
     * Returns 0 if the date has already passed.
     */
    @OptIn(ExperimentalTime::class)
    private fun calculateDaysLeft(date: LocalDate?): Int {
        if (date == null) return 0
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        return today.daysUntil(date).coerceAtLeast(0)
    }

    /** Triggers a snackbar notification with the provided [message]. */
    private fun showError(message: UiText) {
        viewModelScope.launch {
            snackbarController.showSnackbar(message = message)
        }
    }

    /** Initializes mock statistics for UI preview and initial development phases. */
    private fun loadMockSubjectStats() {
        val mockStats = listOf(
            SubjectStat(SubjectStaticData.getById("ENGLISH"), 0.75f, "85%"),
            SubjectStat(SubjectStaticData.getById("MATH"), 0.50f, "60%"),
            SubjectStat(SubjectStaticData.getById("HUNGARIAN"), 0.90f, "92%"),
            SubjectStat(SubjectStaticData.getById("SLOVAK"), 0.30f, "45%")
        )
        _profileUiState.update { it.copy(subjectStats = mockStats) }
    }
}