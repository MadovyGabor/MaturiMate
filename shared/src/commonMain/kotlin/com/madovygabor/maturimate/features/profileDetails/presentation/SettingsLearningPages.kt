package com.madovygabor.maturimate.features.profileDetails.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CoPresent
import androidx.compose.material.icons.rounded.Lock
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
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
import com.madovygabor.maturimate.core.presentation.uiComponents.AppDatePickerDialog
import com.madovygabor.maturimate.core.presentation.uiComponents.AppTimePickerDialog
import com.madovygabor.maturimate.core.presentation.utils.longDate
import com.madovygabor.maturimate.features.appOnboarding.presentation.goal.DEFAULT_GOAL
import com.madovygabor.maturimate.features.appOnboarding.presentation.goal.GOAL_OPTIONS
import com.madovygabor.maturimate.features.appOnboarding.presentation.goal.hoursPerYear
import com.madovygabor.maturimate.features.appOnboarding.presentation.reminder.DEFAULT_REMINDER_TIME
import com.madovygabor.maturimate.features.appOnboarding.presentation.reminder.hoursAndMinutes
import com.madovygabor.maturimate.features.appOnboarding.presentation.subjects.LevelChoice
import com.madovygabor.maturimate.features.appOnboarding.presentation.subjects.SubjectItem
import com.madovygabor.maturimate.features.appOnboarding.presentation.subjects.label
import com.madovygabor.maturimate.features.userProfile.domain.CatalogSubject
import com.madovygabor.maturimate.features.userProfile.domain.ExamDates
import com.madovygabor.maturimate.features.userProfile.domain.SubjectLevel
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import com.madovygabor.maturimate.features.userProfile.domain.UserSubject
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.todayIn
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.common_cancel
import maturimate.shared.generated.resources.common_ok
import maturimate.shared.generated.resources.date_days_left
import maturimate.shared.generated.resources.date_other
import maturimate.shared.generated.resources.date_other_desc
import maturimate.shared.generated.resources.date_picker_title
import maturimate.shared.generated.resources.date_spring
import maturimate.shared.generated.resources.date_written_days
import maturimate.shared.generated.resources.goal_minutes
import maturimate.shared.generated.resources.goal_year_hint
import maturimate.shared.generated.resources.language_hu
import maturimate.shared.generated.resources.language_hu_desc
import maturimate.shared.generated.resources.language_sk
import maturimate.shared.generated.resources.language_sk_desc
import maturimate.shared.generated.resources.level_unknown
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
import maturimate.shared.generated.resources.settings_edit_date_desc
import maturimate.shared.generated.resources.settings_edit_date_title
import maturimate.shared.generated.resources.settings_edit_goal_desc
import maturimate.shared.generated.resources.settings_edit_goal_title
import maturimate.shared.generated.resources.settings_edit_reminder_desc
import maturimate.shared.generated.resources.settings_edit_reminder_title
import maturimate.shared.generated.resources.settings_edit_role_desc
import maturimate.shared.generated.resources.settings_edit_role_title
import maturimate.shared.generated.resources.settings_edit_school_language_desc
import maturimate.shared.generated.resources.settings_edit_school_language_title
import maturimate.shared.generated.resources.settings_edit_subjects_desc
import maturimate.shared.generated.resources.settings_edit_subjects_title
import maturimate.shared.generated.resources.subject_anj
import maturimate.shared.generated.resources.subjects_compulsory
import maturimate.shared.generated.resources.subjects_english_level
import maturimate.shared.generated.resources.subjects_level_unknown_hint
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

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

