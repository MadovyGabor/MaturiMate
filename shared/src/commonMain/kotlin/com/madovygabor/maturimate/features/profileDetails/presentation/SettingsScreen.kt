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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.uiComponents.AppChoiceCard
import com.madovygabor.maturimate.core.presentation.uiComponents.AppSettingsGroup
import com.madovygabor.maturimate.core.presentation.uiComponents.AppSettingsRow
import com.madovygabor.maturimate.core.presentation.uiComponents.SettingsTrailing
import com.madovygabor.maturimate.core.presentation.utils.longDate
import com.madovygabor.maturimate.features.appOnboarding.presentation.LocalEditingAnswer
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingStep
import com.madovygabor.maturimate.features.appOnboarding.presentation.date.DateScreen
import com.madovygabor.maturimate.features.appOnboarding.presentation.goal.GoalScreen
import com.madovygabor.maturimate.features.appOnboarding.presentation.reminder.ReminderScreen
import com.madovygabor.maturimate.features.appOnboarding.presentation.reminder.hoursAndMinutes
import com.madovygabor.maturimate.features.appOnboarding.presentation.role.RoleScreen
import com.madovygabor.maturimate.features.appOnboarding.presentation.subjects.SubjectsScreen
import com.madovygabor.maturimate.features.appOnboarding.presentation.subjects.label
import com.madovygabor.maturimate.features.userProfile.domain.CatalogSubject
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import maturimate.shared.generated.resources.Res
import com.madovygabor.maturimate.features.auth.domain.AuthError
import maturimate.shared.generated.resources.settings_reset_confirm
import maturimate.shared.generated.resources.settings_reset_text
import maturimate.shared.generated.resources.settings_reset_title
import maturimate.shared.generated.resources.settings_sign_out_text
import maturimate.shared.generated.resources.settings_sign_out_title
import maturimate.shared.generated.resources.common_back
import maturimate.shared.generated.resources.common_cancel
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
import maturimate.shared.generated.resources.settings_choose_language
import maturimate.shared.generated.resources.settings_content
import maturimate.shared.generated.resources.settings_content_hint
import maturimate.shared.generated.resources.settings_create_hint
import maturimate.shared.generated.resources.settings_delete
import maturimate.shared.generated.resources.settings_delete_hint
import maturimate.shared.generated.resources.settings_email
import maturimate.shared.generated.resources.settings_exam_value
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
import maturimate.shared.generated.resources.settings_school_language
import maturimate.shared.generated.resources.settings_school_language_value
import maturimate.shared.generated.resources.settings_sign_in_hint
import maturimate.shared.generated.resources.settings_sign_out
import maturimate.shared.generated.resources.settings_sync
import maturimate.shared.generated.resources.settings_sync_hint
import maturimate.shared.generated.resources.account_create
import maturimate.shared.generated.resources.subjects_coming_soon
import maturimate.shared.generated.resources.welcome_have_account
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.ExperimentalTime

/** A page of the settings: the list, or one onboarding question opened to change its answer. */
private sealed interface SettingsPage {
    data object List : SettingsPage
    data class Edit(val step: OnboardingStep) : SettingsPage
}

/** Which language the choice dialog changes. */
private enum class LanguageKind { SCHOOL, APP }

