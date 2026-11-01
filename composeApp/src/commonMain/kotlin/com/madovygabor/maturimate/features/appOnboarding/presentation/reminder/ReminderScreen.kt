package com.madovygabor.maturimate.features.appOnboarding.presentation.reminder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.core.presentation.notificationsNeedPermission
import com.madovygabor.maturimate.core.presentation.rememberNotificationPermissionRequest
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.uiComponents.AppButtonVariant
import com.madovygabor.maturimate.core.presentation.uiComponents.AppTimePickerDialog
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingProgress
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingStep
import com.madovygabor.maturimate.features.appOnboarding.presentation.components.OnboardingAction
import com.madovygabor.maturimate.features.appOnboarding.presentation.components.OnboardingScaffold
import com.madovygabor.maturimate.features.appOnboarding.presentation.onboardingSteps
import com.madovygabor.maturimate.features.appOnboarding.presentation.profileSetup.error.toUiText
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime
import maturimate.composeapp.generated.resources.Res
import maturimate.composeapp.generated.resources.common_cancel
import maturimate.composeapp.generated.resources.common_ok
import maturimate.composeapp.generated.resources.reminder_not_now
import maturimate.composeapp.generated.resources.reminder_permission_hint
import maturimate.composeapp.generated.resources.reminder_time
import maturimate.composeapp.generated.resources.reminder_title
import maturimate.composeapp.generated.resources.reminder_turn_on
import maturimate.composeapp.generated.resources.reminder_why
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

/** The time offered first, as in the prototype. */
val DEFAULT_REMINDER_TIME = LocalTime(18, 0)

/** "18:00" */
fun LocalTime.hoursAndMinutes(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

data class ReminderUiState(
    val time: LocalTime = DEFAULT_REMINDER_TIME,
    val saving: Boolean = false,
)

/**
 * The reminder question: on at a chosen time (after the system allows notifications), or
 * "Most nem". Only the setting is saved; sending the reminder comes with the streak (Milestone 8).
 */
class ReminderViewModel(
    private val repository: UserProfileRepository,
    private val snackbarController: SnackbarController,
) : ViewModel() {

    private val _state = MutableStateFlow(ReminderUiState())
    val state: StateFlow<ReminderUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = runCatching { repository.profile.first()?.reminderTime }.getOrNull()
            if (saved != null) _state.update { it.copy(time = saved) }
        }
    }

    fun onTimeSelected(time: LocalTime) {
        _state.update { it.copy(time = time) }
    }

    /** Saves the answer; [enabled] is false for "Most nem" and when notifications are not allowed. */
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

/** The reminder step: a daily notification at a chosen time, or "not now". */
@Composable
fun ReminderScreen(
    role: UserRole?,
    onBack: () -> Unit,
    onNext: () -> Unit,
    viewModel: ReminderViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    // the system dialog's answer decides whether the reminder is on
    val requestPermission = rememberNotificationPermissionRequest { granted -> viewModel.onAnswer(granted, onNext) }
    ReminderContent(
        state = state,
        role = role,
        showPermissionHint = notificationsNeedPermission,
        onTimeSelected = viewModel::onTimeSelected,
        onTurnOn = requestPermission,
        onNotNow = { viewModel.onAnswer(false, onNext) },
        onBack = onBack,
    )
}

@Composable
fun ReminderContent(
    state: ReminderUiState,
    role: UserRole?,
    showPermissionHint: Boolean,
    onTimeSelected: (LocalTime) -> Unit,
    onTurnOn: () -> Unit,
    onNotNow: () -> Unit,
    onBack: () -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    OnboardingScaffold(
        actions = listOf(
            OnboardingAction(stringResource(Res.string.reminder_turn_on), onTurnOn, enabled = !state.saving),
            OnboardingAction(stringResource(Res.string.reminder_not_now), onNotNow, AppButtonVariant.TEXT, enabled = !state.saving),
        ),
        stackedActions = true,
        progress = OnboardingProgress(OnboardingStep.REMINDER, onboardingSteps(role), onBack),
    ) {
        Text(stringResource(Res.string.reminder_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(stringResource(Res.string.reminder_why), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(Res.string.reminder_time), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground)
        TimeField(state.time.hoursAndMinutes(), onClick = { picking = true })
        if (showPermissionHint) {
            Text(stringResource(Res.string.reminder_permission_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (picking) {
        AppTimePickerDialog(
            title = stringResource(Res.string.reminder_time),
            confirmText = stringResource(Res.string.common_ok),
            dismissText = stringResource(Res.string.common_cancel),
            initial = state.time,
            onConfirm = { onTimeSelected(it); picking = false },
            onDismiss = { picking = false },
        )
    }
}

/** The chosen time as a field with a clock icon; tapping it opens the time picker. */
@Composable
private fun TimeField(time: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = MaterialTheme.layout.choiceCardMinHeight),
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceContainerLowest,
        border = BorderStroke(MaterialTheme.layout.borderThin, colors.outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.space4, vertical = spacing.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.space3),
        ) {
            Icon(Icons.Rounded.Schedule, contentDescription = null, tint = colors.primary)
            Text(time, style = MaterialTheme.typography.titleLarge, color = colors.onBackground)
        }
    }
}

@Composable
@Preview
private fun ReminderContentPreview() {
    AppTheme(darkTheme = false) { ReminderContent(ReminderUiState(), UserRole.STUDENT, true, {}, {}, {}, {}) }
}
