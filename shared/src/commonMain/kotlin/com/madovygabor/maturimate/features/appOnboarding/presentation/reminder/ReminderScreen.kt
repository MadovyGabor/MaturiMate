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
import com.madovygabor.maturimate.core.presentation.NotificationPermissionResult
import com.madovygabor.maturimate.core.presentation.notificationsNeedPermission
import com.madovygabor.maturimate.core.presentation.rememberNotificationPermissionRequest
import com.madovygabor.maturimate.core.presentation.rememberOpenNotificationSettings
import com.madovygabor.maturimate.core.presentation.uiComponents.AppButton
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.uiComponents.AppButtonVariant
import com.madovygabor.maturimate.core.presentation.uiComponents.AppTimePickerDialog
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingProgress
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingStep
import com.madovygabor.maturimate.features.appOnboarding.presentation.components.OnboardingAction
import com.madovygabor.maturimate.features.appOnboarding.presentation.components.OnboardingScaffold
import com.madovygabor.maturimate.features.appOnboarding.presentation.onboardingSteps
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import kotlinx.datetime.LocalTime
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.common_cancel
import maturimate.shared.generated.resources.common_ok
import maturimate.shared.generated.resources.reminder_not_now
import maturimate.shared.generated.resources.reminder_open_settings
import maturimate.shared.generated.resources.reminder_permission_blocked
import maturimate.shared.generated.resources.reminder_permission_denied
import maturimate.shared.generated.resources.reminder_permission_hint
import maturimate.shared.generated.resources.reminder_time
import maturimate.shared.generated.resources.reminder_title
import maturimate.shared.generated.resources.reminder_turn_on
import maturimate.shared.generated.resources.reminder_why
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

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
    val requestPermission = rememberNotificationPermissionRequest { result -> viewModel.onPermissionResult(result, onNext) }
    val openSettings = rememberOpenNotificationSettings()
    ReminderContent(
        state = state,
        role = role,
        showPermissionHint = notificationsNeedPermission,
        onTimeSelected = viewModel::onTimeSelected,
        onTurnOn = requestPermission,
        onNotNow = { viewModel.onAnswer(false, onNext) },
        onBack = onBack,
        onOpenSettings = openSettings,
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
    onOpenSettings: () -> Unit = {},
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
        when (state.permission) {
            null -> if (showPermissionHint) {
                Text(stringResource(Res.string.reminder_permission_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            NotificationPermissionResult.DENIED, NotificationPermissionResult.GRANTED ->
                Text(stringResource(Res.string.reminder_permission_denied), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            NotificationPermissionResult.BLOCKED -> {
                Text(stringResource(Res.string.reminder_permission_blocked), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                AppButton(stringResource(Res.string.reminder_open_settings), onOpenSettings, variant = AppButtonVariant.OUTLINED)
            }
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
