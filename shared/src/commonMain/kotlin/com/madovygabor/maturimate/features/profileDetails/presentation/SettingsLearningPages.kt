package com.madovygabor.maturimate.features.profileDetails.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.CoPresent
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.madovygabor.maturimate.core.presentation.NotificationPermissionResult
import com.madovygabor.maturimate.core.presentation.notificationsNeedPermission
import com.madovygabor.maturimate.core.presentation.rememberNotificationPermissionRequest
import com.madovygabor.maturimate.core.presentation.rememberOpenNotificationSettings
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.uiComponents.AppButton
import com.madovygabor.maturimate.core.presentation.uiComponents.AppButtonVariant
import com.madovygabor.maturimate.core.presentation.uiComponents.AppChoiceCard
import com.madovygabor.maturimate.core.presentation.uiComponents.AppChoiceChip
import com.madovygabor.maturimate.core.presentation.uiComponents.AppTimePickerDialog
import com.madovygabor.maturimate.features.appOnboarding.presentation.goal.DEFAULT_GOAL
import com.madovygabor.maturimate.features.appOnboarding.presentation.goal.GOAL_OPTIONS
import com.madovygabor.maturimate.features.appOnboarding.presentation.goal.hoursPerYear
import com.madovygabor.maturimate.features.appOnboarding.presentation.reminder.DEFAULT_REMINDER_TIME
import com.madovygabor.maturimate.features.appOnboarding.presentation.reminder.hoursAndMinutes
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import kotlinx.datetime.LocalTime
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.common_cancel
import maturimate.shared.generated.resources.common_set
import maturimate.shared.generated.resources.goal_minutes
import maturimate.shared.generated.resources.goal_year_hint
import maturimate.shared.generated.resources.language_hu
import maturimate.shared.generated.resources.language_hu_desc
import maturimate.shared.generated.resources.language_sk
import maturimate.shared.generated.resources.language_sk_desc
import maturimate.shared.generated.resources.reminder_open_settings
import maturimate.shared.generated.resources.reminder_permission_blocked
import maturimate.shared.generated.resources.reminder_permission_denied
import maturimate.shared.generated.resources.reminder_permission_hint
import maturimate.shared.generated.resources.reminder_time
import maturimate.shared.generated.resources.role_learner
import maturimate.shared.generated.resources.role_learner_desc
import maturimate.shared.generated.resources.role_student
import maturimate.shared.generated.resources.role_student_desc
import maturimate.shared.generated.resources.role_teacher
import maturimate.shared.generated.resources.role_teacher_desc
import maturimate.shared.generated.resources.settings_edit_goal_desc
import maturimate.shared.generated.resources.settings_edit_goal_title
import maturimate.shared.generated.resources.settings_edit_reminder_desc
import maturimate.shared.generated.resources.settings_edit_reminder_title
import maturimate.shared.generated.resources.settings_edit_role_desc
import maturimate.shared.generated.resources.settings_edit_role_title
import maturimate.shared.generated.resources.settings_edit_school_language_desc
import maturimate.shared.generated.resources.settings_edit_school_language_title
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsRolePage(
    currentRole: UserRole?,
    busy: Boolean,
    onBack: () -> Unit,
    onSave: (UserRole) -> Unit,
) {
    var selected by rememberSaveable(currentRole) { mutableStateOf(currentRole) }
    SettingsEditScaffold(
        title = stringResource(Res.string.settings_edit_role_title),
        description = stringResource(Res.string.settings_edit_role_desc),
        onBack = onBack,
        onSave = { selected?.let(onSave) },
        canSave = selected != null && selected != currentRole,
        saving = busy,
    ) {
        val options = listOf(
            Triple(UserRole.STUDENT, Icons.Rounded.School, Res.string.role_student to Res.string.role_student_desc),
            Triple(UserRole.LEARNER, Icons.AutoMirrored.Rounded.MenuBook, Res.string.role_learner to Res.string.role_learner_desc),
            Triple(UserRole.TEACHER, Icons.Rounded.CoPresent, Res.string.role_teacher to Res.string.role_teacher_desc),
        )
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space3)) {
            options.forEach { (role, icon, texts) ->
                AppChoiceCard(
                    badgeIcon = icon,
                    title = stringResource(texts.first),
                    description = stringResource(texts.second),
                    selected = selected == role,
                    onClick = { selected = role },
                )
            }
        }
    }
}

