package com.madovygabor.maturimate.features.profileDetails.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.madovygabor.maturimate.core.presentation.remindersAvailable
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.uiComponents.AppSettingsGroup
import com.madovygabor.maturimate.core.presentation.uiComponents.AppSettingsRow
import com.madovygabor.maturimate.core.presentation.uiComponents.SettingsTrailing
import com.madovygabor.maturimate.core.presentation.uiComponents.SettingsValueTone
import com.madovygabor.maturimate.core.presentation.utils.longDate
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingStep
import com.madovygabor.maturimate.features.appOnboarding.presentation.reminder.hoursAndMinutes
import com.madovygabor.maturimate.features.appOnboarding.presentation.subjects.label
import com.madovygabor.maturimate.features.content.domain.ContentUpdateState
import com.madovygabor.maturimate.features.sync.domain.SyncState
import com.madovygabor.maturimate.features.userProfile.domain.CatalogSubject
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.account_create
import maturimate.shared.generated.resources.common_back
import maturimate.shared.generated.resources.common_next
import maturimate.shared.generated.resources.goal_minutes
import maturimate.shared.generated.resources.language_hu
import maturimate.shared.generated.resources.language_sk
import maturimate.shared.generated.resources.plan_days
import maturimate.shared.generated.resources.profile_guest
import maturimate.shared.generated.resources.profile_not_set
import maturimate.shared.generated.resources.profile_settings
import maturimate.shared.generated.resources.role_learner
import maturimate.shared.generated.resources.role_student
import maturimate.shared.generated.resources.role_teacher
import maturimate.shared.generated.resources.settings_app_language
import maturimate.shared.generated.resources.settings_content
import maturimate.shared.generated.resources.settings_content_hint
import maturimate.shared.generated.resources.settings_create_hint
import maturimate.shared.generated.resources.settings_delete
import maturimate.shared.generated.resources.settings_delete_hint
import maturimate.shared.generated.resources.settings_email
import maturimate.shared.generated.resources.settings_exam_value
import maturimate.shared.generated.resources.settings_export
import maturimate.shared.generated.resources.settings_export_hint
import maturimate.shared.generated.resources.settings_group_account
import maturimate.shared.generated.resources.settings_group_app
import maturimate.shared.generated.resources.settings_group_danger
import maturimate.shared.generated.resources.settings_group_data
import maturimate.shared.generated.resources.settings_group_learning
import maturimate.shared.generated.resources.settings_group_notifications
import maturimate.shared.generated.resources.settings_name
import maturimate.shared.generated.resources.settings_no_account
import maturimate.shared.generated.resources.settings_off
import maturimate.shared.generated.resources.settings_on
import maturimate.shared.generated.resources.settings_password
import maturimate.shared.generated.resources.settings_password_hint
import maturimate.shared.generated.resources.settings_reminder
import maturimate.shared.generated.resources.settings_reminder_time
import maturimate.shared.generated.resources.settings_reset
import maturimate.shared.generated.resources.settings_reset_hint
import maturimate.shared.generated.resources.settings_reset_text
import maturimate.shared.generated.resources.settings_reset_title
import maturimate.shared.generated.resources.settings_school_language
import maturimate.shared.generated.resources.settings_school_language_value
import maturimate.shared.generated.resources.settings_sign_in_hint
import maturimate.shared.generated.resources.settings_sign_out
import maturimate.shared.generated.resources.settings_sign_out_anyway
import maturimate.shared.generated.resources.settings_sign_out_text
import maturimate.shared.generated.resources.settings_sign_out_title
import maturimate.shared.generated.resources.settings_sign_out_unsynced_text
import maturimate.shared.generated.resources.settings_sign_out_unsynced_title
import maturimate.shared.generated.resources.settings_sync
import maturimate.shared.generated.resources.welcome_have_account
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsContent(
    state: SettingsUiState?,
    actions: SettingsActions,
    dialogs: SettingsDialogState = SettingsDialogState(),
    syncState: StateFlow<SyncState>? = null,
    contentState: StateFlow<ContentUpdateState>? = null,
) {
    val spacing = MaterialTheme.spacing
    val layout = MaterialTheme.layout
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        // Tablet and desktop: the groups in two columns.
        val wide = maxWidth >= layout.breakpointMedium
        val containerModifier = Modifier.widthIn(max = layout.contentMaxWidth * 2).fillMaxWidth()
        if (wide) {
            Column(
                containerModifier.verticalScroll(rememberScrollState()).padding(spacing.space4),
                verticalArrangement = Arrangement.spacedBy(spacing.space4),
            ) {
                SettingsHeader(actions.onBack)
                if (state == null) return@Column
                val name = fullName(state)
                Who(name, state.account?.email)
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.space6)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.space4)) {
                        Account(state.isGuest, state.account != null, name, state.account?.email, state.account?.hasPassword == true, actions)
                        Learning(state.profile, state.learningSteps, state.daysLeft, actions)
                        if (remindersAvailable) Notifications(state.profile.reminderEnabled, state.profile.reminderTime, actions)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.space4)) {
                        App(state.profile.uiLanguage, actions)
                        Data(
                            isGuest = state.isGuest,
                            today = state.today,
                            timezone = state.profile.timezone,
                            canShareExport = state.canShareExport,
                            fallbackSync = state.sync,
                            fallbackContent = state.content,
                            syncState = syncState,
                            contentState = contentState,
                            actions = actions,
                        )
                        Danger(state.isGuest, actions)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = containerModifier,
                contentPadding = PaddingValues(spacing.space4),
                verticalArrangement = Arrangement.spacedBy(spacing.space4),
            ) {
                item("header") { SettingsHeader(actions.onBack) }
                if (state == null) return@LazyColumn
                val name = fullName(state)
                item("who") { Who(name, state.account?.email) }
                item("account") {
                    Account(state.isGuest, state.account != null, name, state.account?.email, state.account?.hasPassword == true, actions)
                }
                item("learning") { Learning(state.profile, state.learningSteps, state.daysLeft, actions) }
                if (remindersAvailable) {
                    item("notifications") { Notifications(state.profile.reminderEnabled, state.profile.reminderTime, actions) }
                }
                item("app") { App(state.profile.uiLanguage, actions) }
                item("data") {
                    Data(
                        isGuest = state.isGuest,
                        today = state.today,
                        timezone = state.profile.timezone,
                        canShareExport = state.canShareExport,
                        fallbackSync = state.sync,
                        fallbackContent = state.content,
                        syncState = syncState,
                        contentState = contentState,
                        actions = actions,
                    )
                }
                item("danger") { Danger(state.isGuest, actions) }
            }
        }
    }
    if (state != null) {
        SettingsDialogHost(state, actions, dialogs)
    }
}

