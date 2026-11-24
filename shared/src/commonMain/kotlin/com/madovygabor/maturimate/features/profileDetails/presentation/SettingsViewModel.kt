package com.madovygabor.maturimate.features.profileDetails.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madovygabor.maturimate.core.presentation.utils.retryReads
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingStep
import com.madovygabor.maturimate.features.appOnboarding.presentation.isAnswered
import com.madovygabor.maturimate.features.appOnboarding.presentation.onboardingSteps
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.auth.domain.AccountSettings
import com.madovygabor.maturimate.features.auth.domain.AccountUser
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.domain.DeletionProof
import com.madovygabor.maturimate.features.learning.domain.ProgressRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.madovygabor.maturimate.features.auth.domain.AuthRepository
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import com.madovygabor.maturimate.features.userProfile.domain.ExamCalendar
import com.madovygabor.maturimate.features.userProfile.domain.ExamDateRepository
import com.madovygabor.maturimate.features.userProfile.domain.StudyEstimate
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import com.madovygabor.maturimate.features.sync.domain.SyncEngine
import com.madovygabor.maturimate.features.content.domain.ContentUpdateState
import com.madovygabor.maturimate.features.content.domain.ContentUpdater
import com.madovygabor.maturimate.features.sync.domain.SyncState
import com.madovygabor.maturimate.features.sync.domain.SyncTriggers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import com.madovygabor.maturimate.features.appOnboarding.presentation.goal.DEFAULT_GOAL
import kotlinx.datetime.todayIn
import kotlinx.coroutines.flow.update
import org.jetbrains.compose.resources.StringResource
import com.madovygabor.maturimate.core.presentation.utils.UiText
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarKind
import com.madovygabor.maturimate.features.auth.presentation.toUiText
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.error_io
import maturimate.shared.generated.resources.settings_export_done
import maturimate.shared.generated.resources.settings_export_shared
import maturimate.shared.generated.resources.settings_name_done
import maturimate.shared.generated.resources.settings_password_done
import maturimate.shared.generated.resources.settings_reset_done
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** The learning answers of the settings, each opening its onboarding question, in the order of the onboarding. */
val LEARNING_STEPS = listOf(
    OnboardingStep.ROLE,
    OnboardingStep.SUBJECTS,
    OnboardingStep.DATE,
    OnboardingStep.GOAL,
)

/** The dialog open over the settings list. */
enum class SettingsDialog { NAME, PASSWORD, RESET, SIGN_OUT, SIGN_OUT_UNSYNCED, DELETE, EXPORT }

/** The steps of the password dialog: send the code; then the code, the current password and the new one twice. */
enum class PasswordStep { SEND, FORM }

/** Which dialog is open and how its last action ended; it lives here, so a rotation does not close the dialog. */
data class SettingsDialogState(
    val dialog: SettingsDialog? = null,
    /** The failure of the last action of the open dialog. */
    val error: AuthError? = null,
    val passwordStep: PasswordStep = PasswordStep.SEND,
)

data class SettingsUiState(
    val profile: UserProfile,
    /** Today, for "last synced today"; the same day the exam countdown uses. */
    val today: LocalDate? = null,
    /** Whether the export can also be shared (Android). */
    val canShareExport: Boolean = false,
    /** Days until the exam, for students with an exam date. */
    val daysLeft: Int?,
    /** The signed-in account; empty for a guest, also for a guest phone with a session. */
    val account: AccountUser? = null,
    /** Whether this phone is a guest's (the local user, the sync's rule); null when it is not known (no account settings). */
    val guestPhone: Boolean? = null,
    /** How the upload of the progress stands. */
    val sync: SyncState = SyncState(),
    /** Whether new content waits, downloads or is up to date. */
    val content: ContentUpdateState = ContentUpdateState(),
) {
    /** The learning answers this role has. */
    val learningSteps: List<OnboardingStep> get() = LEARNING_STEPS.filter { it in onboardingSteps(profile.role) }

    /**
     * A guest: the phone is a guest's, even while a session is open (an account that is not set up; "Fiók létrehozása"
     * then resumes its form). Without that knowledge: no account and no name yet.
     */
    val isGuest: Boolean get() = guestPhone ?: (account == null && profile.firstName == null)
}

