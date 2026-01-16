package com.madovygabor.maturimate.features.subjects.presentation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class SubjectViewModel: ViewModel(

) {
    // Simulated list of subjects
    private val _subjects = MutableStateFlow(
        (1..4).map { "Subject $it" }
    )
    val subjects = _subjects.asStateFlow()



}