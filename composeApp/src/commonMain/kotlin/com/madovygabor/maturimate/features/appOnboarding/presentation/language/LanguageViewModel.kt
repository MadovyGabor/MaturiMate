package com.madovygabor.maturimate.features.appOnboarding.presentation.language

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.features.appOnboarding.presentation.profileSetup.error.toUiText
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LanguageUiState(
    val selected: TeachingLanguage? = null,
    /** The role saved before, if the user came back here; it sets the number of steps. */
    val role: UserRole? = null,
    val saving: Boolean = false,
) {
    val canContinue: Boolean get() = selected != null && !saving
}

/** The teaching language question: nothing is preselected unless an answer was saved before. */
class LanguageViewModel(
    private val repository: UserProfileRepository,
    private val snackbarController: SnackbarController,
) : ViewModel() {

    private val _state = MutableStateFlow(LanguageUiState())
    val state: StateFlow<LanguageUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = runCatching { repository.profile.first() }.getOrNull()
            _state.update { it.copy(selected = it.selected ?: saved?.teachingLanguage, role = saved?.role) }
        }
    }

    fun onSelected(language: TeachingLanguage) {
        _state.update { it.copy(selected = language) }
    }

    /**
     * Saves the answer (it also sets the app language) and calls [onSaved]. A new language
     * rebuilds the screens in that language; the onboarding then continues from the saved answers.
     */
    fun onNext(onSaved: () -> Unit) {
        val language = _state.value.selected ?: return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val result = repository.saveTeachingLanguage(language)) {
                is OperationResult.Success -> onSaved()
                is OperationResult.Error -> snackbarController.showSnackbar(message = result.error.toUiText())
            }
            _state.update { it.copy(saving = false) }
        }
    }
}
