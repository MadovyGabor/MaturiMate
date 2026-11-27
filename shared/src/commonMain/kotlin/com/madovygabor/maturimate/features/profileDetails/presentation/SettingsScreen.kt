package com.madovygabor.maturimate.features.profileDetails.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.madovygabor.maturimate.core.presentation.navigation.rememberNavTransitions
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingStep
import com.madovygabor.maturimate.features.auth.presentation.rememberGoogleSignIn
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
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

internal fun OnboardingStep.toSettingsPage(): SettingsPage = when (this) {
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
 * What the settings list can do; one object keeps the parameters short. The dialogs and their results live in the view
 * model ([SettingsDialogState]); these only ask it to act.
 */
@Immutable
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

/**
 * The settings, opened by the gear of the profile (not a tab). Every input or multi-step flow opens a dedicated page
 * inside the settings navigation; short confirmations and one-line choices use [AppDialog].
 */
@OptIn(ExperimentalTime::class)
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
    val popPage: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) else onBack() }
    val done: () -> Unit = { if (!busy) popPage() }
    val handleBack: () -> Unit = {
        if (!busy) {
            if (backStack.lastOrNull() == SettingsPage.Password && passwordState.step == PasswordStep.CODE) {
                viewModel.backToPasswordForm()
            } else {
                popPage()
            }
        }
    }
    val replaceCurrent: (SettingsPage) -> Unit = { next ->
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        backStack.add(next)
    }
    val continueWith: (kotlin.collections.List<OnboardingStep>) -> Unit = { missing ->
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        missing.firstOrNull()?.let { backStack.add(it.toMissingPage()) }
    }
    val openPage: (SettingsPage) -> Unit = { page ->
        if (!busy && backStack.lastOrNull() != page) {
            viewModel.clearPageError()
            if (page == SettingsPage.Password) viewModel.resetPasswordPage()
            backStack.add(page)
        }
    }
    val listActions = remember(viewModel, onBack, onSignUp, onSignIn, busy) {
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
        )
    }
    // Composed only while the Delete page is active for a Google-only account, matching the old dialog lifecycle.
    val googleProof = if (backStack.lastOrNull() == SettingsPage.Delete && state?.account?.hasPassword == false) {
        rememberGoogleSignIn(
            onSignedIn = { viewModel.onGoogleDeletionSignedIn(onDeleted = popPage) },
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
        onBack = handleBack,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<SettingsPage.List> {
                SettingsContent(
                    state = state,
                    actions = listActions,
                    dialogs = dialogs,
                    syncState = viewModel.syncState,
                    contentState = viewModel.contentState,
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
                    onBackToForm = viewModel::backToPasswordForm,
                    onBack = done,
                    onSave = { old, new, code -> viewModel.changePassword(old, new, code, onSaved = popPage) },
                )
            }
            entry<SettingsPage.Delete> {
                val current = state ?: return@entry
                var withPassword by remember { mutableStateOf(current.account?.hasPassword != false) }
                current.account?.let { withPassword = it.hasPassword }
                SettingsDeletePage(
                    withPassword = withPassword,
                    busy = busy,
                    error = pageError,
                    onClearError = viewModel::clearPageError,
                    onDeleteWithPassword = { password -> viewModel.deleteAccount(password, onDeleted = popPage) },
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
                        viewModel.saveRole(newRole) { replaceCurrent(SettingsPage.Subjects(afterChange = true)) }
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
                        viewModel.setSchoolLanguage(newLanguage) { replaceCurrent(SettingsPage.Subjects(afterChange = true)) }
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
                            else popPage()
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
                            else popPage()
                        }
                    },
                    today = current.today ?: Clock.System.todayIn(TimeZone.currentSystemDefault()),
                )
            }
            entry<SettingsPage.Goal> { page ->
                val current = state ?: return@entry
                SettingsGoalPage(
                    currentGoal = current.profile.dailyGoalMinutes,
                    afterRoleChange = page.afterRoleChange,
                    busy = busy,
                    onBack = done,
                    onSave = { minutes -> viewModel.saveDailyGoal(minutes, onSaved = popPage) },
                )
            }
            entry<SettingsPage.Reminder> {
                val current = state ?: return@entry
                SettingsReminderPage(
                    currentEnabled = current.profile.reminderEnabled,
                    currentTime = current.profile.reminderTime,
                    busy = busy,
                    onBack = done,
                    onSave = { time -> viewModel.saveReminderTime(time, onSaved = popPage) },
                )
            }
        },
    )
}