/**
 * The settings, opened by the gear of the profile (not a tab). The learning answers open their onboarding
 * question; a new role or a new school language asks for the subjects again, then for an exam date or a daily goal
 * when the role needs one that is missing. [onSignUp] and [onSignIn] are a guest's account rows.
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
    val backStack = remember { mutableStateListOf<SettingsPage>(SettingsPage.List) }
    val done: () -> Unit = { if (backStack.size > 1) backStack.removeLast() else onBack() }
    // the first of the questions still missing replaces the one just answered, so Back returns
    // to the list; each question then asks for what is still missing after it
    val continueWith: (List<OnboardingStep>) -> Unit = { missing ->
        backStack.removeLast()
        missing.firstOrNull()?.let { backStack.add(SettingsPage.Edit(it)) }
    }
    val open: (OnboardingStep) -> Unit = { backStack.add(SettingsPage.Edit(it)) }

    NavDisplay(
        backStack = backStack,
        onBack = done,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            // every opened question loads the saved answer again
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<SettingsPage.List> {
                SettingsContent(
                    state,
                    SettingsActions(
                        onBack = onBack,
                        onEdit = open,
                        onSchoolLanguage = { language -> viewModel.setSchoolLanguage(language) { open(OnboardingStep.SUBJECTS) } },
                        onAppLanguage = viewModel::setAppLanguage,
                        onReminder = { enabled -> if (!viewModel.setReminder(enabled)) open(OnboardingStep.REMINDER) },
                        onSignUp = onSignUp,
                        onSignIn = onSignIn,
                        onSaveNames = viewModel::saveNames,
                        onChangePassword = viewModel::changePassword,
                        onReset = viewModel::resetProgress,
                        onSignOut = viewModel::signOut,
                        busy = busy,
                    ),
                )
            }
            entry<SettingsPage.Edit> { page ->
                val role = state?.profile?.role
                CompositionLocalProvider(LocalEditingAnswer provides true) {
                    when (page.step) {
                        OnboardingStep.ROLE -> RoleScreen(onBack = done, onNext = { continueWith(listOf(OnboardingStep.SUBJECTS)) })
                        OnboardingStep.SUBJECTS -> SubjectsScreen(onBack = done, onNext = { newRole -> continueWith(viewModel.missingAfterRoleChange(newRole)) })
                        OnboardingStep.DATE -> DateScreen(onBack = done, onNext = { continueWith(viewModel.missingAfterRoleChange(role) - OnboardingStep.DATE) })
                        OnboardingStep.GOAL -> GoalScreen(role = role, onBack = done, onNext = done)
                        OnboardingStep.REMINDER -> ReminderScreen(role = role, onBack = done, onNext = done)
                        // the languages are chosen in a dialog of the list
                        OnboardingStep.LANGUAGE, OnboardingStep.PLAN, OnboardingStep.ACCOUNT -> Unit
                    }
                }
            }
        },
    )
}

/** What the settings list can do; one object keeps the parameters short. */
data class SettingsActions(
    val onBack: (() -> Unit)? = null,
    val onEdit: (OnboardingStep) -> Unit = {},
    val onSchoolLanguage: (TeachingLanguage) -> Unit = {},
    val onAppLanguage: (TeachingLanguage) -> Unit = {},
    val onReminder: (Boolean) -> Unit = {},
    val onSignUp: () -> Unit = {},
    val onSignIn: () -> Unit = {},
    /** The name, the password, the reset and the sign-out; each gets back the error, if any. */
    val onSaveNames: (String, String, (AuthError?) -> Unit) -> Unit = { _, _, _ -> },
    val onChangePassword: (String, String, (AuthError?) -> Unit) -> Unit = { _, _, _ -> },
    val onReset: ((Boolean) -> Unit) -> Unit = {},
    val onSignOut: ((AuthError?) -> Unit) -> Unit = {},
    val busy: Boolean = false,
)

/** The dialog open over the list. */
private enum class SettingsDialog { NAME, PASSWORD, RESET, SIGN_OUT }

