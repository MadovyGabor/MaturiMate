package com.madovygabor.maturimate.features.profileDetails.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.MenuBook
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.madovygabor.maturimate.core.presentation.navigation.rememberNavTransitions
import com.madovygabor.maturimate.core.presentation.remindersAvailable
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.uiComponents.AppSettingsGroup
import com.madovygabor.maturimate.core.presentation.uiComponents.AppSettingsRow
import com.madovygabor.maturimate.core.presentation.uiComponents.SettingsTrailing
import com.madovygabor.maturimate.core.presentation.utils.longDate
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingStep
import com.madovygabor.maturimate.features.appOnboarding.presentation.reminder.hoursAndMinutes
import com.madovygabor.maturimate.features.appOnboarding.presentation.subjects.label
import com.madovygabor.maturimate.features.auth.presentation.rememberGoogleSignIn
import com.madovygabor.maturimate.features.userProfile.domain.CatalogSubject
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
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
import maturimate.shared.generated.resources.settings_reset_confirm
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
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.ExperimentalTime

/** A page inside the settings navigation: the main list or a dedicated edit/account page. */
sealed interface SettingsPage {
    data object List : SettingsPage
    data object Password : SettingsPage
    data object Delete : SettingsPage
    data object Role : SettingsPage
    data object SchoolLanguage : SettingsPage
    data class Subjects(val afterChange: Boolean = false) : SettingsPage
    data class Date(val afterRoleChange: Boolean = false) : SettingsPage
    data class Goal(val afterRoleChange: Boolean = false) : SettingsPage
    data object Reminder : SettingsPage
}

private fun SettingsPage.encode(): String = when (this) {
    SettingsPage.List -> "list"
    SettingsPage.Password -> "password"
    SettingsPage.Delete -> "delete"
    SettingsPage.Role -> "role"
    SettingsPage.SchoolLanguage -> "schoolLanguage"
    is SettingsPage.Subjects -> "subjects:$afterChange"
    is SettingsPage.Date -> "date:$afterRoleChange"
    is SettingsPage.Goal -> "goal:$afterRoleChange"
    SettingsPage.Reminder -> "reminder"
}

private fun decodeSettingsPage(raw: String): SettingsPage = when {
    raw == "password" -> SettingsPage.Password
    raw == "delete" -> SettingsPage.Delete
    raw == "role" -> SettingsPage.Role
    raw == "schoolLanguage" -> SettingsPage.SchoolLanguage
    raw.startsWith("subjects:") -> SettingsPage.Subjects(raw.substringAfter(':').toBoolean())
    raw.startsWith("date:") -> SettingsPage.Date(raw.substringAfter(':').toBoolean())
    raw.startsWith("goal:") -> SettingsPage.Goal(raw.substringAfter(':').toBoolean())
    raw == "reminder" -> SettingsPage.Reminder
    else -> SettingsPage.List
}

private val pagesSaver = listSaver<SnapshotStateList<SettingsPage>, String>(
    save = { pages -> pages.map { it.encode() } },
    restore = { saved -> saved.map(::decodeSettingsPage).toMutableStateList() },
)

private fun OnboardingStep.toSettingsPage(): SettingsPage = when (this) {
    OnboardingStep.ROLE -> SettingsPage.Role
    OnboardingStep.SUBJECTS -> SettingsPage.Subjects(afterChange = false)
    OnboardingStep.DATE -> SettingsPage.Date(afterRoleChange = false)
    OnboardingStep.GOAL -> SettingsPage.Goal(afterRoleChange = false)
    OnboardingStep.REMINDER -> SettingsPage.Reminder
    OnboardingStep.LANGUAGE -> SettingsPage.SchoolLanguage
    OnboardingStep.PLAN, OnboardingStep.ACCOUNT -> SettingsPage.List
}

private fun OnboardingStep.toMissingPage(): SettingsPage = when (this) {
    OnboardingStep.DATE -> SettingsPage.Date(afterRoleChange = true)
    OnboardingStep.GOAL -> SettingsPage.Goal(afterRoleChange = true)
    else -> toSettingsPage()
}