/** The settings: shows the saved answers and changes the ones that need no question screen. */
@OptIn(ExperimentalTime::class)
class SettingsViewModel(
    private val repository: UserProfileRepository,
    private val auth: AuthRepository,
    private val accountSettings: AccountSettings,
    private val progress: ProgressRepository,
    private val snackbar: SnackbarController,
    /** The official exam dates: the days left follow them, as on home and in the plan. */
    private val examDates: ExamDateRepository,
    // The three below are whole machines (the sync, its triggers, the content download) that a screen test cannot build
    // without the server and the content database; without them the rows show their first state and do nothing.
    private val syncEngine: SyncEngine? = null,
    private val syncTriggers: SyncTriggers? = null,
    private val contentUpdater: ContentUpdater? = null,
    /** The day the countdown starts from; null reads the clock (tests fix it). */
    private val fixedToday: LocalDate? = null,
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    private val _dialogs = MutableStateFlow(SettingsDialogState())

    /** The open dialog and the error of its last action. */
    val dialogs: StateFlow<SettingsDialogState> = _dialogs.asStateFlow()

    /** True while a change of the account runs; the dialogs wait for it. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Null until the profile is read, or when it cannot be read. */
    val state: StateFlow<SettingsUiState?> = combine(
        repository.profile,
        syncEngine?.state ?: flowOf(SyncState()),
        contentUpdater?.state ?: flowOf(ContentUpdateState()),
        examDates.dates,
    ) { profile, sync, content, dates ->
            // a session of another account on this account's phone (a proof that never finished) is ended, never shown
            accountSettings.endForeignSession()
            val guestPhone = accountSettings.isGuestPhone()
            // read again at every change, so the day is the real one after a night in the background
            val today = fixedToday ?: Clock.System.todayIn(TimeZone.currentSystemDefault())
            profile?.let {
                SettingsUiState(
                    profile = it,
                    today = today,
                    canShareExport = accountSettings.canShareExport,
                    daysLeft = it.graduationDate?.takeIf { _ -> it.role == UserRole.STUDENT }
                        ?.let { date -> StudyEstimate.of(date, ExamCalendar(dates), today, it.dailyGoalMinutes ?: DEFAULT_GOAL)?.daysLeft },
                    // read with the profile, so a sign-up made from the profile shows here at once
                    account = auth.currentUser()?.takeIf { !guestPhone },
                    guestPhone = guestPhone,
                    sync = sync,
                    content = content,
                )
            }
        }
        .retryReads()
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * The questions still to answer after the role changed to [role] and its subjects were
     * chosen: a new student needs an exam date, a former teacher a daily goal.
     */
    fun missingAfterRoleChange(role: UserRole?): List<OnboardingStep> {
        val profile = state.value?.profile ?: return emptyList()
        return listOf(OnboardingStep.DATE, OnboardingStep.GOAL)
            .filter { it in onboardingSteps(role) && !it.isAnswered(profile) }
    }

    /** The app's labels in [language]; the app is rebuilt in it. A failed save is said in a snackbar. */
    fun setAppLanguage(language: TeachingLanguage) {
        viewModelScope.launch {
            if (repository.saveUiLanguage(language) is OperationResult.Error) sayError(Res.string.error_io)
        }
    }

    /**
     * The school's language. Its subjects differ (Slovak at a Hungarian school is another exam), so once the language is
     * saved and really changed, [onChanged] runs and the screen asks for the subjects again. A failed save runs nothing but
     * says so in a snackbar.
     */
    fun setSchoolLanguage(language: TeachingLanguage, onChanged: () -> Unit) {
        if (state.value?.profile?.teachingLanguage == language) return
        viewModelScope.launch {
            if (repository.saveSchoolLanguage(language) is OperationResult.Error) sayError(Res.string.error_io) else onChanged()
        }
    }

    /** The dialog [dialog] opens, with no earlier error. */
    fun openDialog(dialog: SettingsDialog) {
        _dialogs.value = SettingsDialogState(dialog = dialog)
    }

    fun closeDialog() {
        _dialogs.value = SettingsDialogState()
    }

    /** The error of the last try goes away (the user moved on, or typed again). */
    fun clearError() {
        _dialogs.update { it.copy(error = null) }
    }

    /** "Haladás újrakezdése": the finished lessons and tests, the answers and the mistakes go. */
    fun resetProgress() = work {
        if (progress.resetProgress() is OperationResult.Success) {
            closed(SettingsDialog.RESET)
            say(Res.string.settings_reset_done)
        } else {
            failed(SettingsDialog.RESET, AuthError.UNKNOWN)
        }
    }

    /**
     * Uploads what waits, signs out, and the local rows go: the app starts again with the welcome screen. When the upload
     * failed ([AuthError.NETWORK]) nothing was removed and the dialog asks whether to go on anyway ([force]); any other
     * failure is said in the dialog. Once it went through the dialog closes (the app is rebuilt a moment later).
     */
    fun signOut(force: Boolean = false) = work {
        val dialog = if (force) SettingsDialog.SIGN_OUT_UNSYNCED else SettingsDialog.SIGN_OUT
        val error = (accountSettings.signOut(force) as? OperationResult.Error)?.error
        when {
            error == null -> closed(dialog)
            error == AuthError.NETWORK && !force -> _dialogs.value = SettingsDialogState(dialog = SettingsDialog.SIGN_OUT_UNSYNCED)
            else -> failed(dialog, error)
        }
    }

    /**
     * "A fiók törlése" of an account with a password: the [password] is the proof (checked by the server), then the account
     * goes on the server for good, then here. A failure is said in the dialog, which stays open; a success closes it.
     */
    fun deleteAccount(password: String) = work { delete(DeletionProof.Password(password)) }

    private suspend fun delete(proof: DeletionProof) {
        when (val result = accountSettings.deleteAccount(proof)) {
            is OperationResult.Success -> closed(SettingsDialog.DELETE)
            is OperationResult.Error -> failed(SettingsDialog.DELETE, result.error)
        }
    }

    /**
     * "A fiók törlése" of an account made with Google: its proof is a fresh Google sign-in of the same account. Once the
     * session is remembered (and the sync held), [openGoogle] starts that sign-in; the dialog waits until one of the Google
     * callbacks below comes. A failure to start is said in the dialog and Google is not opened.
     */
    fun startGoogleDeletion(openGoogle: () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        _dialogs.update { it.copy(error = null) }
        viewModelScope.launch {
            when (val started = accountSettings.startFreshSignInProof()) {
                is OperationResult.Success -> openGoogle()
                is OperationResult.Error -> {
                    failed(SettingsDialog.DELETE, started.error)
                    _busy.value = false
                }
            }
        }
    }

    /** The Google sign-in is back: a new session of the same account deletes it; another account is refused. */
    fun onGoogleDeletionSignedIn() {
        viewModelScope.launch {
            try {
                when (val proof = accountSettings.finishFreshSignInProof()) {
                    is OperationResult.Success -> delete(DeletionProof.FreshSignIn)
                    is OperationResult.Error -> failed(SettingsDialog.DELETE, proof.error)
                }
            } finally {
                _busy.value = false
            }
        }
    }

    /**
     * The Google sign-in failed: said in the dialog; nothing was deleted. A session of another account that came in anyway
     * is ended and this phone's put back; the sync goes on.
     */
    fun onGoogleDeletionFailed(error: AuthError) {
        failed(SettingsDialog.DELETE, error)
        endGoogleDeletion()
    }

    /** The Google account picker was closed: nothing happened (the proof is ended as after a failure). */
    fun onGoogleDeletionClosed() = endGoogleDeletion()

    private fun endGoogleDeletion() {
        // the cancel runs to its end even when the settings go away (it releases the sync hold)
        viewModelScope.launch(NonCancellable) { accountSettings.cancelFreshSignInProof() }
        _busy.value = false
    }

    /**
     * "Adataim letöltése": saved to a file the user picks, or with [share] handed to the share sheet. Says where it went
     * ("saved" or "shared", truthfully); nothing when the user closed the picker; the error otherwise.
     */
    fun exportData(share: Boolean = false) = work {
        closed(SettingsDialog.EXPORT)
        when (val result = accountSettings.exportData(share)) {
            is OperationResult.Success -> when {
                share -> say(Res.string.settings_export_shared)
                result.data.isNotEmpty() -> say(Res.string.settings_export_done, result.data)
            }
            is OperationResult.Error -> snackbar.showSnackbar(result.error.toUiText(), kind = SnackbarKind.ERROR)
        }
    }

    /** "Tartalom frissítése": checks and downloads now (also over mobile data: the user asked). */
    fun updateContent() {
        contentUpdater?.requestUpdate()
    }

    /** "Szinkronizálás": a full run now. */
    fun syncNow() {
        syncTriggers?.onManual()
    }

    /** A new name, on the server and on this phone. A failure is said in the dialog, a success in a snackbar. */
    fun saveNames(firstName: String, lastName: String) = work {
        when (val result = accountSettings.saveNames(firstName, lastName)) {
            is OperationResult.Success -> {
                closed(SettingsDialog.NAME)
                say(Res.string.settings_name_done)
            }
            is OperationResult.Error -> failed(SettingsDialog.NAME, result.error)
        }
    }

    /** Sends the 6-digit code of the password change to the account's e-mail; the dialog then asks for it and the passwords. */
    fun sendPasswordCode() = work {
        when (val result = accountSettings.sendPasswordCode()) {
            is OperationResult.Success ->
                _dialogs.update { if (it.dialog == SettingsDialog.PASSWORD) it.copy(passwordStep = PasswordStep.FORM, error = null) else it }
            is OperationResult.Error -> failed(SettingsDialog.PASSWORD, result.error)
        }
    }

    /**
     * A new password, proved by both the [oldPassword] and the e-mailed [code]. A failure is said at its field and the
     * dialog keeps what was typed; a success closes it and says so in a snackbar.
     */
    fun changePassword(oldPassword: String, code: String, newPassword: String) = work {
        when (val result = accountSettings.changePassword(oldPassword, code, newPassword)) {
            is OperationResult.Success -> {
                closed(SettingsDialog.PASSWORD)
                say(Res.string.settings_password_done)
            }
            is OperationResult.Error -> failed(SettingsDialog.PASSWORD, result.error)
        }
    }

    private fun failed(dialog: SettingsDialog, error: AuthError) {
        _dialogs.update { if (it.dialog == dialog) it.copy(error = error) else it }
    }

    private fun closed(dialog: SettingsDialog) {
        _dialogs.update { if (it.dialog == dialog) SettingsDialogState() else it }
    }

    /** A message that says something worked (saved, done): the green one. */
    private suspend fun say(message: StringResource, vararg args: Any) {
        snackbar.showSnackbar(UiText.StringRes(message, arrayOf(*args)), kind = SnackbarKind.SUCCESS)
    }

    /** A message that says something failed: the red one. */
    private suspend fun sayError(message: StringResource) {
        snackbar.showSnackbar(UiText.StringRes(message), kind = SnackbarKind.ERROR)
    }

    /** One change of the account at a time. */
    private fun work(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try { block() } finally { _busy.value = false }
        }
    }

    /**
     * Turns the reminder on or off; the time is kept. Without a saved time the screen opens the reminder question
     * instead (returns false).
     */
    fun setReminder(enabled: Boolean): Boolean {
        val profile = state.value?.profile ?: return false
        if (enabled && profile.reminderTime == null) return false
        viewModelScope.launch {
            if (repository.saveReminder(enabled, profile.reminderTime) is OperationResult.Error) sayError(Res.string.error_io)
        }
        return true
    }
}