internal fun buildSubjectItems(profile: UserProfile): Pair<List<SubjectItem>, LevelChoice?> {
    val role = profile.role
    val saved = profile.subjects.mapNotNull { CatalogSubject.byId(it.subjectId) }
    val offered = CatalogSubject.offeredTo(role, profile.teachingLanguage)
    val shown = if (role == UserRole.TEACHER) offered else offered - CatalogSubject.ENGLISH_B2
    val items = shown.map { subject ->
        val locked = role == UserRole.STUDENT && subject.compulsory
        val asksLevel = role != UserRole.TEACHER && subject.level != null
        val isSaved = if (asksLevel) saved.any { it.level != null } else subject in saved
        SubjectItem(
            subject = subject,
            selected = locked || isSaved,
            locked = locked,
            asksLevel = asksLevel,
        )
    }
    val savedLevel = if (role == UserRole.TEACHER) null else saved.firstNotNullOfOrNull { it.level }
    return items to savedLevel?.let { LevelChoice.valueOf(it.name) }
}

internal fun toUserSubjects(items: List<SubjectItem>, level: LevelChoice?): List<UserSubject> {
    val resolvedLevel = if (level == LevelChoice.B2) SubjectLevel.B2 else SubjectLevel.B1
    return items.filter { it.selected }.map { item ->
        val subject = if (item.asksLevel) CatalogSubject.english(resolvedLevel) else item.subject
        UserSubject(subjectId = subject.id, compulsory = item.locked)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsSubjectsPage(
    profile: UserProfile,
    afterChange: Boolean,
    busy: Boolean,
    onBack: () -> Unit,
    onSave: (List<UserSubject>) -> Unit,
) {
    val (initialItems, initialLevel) = remember(profile) { buildSubjectItems(profile) }
    var items by remember(profile) { mutableStateOf(initialItems) }
    var level by remember(profile) { mutableStateOf(initialLevel) }
    val selectedSubjects = items.filter { it.selected }
    val needsLevel = selectedSubjects.any { it.asksLevel }
    val isValid = items.isNotEmpty() && selectedSubjects.isNotEmpty() && (!needsLevel || level != null)
    val targetSubjects = remember(items, level) { toUserSubjects(items, level) }
    val savedSet = remember(profile.subjects) { profile.subjects.map { it.subjectId to it.compulsory }.toSet() }
    val currentSet = targetSubjects.map { it.subjectId to it.compulsory }.toSet()
    val changed = afterChange || currentSet != savedSet

    SettingsEditScaffold(
        title = stringResource(Res.string.settings_edit_subjects_title),
        description = stringResource(Res.string.settings_edit_subjects_desc),
        onBack = onBack,
        onSave = { if (isValid) onSave(targetSubjects) },
        canSave = isValid && changed,
        saving = busy,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space3)) {
            items.forEach { item ->
                SettingsSubjectCard(
                    item = item,
                    level = level,
                    onToggled = {
                        items = items.map { current ->
                            if (current.subject == item.subject && !current.locked) current.copy(selected = !current.selected) else current
                        }
                    },
                    onLevelSelected = { level = it },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsSubjectCard(
    item: SubjectItem,
    level: LevelChoice?,
    onToggled: () -> Unit,
    onLevelSelected: (LevelChoice) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val layout = MaterialTheme.layout
    val spacing = MaterialTheme.spacing
    Surface(
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerLowest,
        contentColor = colors.onBackground,
        border = if (item.selected) BorderStroke(layout.borderThick, colors.primary) else BorderStroke(layout.borderThin, colors.outline),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = layout.choiceCardMinHeight)
                    .toggleable(value = item.selected, enabled = !item.locked, role = Role.Checkbox, onValueChange = { onToggled() })
                    .padding(horizontal = spacing.space4, vertical = spacing.space3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.space3),
            ) {
                Surface(
                    modifier = Modifier.size(layout.icon),
                    shape = MaterialTheme.shapes.extraSmall,
                    color = if (item.selected) colors.primary else Color.Transparent,
                    contentColor = colors.onPrimary,
                    border = if (item.selected) null else BorderStroke(layout.borderThick, colors.outline),
                ) {
                    if (item.selected) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(layout.iconSmall))
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.space1)) {
                    Text(stringResource(if (item.asksLevel) Res.string.subject_anj else item.subject.label), style = MaterialTheme.typography.titleMedium)
                    if (item.locked) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.space1)) {
                            Icon(Icons.Rounded.Lock, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(layout.iconSmall))
                            Text(stringResource(Res.string.subjects_compulsory), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
            if (item.asksLevel && item.selected) {
                Column(
                    modifier = Modifier.padding(start = spacing.space4, end = spacing.space4, bottom = spacing.space4),
                    verticalArrangement = Arrangement.spacedBy(spacing.space2),
                ) {
                    Text(stringResource(Res.string.subjects_english_level), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.space2)) {
                        AppChoiceChip("B1", level == LevelChoice.B1, { onLevelSelected(LevelChoice.B1) })
                        AppChoiceChip("B2", level == LevelChoice.B2, { onLevelSelected(LevelChoice.B2) })
                        AppChoiceChip(stringResource(Res.string.level_unknown), level == LevelChoice.UNKNOWN, { onLevelSelected(LevelChoice.UNKNOWN) })
                    }
                    if (level == LevelChoice.UNKNOWN) {
                        Text(stringResource(Res.string.subjects_level_unknown_hint), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTime::class)
@Composable
fun SettingsDatePage(
    currentDate: LocalDate?,
    afterRoleChange: Boolean,
    busy: Boolean,
    onBack: () -> Unit,
    onSave: (LocalDate) -> Unit,
    today: LocalDate = remember { Clock.System.todayIn(TimeZone.currentSystemDefault()) },
) {
    val suggestions = remember(today) { ExamDates.nextSpringExams(today) }
    val initial = remember(currentDate, suggestions) {
        currentDate?.takeIf { ExamDates.isValid(it, today) } ?: suggestions.first()
    }
    var selected by remember(currentDate, suggestions) { mutableStateOf(initial) }
    var picking by remember { mutableStateOf(false) }
    val custom = selected.takeIf { it !in suggestions }

    SettingsEditScaffold(
        title = stringResource(Res.string.settings_edit_date_title),
        description = stringResource(Res.string.settings_edit_date_desc),
        onBack = onBack,
        onSave = { onSave(selected) },
        canSave = afterRoleChange || selected != currentDate,
        saving = busy,
    ) {
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space3)) {
            suggestions.forEach { exam ->
                val days = today.daysUntil(exam)
                AppChoiceCard(
                    badge = (exam.year % 100).toString().padStart(2, '0'),
                    title = stringResource(Res.string.date_spring, exam.year),
                    description = pluralStringResource(Res.plurals.date_written_days, days, longDate(exam), days),
                    selected = selected == exam,
                    onClick = { selected = exam },
                )
            }
            AppChoiceCard(
                badgeIcon = Icons.Rounded.CalendarMonth,
                title = stringResource(Res.string.date_other),
                description = custom?.let {
                    val days = today.daysUntil(it)
                    pluralStringResource(Res.plurals.date_days_left, days, longDate(it), days)
                } ?: stringResource(Res.string.date_other_desc),
                selected = custom != null,
                onClick = { picking = true },
            )
        }
    }

    if (picking) {
        AppDatePickerDialog(
            title = stringResource(Res.string.date_picker_title),
            confirmText = stringResource(Res.string.common_ok),
            dismissText = stringResource(Res.string.common_cancel),
            initial = selected,
            earliest = today,
            latest = ExamDates.latest(today),
            onConfirm = { date -> selected = date; picking = false },
            onDismiss = { picking = false },
        )
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
    var selectedTime by remember(currentTime) { mutableStateOf(currentTime ?: DEFAULT_REMINDER_TIME) }
    var picking by remember { mutableStateOf(false) }
    var permission by remember { mutableStateOf<NotificationPermissionResult?>(null) }
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
        Text(stringResource(Res.string.reminder_time), style = MaterialTheme.typography.labelLarge, color = colors.onBackground)
        Surface(
            onClick = { picking = true },
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
                Text(selectedTime.hoursAndMinutes(), style = MaterialTheme.typography.titleLarge, color = colors.onBackground)
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
            confirmText = stringResource(Res.string.common_ok),
            dismissText = stringResource(Res.string.common_cancel),
            initial = selectedTime,
            onConfirm = { selectedTime = it; picking = false },
            onDismiss = { picking = false },
        )
    }
}
