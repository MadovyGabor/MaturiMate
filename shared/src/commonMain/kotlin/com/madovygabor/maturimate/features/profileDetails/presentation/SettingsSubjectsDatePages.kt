package com.madovygabor.maturimate.features.profileDetails.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
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
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.uiComponents.AppChoiceCard
import com.madovygabor.maturimate.core.presentation.uiComponents.AppChoiceChip
import com.madovygabor.maturimate.core.presentation.uiComponents.AppDatePickerDialog
import com.madovygabor.maturimate.core.presentation.utils.longDate
import com.madovygabor.maturimate.features.appOnboarding.presentation.subjects.LevelChoice
import com.madovygabor.maturimate.features.appOnboarding.presentation.subjects.SubjectItem
import com.madovygabor.maturimate.features.appOnboarding.presentation.subjects.label
import com.madovygabor.maturimate.features.userProfile.domain.CatalogSubject
import com.madovygabor.maturimate.features.userProfile.domain.ExamDates
import com.madovygabor.maturimate.features.userProfile.domain.SubjectLevel
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import com.madovygabor.maturimate.features.userProfile.domain.UserSubject
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.todayIn
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.common_cancel
import maturimate.shared.generated.resources.common_set
import maturimate.shared.generated.resources.date_days_left
import maturimate.shared.generated.resources.date_other
import maturimate.shared.generated.resources.date_other_desc
import maturimate.shared.generated.resources.date_picker_title
import maturimate.shared.generated.resources.date_spring
import maturimate.shared.generated.resources.date_written_days
import maturimate.shared.generated.resources.level_unknown
import maturimate.shared.generated.resources.settings_edit_date_desc
import maturimate.shared.generated.resources.settings_edit_date_title
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

@Composable
fun SettingsSubjectsPage(
    profile: UserProfile,
    afterChange: Boolean,
    busy: Boolean,
    onBack: () -> Unit,
    onSave: (List<UserSubject>) -> Unit,
) {
    val baseConfig = remember(profile.role, profile.teachingLanguage) { buildSubjectItems(profile) }
    val initialSelectedIds = remember(profile.role, profile.teachingLanguage) {
        baseConfig.first.filter { it.selected }.map { it.subject.id }
    }
    var selectedIds by rememberSaveable(profile.role, profile.teachingLanguage) { mutableStateOf(initialSelectedIds) }
    var level by rememberSaveable(profile.role, profile.teachingLanguage) { mutableStateOf(baseConfig.second) }

    val items = remember(baseConfig.first, selectedIds) {
        baseConfig.first.map { item ->
            item.copy(selected = item.locked || item.subject.id in selectedIds)
        }
    }
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
                        if (!item.locked) {
                            val id = item.subject.id
                            selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
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
    var selectedIso by rememberSaveable(currentDate?.toString()) { mutableStateOf(initial.toString()) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val selected = remember(selectedIso) { LocalDate.parse(selectedIso) }
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
                    onClick = { selectedIso = exam.toString() },
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
            confirmText = stringResource(Res.string.common_set),
            dismissText = stringResource(Res.string.common_cancel),
            initial = selected,
            earliest = today,
            latest = ExamDates.latest(today),
            onConfirm = { date -> selectedIso = date.toString(); picking = false },
            onDismiss = { picking = false },
        )
    }
}
