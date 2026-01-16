package com.madovygabor.maturimate.core.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SubjectDetailViewModel(
    private val subjectId: String
): ViewModel() {
    // Simulated list of subjects
    private val _state = MutableStateFlow(SubjectDetailState(subjectId))
    val state = _state.asStateFlow()

}

data class SubjectDetailState(
    val subject: String
)