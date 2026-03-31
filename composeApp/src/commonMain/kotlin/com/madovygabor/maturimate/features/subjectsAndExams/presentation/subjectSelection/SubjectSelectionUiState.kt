package com.madovygabor.maturimate.features.subjectsAndExams.presentation.subjectSelection

import com.madovygabor.maturimate.core.presentation.utils.Subject
import com.madovygabor.maturimate.features.appOnboarding.domain.UserPreferences
import com.madovygabor.maturimate.features.subjectsAndExams.domain.model.Exam

data class SubjectSelectionUiState(
    val subjects: List<Subject> = emptyList(),
    val filteredExams: List<Exam> = emptyList(),
    val userPreferences: UserPreferences? = null, // Ezt adtuk hozzá!
    val isLoading: Boolean = false,
    val error: String? = null
)
