package com.madovygabor.maturimate.features.appOnboarding.presentation.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.core.presentation.NotificationPermissionResult
import com.madovygabor.maturimate.core.presentation.utils.readOrNull
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.features.appOnboarding.presentation.profileSetup.error.toUiText
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime

/** The time offered first, as in the prototype. */
val DEFAULT_REMINDER_TIME = LocalTime(18, 0)

/** "18:00" */
fun LocalTime.hoursAndMinutes(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

data class ReminderUiState(
    val time: LocalTime = DEFAULT_REMINDER_TIME,
    val saving: Boolean = false,
    /** How the last permission request ended when it did not allow notifications (DENIED or BLOCKED); null before. */
    val permission: NotificationPermissionResult? = null,
)

/**
 * The reminder question: on at a chosen time (after the system allows notifications), or
 * "not now". Only the setting is saved; sending the reminder comes with the streak (Milestone 9).
 */
class ReminderViewModel(
    private val repository: UserProfileRepository,
    private val snackbarController: SnackbarController,
) : ViewModel() {

    private val _state = MutableStateFlow(ReminderUiState())
    val state: StateFlow<ReminderUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = readOrNull { repository.profile.first()?.reminderTime }
            if (saved != null) _state.update { it.copy(time = saved) }
        }
    }

    fun onTimeSelected(time: LocalTime) {
        _state.update { it.copy(time = time) }
    }

    /**
     * The answer of the permission request. Allowed: the reminder is turned on. Otherwise nothing is saved: the screen
     * says why and offers to try again or (when the system will not ask any more) the system settings, and the user
     * decides with "not now" (so a refusal is never saved as that choice).
     */
    fun onPermissionResult(result: NotificationPermissionResult, onSaved: () -> Unit) {
        if (result == NotificationPermissionResult.GRANTED) {
            _state.update { it.copy(permission = null) }
            onAnswer(true, onSaved)
        } else {
            _state.update { it.copy(permission = result) }
        }
    }

    /** Saves the answer; [enabled] is false for "not now" and when notifications are not allowed. */
    fun onAnswer(enabled: Boolean, onSaved: () -> Unit) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            when (val result = repository.saveReminder(enabled, _state.value.time)) {
                is OperationResult.Success -> onSaved()
                is OperationResult.Error -> snackbarController.showSnackbar(message = result.error.toUiText())
            }
            _state.update { it.copy(saving = false) }
        }
    }
}
