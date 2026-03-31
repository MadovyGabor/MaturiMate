package com.madovygabor.maturimate.features.subjectsAndExams.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madovygabor.maturimate.core.presentation.utils.SubjectStaticData
import com.madovygabor.maturimate.features.appOnboarding.domain.UserPreferencesRepository
import com.madovygabor.maturimate.features.subjectsAndExams.domain.repository.ExamRepository
import com.madovygabor.maturimate.features.subjectsAndExams.presentation.subjectSelection.SubjectSelectionUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SubjectViewModel(
    private val examRepository: ExamRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SubjectSelectionUiState())
    val uiState = _uiState.asStateFlow()

    init {
        // 1. Statikus tantárgyak betöltése
        _uiState.update { it.copy(subjects = SubjectStaticData.getAll()) }

        // 2. A 'preferences' property (Flow) figyelése
        viewModelScope.launch {
            userPreferencesRepository.preferences.collect { prefs ->
                _uiState.update { it.copy(userPreferences = prefs) }
            }
        }
    }

    /**
     * Betölti a vizsgákat az adatbázisból a UI-ban kiválasztott tantárgy alapján.
     */
    fun loadExamsForUiSubject(uiId: String) {
        val prefs = _uiState.value.userPreferences ?: return

        // Itt dől el a tagozat/szint alapján a DB kód (ENG_B2, SJSL, stb.)
        val actualDbId = SubjectStaticData.getDbId(uiId, prefs) ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                examRepository.getExamsBySubject(actualDbId).collect { exams ->
                    _uiState.update { it.copy(
                        filteredExams = exams,
                        isLoading = false
                    ) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isLoading = false,
                    error = "Nem sikerült betölteni a vizsgákat: ${e.message}"
                ) }
            }
        }
    }
}