/**
 * The settings, opened by the gear of the profile (not a tab). Every input or multi-step flow opens a dedicated page
 * inside the settings navigation; short confirmations and one-line choices use [AppDialog].
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
    onSignUp: () -> Unit = {},
    onSignIn: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val dialogs by viewModel.dialogs.collectAsState()
    val passwordState by viewModel.passwordState.collectAsState()
    val pageError by viewModel.pageError.collectAsState()
    val backStack = rememberSaveable(saver = pagesSaver) { mutableStateListOf<SettingsPage>(SettingsPage.List) }
    val done: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) else onBack() }
    val continueWith: (kotlin.collections.List<OnboardingStep>) -> Unit = { missing ->
        backStack.removeAt(backStack.lastIndex)
        missing.firstOrNull()?.let { backStack.add(it.toMissingPage()) }
    }
    val openPage: (SettingsPage) -> Unit = { page ->
        viewModel.clearPageError()
        if (page == SettingsPage.Password) viewModel.resetPasswordPage()
        backStack.add(page)
    }
    // Composed only while the Delete page is active for a Google-only account, matching the old dialog lifecycle.
    val googleProof = if (backStack.lastOrNull() == SettingsPage.Delete && state?.account?.hasPassword == false) {
        rememberGoogleSignIn(
            onSignedIn = { viewModel.onGoogleDeletionSignedIn(onDeleted = done) },
            onError = viewModel::onGoogleDeletionFailed,
            onClosed = viewModel::onGoogleDeletionClosed,
            accountHint = state?.account?.email,
        )
    } else null

    val transitions = rememberNavTransitions()
    NavDisplay(
        transitionSpec = transitions.forward(),
        popTransitionSpec = transitions.back(),
        predictivePopTransitionSpec = transitions.predictiveBack(),
        backStack = backStack,
        onBack = done,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<SettingsPage.List> {
                SettingsContent(
                    state,
                    SettingsActions(
                        onBack = onBack,
                        onOpenPage = openPage,
                        onAppLanguage = viewModel::setAppLanguage,
                        onReminder = { enabled -> if (!viewModel.setReminder(enabled)) openPage(SettingsPage.Reminder) },
                        onSignUp = onSignUp,
                        onSignIn = onSignIn,
                        onOpenDialog = viewModel::openDialog,
                        onCloseDialog = viewModel::closeDialog,
                        onClearDialogError = viewModel::clearError,
                        onSaveNames = viewModel::saveNames,
                        onReset = viewModel::resetProgress,
                        onSignOut = viewModel::signOut,
                        onSync = viewModel::syncNow,
                        onUpdateContent = viewModel::updateContent,
                        onExport = viewModel::exportData,
                        busy = busy,
                    ),
                    dialogs,
                )
            }
            entry<SettingsPage.Password> {
                val current = state ?: return@entry
                SettingsPasswordPage(
                    email = current.account?.email.orEmpty(),
                    pageState = passwordState,
                    busy = busy,
                    onClearError = viewModel::clearPasswordError,
                    onSendCode = viewModel::sendPasswordCode,
                    onBack = done,
                    onSave = { old, new, code -> viewModel.changePassword(old, new, code, onSaved = done) },
                )
            }
            entry<SettingsPage.Delete> {
                val current = state ?: return@entry
                SettingsDeletePage(
                    withPassword = current.account?.hasPassword != false,
                    busy = busy,
                    error = pageError,
                    onClearError = viewModel::clearPageError,
                    onDeleteWithPassword = { password -> viewModel.deleteAccount(password, onDeleted = done) },
                    onDeleteWithGoogle = { if (googleProof != null) viewModel.startGoogleDeletion(openGoogle = googleProof) },
                    onBack = done,
                )
            }
            entry<SettingsPage.Role> {
                val current = state ?: return@entry
                SettingsRolePage(
                    currentRole = current.profile.role,
                    busy = busy,
                    onBack = done,
                    onSave = { newRole ->
                        viewModel.saveRole(newRole) {
                            backStack.removeAt(backStack.lastIndex)
                            backStack.add(SettingsPage.Subjects(afterChange = true))
                        }
                    },
                )
            }
            entry<SettingsPage.SchoolLanguage> {
                val current = state ?: return@entry
                SettingsSchoolLanguagePage(
                    currentLanguage = current.profile.teachingLanguage,
                    busy = busy,
                    onBack = done,
                    onSave = { newLanguage ->
                        viewModel.setSchoolLanguage(newLanguage) {
                            backStack.removeAt(backStack.lastIndex)
                            backStack.add(SettingsPage.Subjects(afterChange = true))
                        }
                    },
                )
            }
            entry<SettingsPage.Subjects> { page ->
                val current = state ?: return@entry
                SettingsSubjectsPage(
                    profile = current.profile,
                    afterChange = page.afterChange,
                    busy = busy,
                    onBack = done,
                    onSave = { subjects ->
                        viewModel.saveSubjects(subjects) {
                            if (page.afterChange) continueWith(viewModel.missingAfterRoleChange(current.profile.role))
                            else done()
                        }
                    },
                )
            }
            entry<SettingsPage.Date> { page ->
                val current = state ?: return@entry
                SettingsDatePage(
                    currentDate = current.profile.graduationDate,
                    afterRoleChange = page.afterRoleChange,
                    busy = busy,
                    onBack = done,
                    onSave = { date ->
                        viewModel.saveGraduationDate(date) {
                            if (page.afterRoleChange) continueWith(viewModel.missingAfterRoleChange(current.profile.role) - OnboardingStep.DATE)
                            else done()
                        }
                    },
                )
            }
            entry<SettingsPage.Goal> { page ->
                val current = state ?: return@entry
                SettingsGoalPage(
                    currentGoal = current.profile.dailyGoalMinutes,
                    afterRoleChange = page.afterRoleChange,
                    busy = busy,
                    onBack = done,
                    onSave = { minutes -> viewModel.saveDailyGoal(minutes, onSaved = done) },
                )
            }
            entry<SettingsPage.Reminder> {
                val current = state ?: return@entry
                SettingsReminderPage(
                    currentEnabled = current.profile.reminderEnabled,
                    currentTime = current.profile.reminderTime,
                    busy = busy,
                    onBack = done,
                    onSave = { time -> viewModel.saveReminderTime(time, onSaved = done) },
                )
            }
        },
    )
}

/**
 * What the settings list can do; one object keeps the parameters short. The dialogs and their results live in the view
 * model ([SettingsDialogState]); these only ask it to act.
 */