@Composable
fun SettingsSchoolLanguagePage(
    currentLanguage: TeachingLanguage?,
    busy: Boolean,
    onBack: () -> Unit,
    onSave: (TeachingLanguage) -> Unit,
) {
    var selected by rememberSaveable(currentLanguage) { mutableStateOf(currentLanguage) }
    SettingsEditScaffold(
        title = stringResource(Res.string.settings_edit_school_language_title),
        description = stringResource(Res.string.settings_edit_school_language_desc),
        onBack = onBack,
        onSave = { selected?.let(onSave) },
        canSave = selected != null && selected != currentLanguage,
        saving = busy,
    ) {
        val options = listOf(
            Triple(TeachingLanguage.HU, "HU", Res.string.language_hu to Res.string.language_hu_desc),
            Triple(TeachingLanguage.SK, "SK", Res.string.language_sk to Res.string.language_sk_desc),
        )
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space3)) {
            options.forEach { (language, badge, texts) ->
                AppChoiceCard(
                    badge = badge,
                    title = stringResource(texts.first),
                    description = stringResource(texts.second),
                    selected = selected == language,
                    onClick = { selected = language },
                )
            }
        }
    }
}

@Composable
fun SettingsGoalPage(
    currentGoal: Int?,
    afterRoleChange: Boolean,
    busy: Boolean,
    onBack: () -> Unit,
    onSave: (Int) -> Unit,
) {
    var selected by rememberSaveable(currentGoal) { mutableStateOf(currentGoal ?: DEFAULT_GOAL) }
    val hours = hoursPerYear(selected)
    SettingsEditScaffold(
        title = stringResource(Res.string.settings_edit_goal_title),
        description = stringResource(Res.string.settings_edit_goal_desc),
        onBack = onBack,
        onSave = { onSave(selected) },
        canSave = afterRoleChange || selected != currentGoal,
        saving = busy,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val perRow = if (maxWidth >= MaterialTheme.layout.breakpointMedium) GOAL_OPTIONS.size else 2
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space2),
            ) {
                GOAL_OPTIONS.chunked(perRow).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space2)) {
                        row.forEach { minutes ->
                            AppChoiceChip(
                                label = pluralStringResource(Res.plurals.goal_minutes, minutes, minutes),
                                selected = selected == minutes,
                                onClick = { selected = minutes },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
        Text(
            pluralStringResource(Res.plurals.goal_year_hint, hours, selected, hours),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SettingsReminderPage(
    currentEnabled: Boolean,
    currentTime: LocalTime?,
    busy: Boolean,
    onBack: () -> Unit,
    onSave: (LocalTime) -> Unit,
) {
    val initialTime = remember(currentTime) { currentTime ?: DEFAULT_REMINDER_TIME }
    var selectedTimeStr by rememberSaveable(currentTime?.toString()) { mutableStateOf(initialTime.toString()) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var permission by remember { mutableStateOf<NotificationPermissionResult?>(null) }
    val selectedTime = remember(selectedTimeStr) { LocalTime.parse(selectedTimeStr) }
    val requestPermission = rememberNotificationPermissionRequest { result ->
        if (result == NotificationPermissionResult.GRANTED) {
            permission = null
            onSave(selectedTime)
        } else {
            permission = result
        }
    }
    val openSettings = rememberOpenNotificationSettings()

    SettingsEditScaffold(
        title = stringResource(Res.string.settings_edit_reminder_title),
        description = stringResource(Res.string.settings_edit_reminder_desc),
        onBack = onBack,
        onSave = requestPermission,
        canSave = !currentEnabled || selectedTime != currentTime,
        saving = busy,
    ) {
        val colors = MaterialTheme.colorScheme
        val spacing = MaterialTheme.spacing
        val timeLabel = stringResource(Res.string.reminder_time)
        val formattedTime = selectedTime.hoursAndMinutes()
        Text(timeLabel, style = MaterialTheme.typography.labelLarge, color = colors.onBackground)
        Surface(
            onClick = { picking = true },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MaterialTheme.layout.choiceCardMinHeight)
                .semantics {
                    role = Role.Button
                    contentDescription = "$timeLabel, $formattedTime"
                },
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
                Text(formattedTime, style = MaterialTheme.typography.titleLarge, color = colors.onBackground)
            }
        }
        when (permission) {
            null -> if (notificationsNeedPermission) {
                Text(stringResource(Res.string.reminder_permission_hint), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            NotificationPermissionResult.DENIED, NotificationPermissionResult.GRANTED ->
                Text(stringResource(Res.string.reminder_permission_denied), style = MaterialTheme.typography.bodyMedium, color = colors.error)
            NotificationPermissionResult.BLOCKED -> {
                Text(stringResource(Res.string.reminder_permission_blocked), style = MaterialTheme.typography.bodyMedium, color = colors.error)
                AppButton(stringResource(Res.string.reminder_open_settings), openSettings, variant = AppButtonVariant.OUTLINED)
            }
        }
    }

    if (picking) {
        AppTimePickerDialog(
            title = stringResource(Res.string.reminder_time),
            confirmText = stringResource(Res.string.common_set),
            dismissText = stringResource(Res.string.common_cancel),
            initial = selectedTime,
            onConfirm = { selectedTimeStr = it.toString(); picking = false },
            onDismiss = { picking = false },
        )
    }
}
