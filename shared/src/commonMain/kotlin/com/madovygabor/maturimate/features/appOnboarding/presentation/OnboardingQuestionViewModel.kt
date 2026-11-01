package com.madovygabor.maturimate.features.appOnboarding.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.features.appOnboarding.presentation.profileSetup.error.toUiText
import com.madovygabor.maturimate.features.userProfile.domain.DataError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** State of a one-answer onboarding question. */
data class QuestionUiState<T : Any>(
    val selected: T? = null,
    val saving: Boolean = false,
) {
    val canContinue: Boolean get() = selected != null && !saving
}

/**
 * The common part of a one-answer onboarding question: it loads the saved answer (nothing is
 * preselected otherwise), keeps the selection, saves it on Next and shows a snackbar when
 * saving fails. A screen's ViewModel only says how its answer is read and saved.
 *
 * @param load The answer saved before, if any.
 * @param save Saves the answer; called on Next.
 */
open class OnboardingQuestionViewModel<T : Any>(
    private val snackbarController: SnackbarController,
    private val load: suspend () -> T?,
    private val save: suspend (T) -> OperationResult<Unit, DataError>,
) : ViewModel() {

    private val _state = MutableStateFlow(QuestionUiState<T>())
    val state: StateFlow<QuestionUiState<T>> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = runCatching { load() }.getOrNull()
            // a choice made while loading wins over the saved one
            _state.update { if (it.selected == null) it.copy(selected = saved) else it }
        }
    }

    fun onSelected(answer: T) {
        _state.update { it.copy(selected = answer) }
    }

    /** Saves the selected answer, then calls [onSaved] with it; nothing happens without a selection. */
    fun onNext(onSaved: (T) -> Unit) {
        val answer = _state.value.selected ?: return
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val result = save(answer)) {
                is OperationResult.Success -> onSaved(answer)
                is OperationResult.Error -> snackbarController.showSnackbar(message = result.error.toUiText())
            }
            _state.update { it.copy(saving = false) }
        }
    }
}