data class SettingsActions(
    val onBack: (() -> Unit)? = null,
    val onOpenPage: (SettingsPage) -> Unit = {},
    val onAppLanguage: (TeachingLanguage) -> Unit = {},
    val onReminder: (Boolean) -> Unit = {},
    val onSignUp: () -> Unit = {},
    val onSignIn: () -> Unit = {},
    val onOpenDialog: (SettingsDialog) -> Unit = {},
    val onCloseDialog: () -> Unit = {},
    val onClearDialogError: () -> Unit = {},
    val onSaveNames: (String, String) -> Unit = { _, _ -> },
    val onReset: () -> Unit = {},
    /** The sign-out; true goes on although the upload failed. */
    val onSignOut: (Boolean) -> Unit = {},
    val onSync: () -> Unit = {},
    val onUpdateContent: () -> Unit = {},
    /** The export; true shares it instead of saving it to a file. */
    val onExport: (Boolean) -> Unit = {},
    val busy: Boolean = false,
)

@Composable
fun SettingsContent(state: SettingsUiState?, actions: SettingsActions, dialogs: SettingsDialogState = SettingsDialogState()) {
    val spacing = MaterialTheme.spacing
    val layout = MaterialTheme.layout
    var choosingAppLanguage by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        // tablet and desktop: the groups in two columns
        val wide = maxWidth >= layout.breakpointMedium
        Column(
            Modifier
                .widthIn(max = layout.contentMaxWidth * 2)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.space4),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                actions.onBack?.let { back ->
                    IconButton(onClick = back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(Res.string.common_back)) }
                }
                Text(stringResource(Res.string.profile_settings), modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
            }
            if (state == null) return@Column
            Who(state)
            val left: @Composable () -> Unit = {
                Account(state, actions)
                Learning(state, actions)
                // the desktop app sends no reminder, so it offers no setting for it
                if (remindersAvailable) Notifications(state.profile, actions)
            }
            val right: @Composable () -> Unit = {
                App(state.profile, onAppLanguage = { choosingAppLanguage = true })
                Data(
                    state,
                    onSync = actions.onSync,
                    onUpdateContent = actions.onUpdateContent,
                    onExport = { if (state.canShareExport) actions.onOpenDialog(SettingsDialog.EXPORT) else actions.onExport(false) },
                )
                Danger(
                    state,
                    onReset = { actions.onOpenDialog(SettingsDialog.RESET) },
                    onSignOut = { actions.onOpenDialog(SettingsDialog.SIGN_OUT) },
                    onDelete = { actions.onOpenPage(SettingsPage.Delete) },
                )
            }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.space6)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.space4)) { left() }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.space4)) { right() }
                }
            } else {
                left()
                right()
            }
        }
    }
    if (state != null) {
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
            SettingsDialog.RESET -> ConfirmDialog(
                title = stringResource(Res.string.settings_reset_title),
                text = stringResource(Res.string.settings_reset_text),
                confirm = stringResource(Res.string.common_next),
                busy = actions.busy,
                onConfirm = { actions.onOpenDialog(SettingsDialog.RESET_CONFIRM) },
                onDismiss = close,
                error = error,
            )
            SettingsDialog.RESET_CONFIRM -> ResetConfirmDialog(
                busy = actions.busy,
                error = error,
                onClearError = actions.onClearDialogError,
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
            null -> Unit
        }
    }
    if (choosingAppLanguage && state != null) {
        AppLanguageDialog(
            current = state.profile.uiLanguage,
            onChoose = { language ->
                choosingAppLanguage = false
                actions.onAppLanguage(language)
            },
            onDismiss = { choosingAppLanguage = false },
        )
    }
}