@Composable
private fun SettingsHeader(onBack: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        onBack?.let { back ->
            IconButton(onClick = back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(Res.string.common_back)) }
        }
        Text(stringResource(Res.string.profile_settings), modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun SettingsDialogHost(state: SettingsUiState, actions: SettingsActions, dialogs: SettingsDialogState) {
    val close = actions.onCloseDialog
    val error = dialogs.error
    when (dialogs.dialog) {
        SettingsDialog.NAME -> NameDialog(
            initialFirstName = state.profile.firstName ?: state.account?.firstName.orEmpty(),
            initialLastName = state.profile.lastName ?: state.account?.lastName.orEmpty(),
            busy = actions.busy,
            error = error,
            onClearError = actions.onClearDialogError,
            onSave = actions.onSaveNames,
            onDismiss = close,
        )
        SettingsDialog.RESET, SettingsDialog.RESET_CONFIRM -> ResetFlowDialog(
            confirmStep = dialogs.dialog == SettingsDialog.RESET_CONFIRM,
            busy = actions.busy,
            error = error,
            onClearError = actions.onClearDialogError,
            onNext = { actions.onOpenDialog(SettingsDialog.RESET_CONFIRM) },
            onConfirm = actions.onReset,
            onDismiss = close,
        )
        SettingsDialog.SIGN_OUT -> ConfirmDialog(
            title = stringResource(Res.string.settings_sign_out_title),
            text = stringResource(Res.string.settings_sign_out_text),
            confirm = stringResource(Res.string.settings_sign_out),
            busy = actions.busy,
            onConfirm = { actions.onSignOut(false) },
            onDismiss = close,
            error = error,
        )
        SettingsDialog.SIGN_OUT_UNSYNCED -> ConfirmDialog(
            title = stringResource(Res.string.settings_sign_out_unsynced_title),
            text = stringResource(Res.string.settings_sign_out_unsynced_text),
            confirm = stringResource(Res.string.settings_sign_out_anyway),
            busy = actions.busy,
            onConfirm = { actions.onSignOut(true) },
            onDismiss = close,
            error = error,
        )
        SettingsDialog.EXPORT -> ExportDialog(
            onSave = { actions.onExport(false) },
            onShare = { actions.onExport(true) },
            onDismiss = close,
        )
        SettingsDialog.APP_LANGUAGE -> AppLanguageDialog(
            current = state.profile.uiLanguage,
            onChoose = { language ->
                close()
                actions.onAppLanguage(language)
            },
            onDismiss = close,
        )
        null -> Unit
    }
}

/** Keeps one Dialog window open across Reset step 1 and step 2 to avoid window-recreation jank. */
@Composable
private fun ResetFlowDialog(
    confirmStep: Boolean,
    busy: Boolean,
    error: com.madovygabor.maturimate.features.auth.domain.AuthError?,
    onClearError: () -> Unit,
    onNext: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!confirmStep) {
        ConfirmDialog(
            title = stringResource(Res.string.settings_reset_title),
            text = stringResource(Res.string.settings_reset_text),
            confirm = stringResource(Res.string.common_next),
            busy = busy,
            onConfirm = onNext,
            onDismiss = onDismiss,
            error = error,
        )
    } else {
        ResetConfirmDialog(
            busy = busy,
            error = error,
            onClearError = onClearError,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    }
}

/** The initial, the name and the e-mail; "Vendég" for a guest. */
@Composable
private fun Who(name: String?, email: String?) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.space3)) {
        Surface(
            Modifier.size(MaterialTheme.layout.badge),
            shape = MaterialTheme.shapes.extraLarge,
            color = if (name != null) colors.primaryContainer else colors.surfaceContainerHighest,
            contentColor = if (name != null) colors.onPrimaryContainer else colors.onSurfaceVariant,
        ) {
            Box(contentAlignment = Alignment.Center) {
                val initial = name?.firstOrNull { it.isLetter() }
                if (initial != null) Text(initial.uppercase(), style = MaterialTheme.typography.titleLarge)
                else Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(MaterialTheme.layout.iconSmall))
            }
        }
        Column {
            Text(name ?: stringResource(Res.string.profile_guest), style = MaterialTheme.typography.titleLarge)
            Text(
                email ?: stringResource(Res.string.settings_no_account),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/** The full name (the family name first in Hungarian): the one saved on this phone, else the account's (from Google). */
private fun fullName(state: SettingsUiState): String? {
    fun join(last: String?, first: String?): String? {
        val parts = if (state.profile.uiLanguage == TeachingLanguage.HU) listOf(last, first) else listOf(first, last)
        return parts.filterNot { it.isNullOrBlank() }.joinToString(" ").ifBlank { null }
    }
    return join(state.profile.lastName, state.profile.firstName) ?: join(state.account?.lastName, state.account?.firstName)
}

@Composable
private fun Account(
    isGuest: Boolean,
    signedIn: Boolean,
    name: String?,
    email: String?,
    hasPassword: Boolean,
    actions: SettingsActions,
) {
    AppSettingsGroup(stringResource(Res.string.settings_group_account), Icons.Rounded.Person) {
        if (isGuest) {
            AppSettingsRow(Icons.Rounded.PersonAdd, stringResource(Res.string.account_create), stringResource(Res.string.settings_create_hint), actions.onSignUp, divider = false)
            AppSettingsRow(Icons.AutoMirrored.Rounded.Login, stringResource(Res.string.welcome_have_account), stringResource(Res.string.settings_sign_in_hint), actions.onSignIn)
        } else {
            AppSettingsRow(
                Icons.Rounded.Person,
                stringResource(Res.string.settings_name),
                name,
                onClick = if (signedIn) ({ actions.onOpenDialog(SettingsDialog.NAME) }) else null,
                divider = false,
            )
            email?.let {
                AppSettingsRow(
                    icon = Icons.Rounded.Email,
                    title = stringResource(Res.string.settings_email),
                    value = it,
                    onClick = null,
                    trailing = SettingsTrailing.None,
                )
            }
            if (hasPassword) {
                AppSettingsRow(
                    Icons.Rounded.Key,
                    stringResource(Res.string.settings_password),
                    stringResource(Res.string.settings_password_hint),
                    onClick = { actions.onOpenPage(SettingsPage.Password) },
                )
            }
        }
    }
}

@Composable
private fun Learning(
    profile: UserProfile,
    learningSteps: List<OnboardingStep>,
    daysLeft: Int?,
    actions: SettingsActions,
) {
    AppSettingsGroup(stringResource(Res.string.settings_group_learning), Icons.Rounded.School) {
        learningSteps.forEachIndexed { index, step ->
            AppSettingsRow(
                stepIcon(step),
                stringResource(step.label),
                answerText(step, profile, daysLeft),
                onClick = { actions.onOpenPage(step.toSettingsPage()) },
                divider = index > 0,
            )
            if (step == OnboardingStep.ROLE) {
                AppSettingsRow(
                    Icons.Rounded.Translate,
                    stringResource(Res.string.settings_school_language),
                    stringResource(Res.string.settings_school_language_value, languageName(profile.teachingLanguage)),
                    onClick = { actions.onOpenPage(SettingsPage.SchoolLanguage) },
                )
            }
        }
    }
}

@Composable
private fun Notifications(reminderEnabled: Boolean, reminderTime: LocalTime?, actions: SettingsActions) {
    AppSettingsGroup(stringResource(Res.string.settings_group_notifications), Icons.Rounded.Notifications) {
        AppSettingsRow(
            Icons.Rounded.Notifications,
            stringResource(Res.string.settings_reminder),
            stringResource(if (reminderEnabled) Res.string.settings_on else Res.string.settings_off),
            onClick = { actions.onReminder(!reminderEnabled) },
            trailing = SettingsTrailing.Toggle(reminderEnabled),
            divider = false,
        )
        if (reminderEnabled && reminderTime != null) {
            AppSettingsRow(
                Icons.Rounded.Schedule,
                stringResource(Res.string.settings_reminder_time),
                reminderTime.hoursAndMinutes(),
                onClick = { actions.onOpenPage(SettingsPage.Reminder) },
            )
        }
    }
}

@Composable
private fun App(uiLanguage: TeachingLanguage, actions: SettingsActions) {
    AppSettingsGroup(stringResource(Res.string.settings_group_app), Icons.Rounded.Tune) {
        AppSettingsRow(
            Icons.Rounded.Translate,
            stringResource(Res.string.settings_app_language),
            languageName(uiLanguage),
            onClick = { actions.onOpenDialog(SettingsDialog.APP_LANGUAGE) },
            divider = false,
        )
    }
}

/** The sync, content download, and data export. */
@Composable
private fun Data(
    isGuest: Boolean,
    today: LocalDate?,
    timezone: String,
    canShareExport: Boolean,
    fallbackSync: SyncState,
    fallbackContent: ContentUpdateState,
    syncState: StateFlow<SyncState>?,
    contentState: StateFlow<ContentUpdateState>?,
    actions: SettingsActions,
) {
    val sync by (syncState?.collectAsState() ?: remember(fallbackSync) { androidx.compose.runtime.mutableStateOf(fallbackSync) })
    val content by (contentState?.collectAsState() ?: remember(fallbackContent) { androidx.compose.runtime.mutableStateOf(fallbackContent) })
    val zone = remember(timezone) { runCatching { TimeZone.of(timezone) }.getOrDefault(TimeZone.currentSystemDefault()) }
    AppSettingsGroup(stringResource(Res.string.settings_group_data), Icons.Rounded.Sync) {
        if (!isGuest) {
            val syncDisplay = syncRowDisplay(sync, today, actions.onSync, zone)
            AppSettingsRow(
                icon = Icons.Rounded.Sync,
                title = stringResource(Res.string.settings_sync),
                value = syncDisplay.text,
                valueTone = syncDisplay.tone,
                onClick = if (sync.running) null else actions.onSync,
                trailing = syncDisplay.trailing,
                divider = false,
                animateSize = true,
            )
        }
        val contentDisplay = if (isGuest) null else contentRowDisplay(content, actions.onUpdateContent)
        AppSettingsRow(
            icon = Icons.Rounded.CloudDownload,
            title = stringResource(Res.string.settings_content),
            value = contentDisplay?.text ?: stringResource(Res.string.settings_content_hint),
            valueTone = contentDisplay?.tone ?: SettingsValueTone.DEFAULT,
            onClick = if (isGuest || content.downloading) null else actions.onUpdateContent,
            trailing = contentDisplay?.trailing ?: SettingsTrailing.None,
            divider = !isGuest,
            animateSize = !isGuest,
        )
        if (!isGuest) {
            AppSettingsRow(
                Icons.Rounded.CloudDownload,
                stringResource(Res.string.settings_export),
                stringResource(Res.string.settings_export_hint),
                onClick = { if (canShareExport) actions.onOpenDialog(SettingsDialog.EXPORT) else actions.onExport(false) },
                trailing = SettingsTrailing.None,
                divider = true,
            )
        }
    }
}

/** The red rows: the reset, the sign-out, and the account deletion. */
@Composable
private fun Danger(isGuest: Boolean, actions: SettingsActions) {
    AppSettingsGroup(stringResource(Res.string.settings_group_danger), Icons.Rounded.RestartAlt) {
        AppSettingsRow(
            Icons.Rounded.RestartAlt,
            stringResource(Res.string.settings_reset),
            stringResource(Res.string.settings_reset_hint),
            onClick = { actions.onOpenDialog(SettingsDialog.RESET) },
            trailing = SettingsTrailing.None,
            danger = true,
            divider = false,
        )
        if (!isGuest) {
            AppSettingsRow(
                Icons.AutoMirrored.Rounded.Logout,
                stringResource(Res.string.settings_sign_out),
                null,
                onClick = { actions.onOpenDialog(SettingsDialog.SIGN_OUT) },
                trailing = SettingsTrailing.None,
                danger = true,
            )
            AppSettingsRow(
                Icons.Rounded.Delete,
                stringResource(Res.string.settings_delete),
                stringResource(Res.string.settings_delete_hint),
                onClick = { actions.onOpenPage(SettingsPage.Delete) },
                trailing = SettingsTrailing.Arrow,
                danger = true,
            )
        }
    }
}

@Composable
private fun languageName(language: TeachingLanguage?): String = when (language) {
    TeachingLanguage.HU -> stringResource(Res.string.language_hu)
    TeachingLanguage.SK -> stringResource(Res.string.language_sk)
    null -> stringResource(Res.string.profile_not_set)
}

private fun stepIcon(step: OnboardingStep) = when (step) {
    OnboardingStep.ROLE -> Icons.Rounded.School
    OnboardingStep.SUBJECTS -> Icons.AutoMirrored.Rounded.MenuBook
    OnboardingStep.DATE -> Icons.Rounded.CalendarMonth
    OnboardingStep.GOAL -> Icons.Rounded.TrackChanges
    else -> Icons.Rounded.Tune
}

/** The saved answer of a step in words. */
@Composable
private fun answerText(step: OnboardingStep, profile: UserProfile, daysLeft: Int?): String {
    val notSet = stringResource(Res.string.profile_not_set)
    return when (step) {
        OnboardingStep.ROLE -> when (profile.role) {
            UserRole.STUDENT -> stringResource(Res.string.role_student)
            UserRole.LEARNER -> stringResource(Res.string.role_learner)
            UserRole.TEACHER -> stringResource(Res.string.role_teacher)
            null -> notSet
        }
        OnboardingStep.SUBJECTS -> profile.subjects
            .mapNotNull { saved -> CatalogSubject.byId(saved.subjectId) }
            .map { stringResource(it.label) }
            .joinToString(", ")
            .ifEmpty { notSet }
        OnboardingStep.DATE -> profile.graduationDate?.let { date ->
            if (daysLeft != null) stringResource(Res.string.settings_exam_value, longDate(date), pluralStringResource(Res.plurals.plan_days, daysLeft, daysLeft))
            else longDate(date)
        } ?: notSet
        OnboardingStep.GOAL -> profile.dailyGoalMinutes?.let { pluralStringResource(Res.plurals.goal_minutes, it, it) } ?: notSet
        else -> ""
    }
}