@Composable
fun SettingsContent(state: SettingsUiState?, actions: SettingsActions) {
    val spacing = MaterialTheme.spacing
    val layout = MaterialTheme.layout
    var choosing by remember { mutableStateOf<LanguageKind?>(null) }
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }
    var error by remember { mutableStateOf<AuthError?>(null) }
    val openDialog: (SettingsDialog) -> Unit = { error = null; dialog = it }
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
                Account(state, actions, onName = { openDialog(SettingsDialog.NAME) }, onPassword = { openDialog(SettingsDialog.PASSWORD) })
                Learning(state, actions, onSchoolLanguage = { choosing = LanguageKind.SCHOOL })
                Notifications(state.profile, actions)
            }
            val right: @Composable () -> Unit = {
                App(state.profile, onAppLanguage = { choosing = LanguageKind.APP })
                Data()
                Danger(state, onReset = { openDialog(SettingsDialog.RESET) }, onSignOut = { openDialog(SettingsDialog.SIGN_OUT) })
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
        val close = { dialog = null }
        // a dialog closes on success and shows the error otherwise
        val after: (AuthError?) -> Unit = { failed -> if (failed == null) dialog = null else error = failed }
        when (dialog) {
            SettingsDialog.NAME -> NameDialog(
                lastName = state.profile.lastName ?: state.account?.lastName.orEmpty(),
                firstName = state.profile.firstName ?: state.account?.firstName.orEmpty(),
                busy = actions.busy, error = error,
                onSave = { first, last -> actions.onSaveNames(first, last, after) }, onDismiss = close,
            )
            SettingsDialog.PASSWORD -> PasswordDialog(
                busy = actions.busy, error = error,
                onSave = { old, new -> actions.onChangePassword(old, new, after) }, onDismiss = close,
            )
            SettingsDialog.RESET -> ConfirmDialog(
                stringResource(Res.string.settings_reset_title), stringResource(Res.string.settings_reset_text),
                stringResource(Res.string.settings_reset_confirm), actions.busy,
                onConfirm = { actions.onReset { dialog = null } }, onDismiss = close,
            )
            SettingsDialog.SIGN_OUT -> ConfirmDialog(
                stringResource(Res.string.settings_sign_out_title), stringResource(Res.string.settings_sign_out_text),
                stringResource(Res.string.settings_sign_out), actions.busy,
                onConfirm = { actions.onSignOut(after) }, onDismiss = close,
            )
            null -> Unit
        }
    }
    val kind = choosing
    if (kind != null && state != null) {
        LanguageDialog(
            current = if (kind == LanguageKind.SCHOOL) state.profile.teachingLanguage else state.profile.uiLanguage,
            onChoose = { language ->
                choosing = null
                if (kind == LanguageKind.SCHOOL) actions.onSchoolLanguage(language) else actions.onAppLanguage(language)
            },
            onDismiss = { choosing = null },
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
                if (name != null) Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge)
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

/** The account's full name (the family name first in Hungarian), or the first name of the profile. */
private fun fullName(state: SettingsUiState): String? {
    val account = state.account
    val parts = if (state.profile.uiLanguage == TeachingLanguage.HU) listOf(account?.lastName, account?.firstName) else listOf(account?.firstName, account?.lastName)
    val full = parts.filterNot { it.isNullOrBlank() }.joinToString(" ").ifBlank { null }
    // the names saved on this phone (sign-up, the settings) come first, then the account's from Google
    val profile = state.profile
    val local = (if (profile.uiLanguage == TeachingLanguage.HU) listOf(profile.lastName, profile.firstName) else listOf(profile.firstName, profile.lastName))
        .filterNot { it.isNullOrBlank() }.joinToString(" ").ifBlank { null }
    return local ?: full
}

@Composable
private fun Account(state: SettingsUiState, actions: SettingsActions, onName: () -> Unit, onPassword: () -> Unit) {
    AppSettingsGroup(stringResource(Res.string.settings_group_account), Icons.Rounded.Person) {
        if (state.isGuest) {
            AppSettingsRow(Icons.Rounded.PersonAdd, stringResource(Res.string.account_create), stringResource(Res.string.settings_create_hint), actions.onSignUp, divider = false)
            AppSettingsRow(Icons.AutoMirrored.Rounded.Login, stringResource(Res.string.welcome_have_account), stringResource(Res.string.settings_sign_in_hint), actions.onSignIn)
        } else {
            // the name and the password need the account on the server; without a session they only show
            val signedIn = state.account != null
            AppSettingsRow(Icons.Rounded.Person, stringResource(Res.string.settings_name), fullName(state), onClick = onName.takeIf { signedIn }, divider = false)
            state.account?.email?.let { AppSettingsRow(Icons.Rounded.Email, stringResource(Res.string.settings_email), it, onClick = null) }
            // an account made with Google has no password
            if (state.account?.hasPassword == true) {
                AppSettingsRow(Icons.Rounded.Key, stringResource(Res.string.settings_password), stringResource(Res.string.settings_password_hint), onClick = onPassword)
            }
        }
    }
}

@Composable
private fun Learning(state: SettingsUiState, actions: SettingsActions, onSchoolLanguage: () -> Unit) {
    val profile = state.profile
    AppSettingsGroup(stringResource(Res.string.settings_group_learning), Icons.Rounded.School) {
        state.learningSteps.forEachIndexed { index, step ->
            AppSettingsRow(stepIcon(step), stringResource(step.label), answerText(step, state), onClick = { actions.onEdit(step) }, divider = index > 0)
            // the school language right after the role, as in the onboarding
            if (step == OnboardingStep.ROLE) {
                AppSettingsRow(
                    Icons.Rounded.Translate,
                    stringResource(Res.string.settings_school_language),
                    stringResource(Res.string.settings_school_language_value, languageName(profile.teachingLanguage)),
                    onClick = onSchoolLanguage,
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
            AppSettingsRow(Icons.Rounded.Schedule, stringResource(Res.string.settings_reminder_time), time.hoursAndMinutes(), onClick = { actions.onEdit(OnboardingStep.REMINDER) })
        }
    }
}

@Composable
private fun App(profile: UserProfile, onAppLanguage: () -> Unit) {
    AppSettingsGroup(stringResource(Res.string.settings_group_app), Icons.Rounded.Tune) {
        AppSettingsRow(Icons.Rounded.Translate, stringResource(Res.string.settings_app_language), languageName(profile.uiLanguage), onClick = onAppLanguage, divider = false)
    }
}

/** The sync and the content download: there already, so they are not forgotten (11.2, 11.3). */
@Composable
private fun Data() {
    val soon = SettingsTrailing.Soon(stringResource(Res.string.subjects_coming_soon))
    AppSettingsGroup(stringResource(Res.string.settings_group_data), Icons.Rounded.Sync) {
        AppSettingsRow(Icons.Rounded.Sync, stringResource(Res.string.settings_sync), stringResource(Res.string.settings_sync_hint), onClick = {}, trailing = soon, divider = false)
        AppSettingsRow(Icons.Rounded.CloudDownload, stringResource(Res.string.settings_content), stringResource(Res.string.settings_content_hint), onClick = {}, trailing = soon)
    }
}

/** The red rows: the reset, the sign-out, and the account deletion (11.6). */
@Composable
private fun Danger(state: SettingsUiState, onReset: () -> Unit, onSignOut: () -> Unit) {
    val soon = SettingsTrailing.Soon(stringResource(Res.string.subjects_coming_soon))
    AppSettingsGroup(stringResource(Res.string.settings_group_danger), Icons.Rounded.RestartAlt) {
        AppSettingsRow(Icons.Rounded.RestartAlt, stringResource(Res.string.settings_reset), stringResource(Res.string.settings_reset_hint), onClick = onReset, trailing = SettingsTrailing.None, danger = true, divider = false)
        if (!state.isGuest) {
            AppSettingsRow(Icons.AutoMirrored.Rounded.Logout, stringResource(Res.string.settings_sign_out), null, onClick = onSignOut, trailing = SettingsTrailing.None, danger = true)
            AppSettingsRow(Icons.Rounded.Delete, stringResource(Res.string.settings_delete), stringResource(Res.string.settings_delete_hint), onClick = {}, trailing = soon, danger = true)
        }
    }
}

/** Magyar or Szlovák, as two choice cards. */
@Composable
private fun LanguageDialog(current: TeachingLanguage?, onChoose: (TeachingLanguage) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_choose_language)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space2)) {
                TeachingLanguage.entries.forEach { language ->
                    AppChoiceCard(title = languageName(language), description = "", selected = language == current, onClick = { onChoose(language) })
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) } },
    )
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