/** The initial, the name and the e-mail; "Vendég" for a guest. */
@Composable
private fun Who(state: SettingsUiState) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    val name = fullName(state)
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
                state.account?.email ?: stringResource(Res.string.settings_no_account),
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
private fun Account(state: SettingsUiState, actions: SettingsActions) {
    AppSettingsGroup(stringResource(Res.string.settings_group_account), Icons.Rounded.Person) {
        if (state.isGuest) {
            AppSettingsRow(Icons.Rounded.PersonAdd, stringResource(Res.string.account_create), stringResource(Res.string.settings_create_hint), actions.onSignUp, divider = false)
            AppSettingsRow(Icons.AutoMirrored.Rounded.Login, stringResource(Res.string.welcome_have_account), stringResource(Res.string.settings_sign_in_hint), actions.onSignIn)
        } else {
            val signedIn = state.account != null
            AppSettingsRow(
                Icons.Rounded.Person,
                stringResource(Res.string.settings_name),
                fullName(state),
                onClick = if (signedIn) ({ actions.onOpenDialog(SettingsDialog.NAME) }) else null,
                divider = false,
            )
            state.account?.email?.let { email ->
                AppSettingsRow(
                    icon = Icons.Rounded.Email,
                    title = stringResource(Res.string.settings_email),
                    value = email,
                    onClick = null,
                    trailing = SettingsTrailing.None,
                )
            }
            if (state.account?.hasPassword == true) {
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
private fun Learning(state: SettingsUiState, actions: SettingsActions) {
    val profile = state.profile
    AppSettingsGroup(stringResource(Res.string.settings_group_learning), Icons.Rounded.School) {
        state.learningSteps.forEachIndexed { index, step ->
            AppSettingsRow(
                stepIcon(step),
                stringResource(step.label),
                answerText(step, state),
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
private fun Notifications(profile: UserProfile, actions: SettingsActions) {
    AppSettingsGroup(stringResource(Res.string.settings_group_notifications), Icons.Rounded.Notifications) {
        AppSettingsRow(
            Icons.Rounded.Notifications,
            stringResource(Res.string.settings_reminder),
            stringResource(if (profile.reminderEnabled) Res.string.settings_on else Res.string.settings_off),
            onClick = { actions.onReminder(!profile.reminderEnabled) },
            trailing = SettingsTrailing.Toggle(profile.reminderEnabled),
            divider = false,
        )
        val time = profile.reminderTime
        if (profile.reminderEnabled && time != null) {
            AppSettingsRow(
                Icons.Rounded.Schedule,
                stringResource(Res.string.settings_reminder_time),
                time.hoursAndMinutes(),
                onClick = { actions.onOpenPage(SettingsPage.Reminder) },
            )
        }
    }
}

@Composable
private fun App(profile: UserProfile, onAppLanguage: () -> Unit) {
    AppSettingsGroup(stringResource(Res.string.settings_group_app), Icons.Rounded.Tune) {
        AppSettingsRow(Icons.Rounded.Translate, stringResource(Res.string.settings_app_language), languageName(profile.uiLanguage), onClick = onAppLanguage, divider = false)
    }
}

/** The sync, content download, and data export. */
@Composable
private fun Data(state: SettingsUiState, onSync: () -> Unit, onUpdateContent: () -> Unit, onExport: () -> Unit) {
    AppSettingsGroup(stringResource(Res.string.settings_group_data), Icons.Rounded.Sync) {
        if (!state.isGuest) {
            val syncDisplay = syncRowDisplay(state.sync, state.today, onSync)
            AppSettingsRow(
                icon = Icons.Rounded.Sync,
                title = stringResource(Res.string.settings_sync),
                value = syncDisplay.text,
                valueTone = syncDisplay.tone,
                onClick = if (state.sync.running) null else onSync,
                trailing = syncDisplay.trailing,
                divider = false,
            )
        }
        val contentDisplay = if (state.isGuest) null else contentRowDisplay(state.content, state.today, onUpdateContent)
        AppSettingsRow(
            icon = Icons.Rounded.CloudDownload,
            title = stringResource(Res.string.settings_content),
            value = contentDisplay?.text ?: stringResource(Res.string.settings_content_hint),
            valueTone = contentDisplay?.tone ?: com.madovygabor.maturimate.core.presentation.uiComponents.SettingsValueTone.DEFAULT,
            onClick = if (state.isGuest || state.content.downloading) null else onUpdateContent,
            trailing = contentDisplay?.trailing ?: SettingsTrailing.None,
            divider = !state.isGuest,
        )
        if (!state.isGuest) {
            AppSettingsRow(Icons.Rounded.CloudDownload, stringResource(Res.string.settings_export), stringResource(Res.string.settings_export_hint), onClick = onExport, trailing = SettingsTrailing.None, divider = true)
        }
    }
}

/** The red rows: the reset, the sign-out, and the account deletion. */
@Composable
private fun Danger(state: SettingsUiState, onReset: () -> Unit, onSignOut: () -> Unit, onDelete: () -> Unit) {
    AppSettingsGroup(stringResource(Res.string.settings_group_danger), Icons.Rounded.RestartAlt) {
        AppSettingsRow(Icons.Rounded.RestartAlt, stringResource(Res.string.settings_reset), stringResource(Res.string.settings_reset_hint), onClick = onReset, trailing = SettingsTrailing.None, danger = true, divider = false)
        if (!state.isGuest) {
            AppSettingsRow(Icons.AutoMirrored.Rounded.Logout, stringResource(Res.string.settings_sign_out), null, onClick = onSignOut, trailing = SettingsTrailing.None, danger = true)
            AppSettingsRow(Icons.Rounded.Delete, stringResource(Res.string.settings_delete), stringResource(Res.string.settings_delete_hint), onClick = onDelete, trailing = SettingsTrailing.Arrow, danger = true)
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
    OnboardingStep.SUBJECTS -> Icons.Rounded.MenuBook
    OnboardingStep.DATE -> Icons.Rounded.CalendarMonth
    OnboardingStep.GOAL -> Icons.Rounded.TrackChanges
    else -> Icons.Rounded.Tune
}

/** The saved answer of a step in words. */
@OptIn(ExperimentalTime::class)
@Composable
private fun answerText(step: OnboardingStep, state: SettingsUiState): String {
    val profile = state.profile
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
            val days = state.daysLeft
            if (days != null) stringResource(Res.string.settings_exam_value, longDate(date), pluralStringResource(Res.plurals.plan_days, days, days))
            else longDate(date)
        } ?: notSet
        OnboardingStep.GOAL -> profile.dailyGoalMinutes?.let { pluralStringResource(Res.plurals.goal_minutes, it, it) } ?: notSet
        else -> ""
    }
}
