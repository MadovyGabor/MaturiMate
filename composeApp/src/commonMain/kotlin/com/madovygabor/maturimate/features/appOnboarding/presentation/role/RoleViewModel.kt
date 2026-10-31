package com.madovygabor.maturimate.features.appOnboarding.presentation.role

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.features.appOnboarding.presentation.profileSetup.error.toUiText
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class RoleUiState(
    val selected: UserRole? = null,
    val saving: Boolean = false,
) {
    val canContinue: Boolean get() = selected != null && !saving
}

/** The role question: nothing is preselected unless an answer was saved before. */
class RoleViewModel(
    private val repository: UserProfileRepository,
    private val snackbarController: SnackbarController,
) : ViewModel() {

    private val _state = MutableStateFlow(RoleUiState())
    val state: StateFlow<RoleUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = runCatching { repository.profile.first()?.role }.getOrNull()
            _state.update { if (it.selected == null) it.copy(selected = saved) else it }
        }
    }

    fun onSelected(role: UserRole) {
        _state.update { it.copy(selected = role) }
    }

    /**
     * Saves the role and calls [onStudent] for a student, who goes on to the questions still on
     * the old setup screen. Learners and teachers finish the onboarding here for now: their next
     * questions come in steps 5.7 to 5.12; finishing it takes the app to the home screen.
     */
    @OptIn(ExperimentalTime::class)
    fun onNext(onStudent: () -> Unit) {
        val role = _state.value.selected ?: return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val saved = repository.saveRole(role)
            val result = if (saved is OperationResult.Success && role != UserRole.STUDENT) {
                repository.completeOnboarding(Clock.System.now())
            } else {
                saved
            }
            when (result) {
                is OperationResult.Success -> if (role == UserRole.STUDENT) onStudent()
                is OperationResult.Error -> snackbarController.showSnackbar(message = result.error.toUiText())
            }
            _state.update { it.copy(saving = false) }
        }
    }
}
