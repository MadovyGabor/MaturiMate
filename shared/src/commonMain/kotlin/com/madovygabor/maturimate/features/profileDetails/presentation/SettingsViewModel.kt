package com.madovygabor.maturimate.features.profileDetails.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.core.presentation.utils.UiText
import com.madovygabor.maturimate.core.presentation.utils.retryReads
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarKind
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingStep
import com.madovygabor.maturimate.features.appOnboarding.presentation.goal.DEFAULT_GOAL
import com.madovygabor.maturimate.features.appOnboarding.presentation.isAnswered
import com.madovygabor.maturimate.features.appOnboarding.presentation.onboardingSteps
import com.madovygabor.maturimate.features.auth.domain.AccountSettings
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.domain.AuthRepository
import com.madovygabor.maturimate.features.auth.domain.DeletionProof
import com.madovygabor.maturimate.features.auth.presentation.toUiText
import com.madovygabor.maturimate.features.content.domain.ContentError
import com.madovygabor.maturimate.features.content.domain.ContentUpdateState
import com.madovygabor.maturimate.features.content.domain.ContentUpdater
import com.madovygabor.maturimate.features.learning.domain.ProgressRepository
import com.madovygabor.maturimate.features.sync.domain.SyncEngine
import com.madovygabor.maturimate.features.sync.domain.SyncError
import com.madovygabor.maturimate.features.sync.domain.SyncOutcome
import com.madovygabor.maturimate.features.sync.domain.SyncReason
import com.madovygabor.maturimate.features.sync.domain.SyncState
import com.madovygabor.maturimate.features.sync.domain.SyncTriggers
import com.madovygabor.maturimate.features.userProfile.domain.DataError
import com.madovygabor.maturimate.features.userProfile.domain.ExamCalendar
import com.madovygabor.maturimate.features.userProfile.domain.ExamDateRepository
import com.madovygabor.maturimate.features.userProfile.domain.StudyEstimate
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import com.madovygabor.maturimate.features.userProfile.domain.UserSubject
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.error_io
import maturimate.shared.generated.resources.settings_app_language_saved
import maturimate.shared.generated.resources.settings_date_saved
import maturimate.shared.generated.resources.settings_export_done
import maturimate.shared.generated.resources.settings_export_shared
import maturimate.shared.generated.resources.settings_goal_saved
import maturimate.shared.generated.resources.settings_name_done
import maturimate.shared.generated.resources.settings_password_done
import maturimate.shared.generated.resources.settings_reminder_off_saved
import maturimate.shared.generated.resources.settings_reminder_on_saved
import maturimate.shared.generated.resources.settings_reminder_saved
import maturimate.shared.generated.resources.settings_reset_done
import maturimate.shared.generated.resources.settings_subjects_saved
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** The settings: shows saved answers, manages edit pages and confirmation dialogs, and triggers sync/content updates. */
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
    /** Minimum visible loading duration when the user manually triggers sync or content check. */
    private val manualFeedbackMs: Long = MANUAL_FEEDBACK_MS,
) : ViewModel() {

    private val _busy = MutableStateFlow(false)
    private val _dialogs = MutableStateFlow(SettingsDialogState())
    private val _passwordState = MutableStateFlow(PasswordPageState())
    private val _pageError = MutableStateFlow<AuthError?>(null)
    private val _syncFeedback = MutableStateFlow(false)
    private val _contentFeedback = MutableStateFlow(false)
    private val _guestPhone = MutableStateFlow<Boolean?>(null)
    private var foreignSessionChecked = false

    /** The open dialog and the error of its last action. */
    val dialogs: StateFlow<SettingsDialogState> = _dialogs.asStateFlow()

    /** State of the password change page (form vs reauthentication code). */
    val passwordState: StateFlow<PasswordPageState> = _passwordState.asStateFlow()

    /** Field/page error for the name and delete-account pages. */
    val pageError: StateFlow<AuthError?> = _pageError.asStateFlow()

    /** True while an edit save or account action runs. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Separate sync state stream so status ticks only recompose the Data section. */
    val syncState: StateFlow<SyncState> = combine(
        syncEngine?.state ?: flowOf(SyncState()),
        _syncFeedback,
    ) { sync, feedback ->
        sync.copy(running = sync.running || feedback)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SyncState())

    /** Separate content update state stream so download ticks only recompose the Data section. */
    val contentState: StateFlow<ContentUpdateState> = combine(
        contentUpdater?.state ?: flowOf(ContentUpdateState()),
        _contentFeedback,
    ) { content, feedback ->
        content.copy(downloading = content.downloading || feedback)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ContentUpdateState())

    init {
        viewModelScope.launch { contentUpdater?.refresh() }
    }

    /** Null until the profile is read, or when it cannot be read. */
    val state: StateFlow<SettingsUiState?> = combine(
        repository.profile,
        examDates.dates,
        _guestPhone,
    ) { profile, dates, cachedGuest ->
        // Run once on entry so a leftover foreign session is ended before showing the account, without repeating on every emit.
        if (!foreignSessionChecked) {
            foreignSessionChecked = true
            accountSettings.endForeignSession()
        }
        val guestPhone = cachedGuest ?: accountSettings.isGuestPhone().also { _guestPhone.value = it }
        // Read again on profile/date changes so the day updates after a night in the background.
        val today = fixedToday ?: Clock.System.todayIn(TimeZone.currentSystemDefault())
        profile?.let {
            SettingsUiState(
                profile = it,
                today = today,
                canShareExport = accountSettings.canShareExport,
                daysLeft = it.graduationDate?.takeIf { _ -> it.role == UserRole.STUDENT }
                    ?.let { date -> StudyEstimate.of(date, ExamCalendar(dates), today, it.dailyGoalMinutes ?: DEFAULT_GOAL)?.daysLeft },
                account = auth.currentUser()?.takeIf { !guestPhone },
                guestPhone = guestPhone,
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

    /** The app's labels in [language]; the app is rebuilt in it. Shows a snackbar on success or failure. */
    fun setAppLanguage(language: TeachingLanguage) {
        if (state.value?.profile?.uiLanguage == language) return
        work {
            if (repository.saveUiLanguage(language) is OperationResult.Success) {
                say(Res.string.settings_app_language_saved)
            } else {
                sayError(Res.string.error_io)
            }
        }
    }

    /**
     * The school's language. Its subjects differ (Slovak at a Hungarian school is another exam), so once the language is
     * saved and really changed, [onChanged] runs and the screen asks for the subjects again. A failed save runs nothing
     * and shows an error snackbar.
     */
    fun setSchoolLanguage(language: TeachingLanguage, onChanged: () -> Unit) {
        if (state.value?.profile?.teachingLanguage == language) return
        saveProfileChange(null, onSaved = onChanged) {
            repository.saveSchoolLanguage(language)
        }
    }

    fun saveRole(role: UserRole, onSaved: () -> Unit) =
        saveProfileChange(null, onSaved) { repository.saveRole(role) }

    fun saveSubjects(subjects: List<UserSubject>, onSaved: () -> Unit) =
        saveProfileChange(Res.string.settings_subjects_saved, onSaved) { repository.saveSubjects(subjects) }

    fun saveGraduationDate(date: LocalDate, onSaved: () -> Unit) =
        saveProfileChange(Res.string.settings_date_saved, onSaved) { repository.saveGraduationDate(date) }

    fun saveDailyGoal(minutes: Int, onSaved: () -> Unit) =
        saveProfileChange(Res.string.settings_goal_saved, onSaved) { repository.saveDailyGoal(minutes) }

    fun saveReminderTime(time: LocalTime, onSaved: () -> Unit) =
        saveProfileChange(Res.string.settings_reminder_saved, onSaved) { repository.saveReminder(true, time) }

    private fun saveProfileChange(
        successMessage: StringResource?,
        onSaved: () -> Unit,
        save: suspend () -> OperationResult<Unit, DataError>,
    ) = work {
        if (save() is OperationResult.Success) {
            onSaved()
            successMessage?.let { viewModelScope.launch { say(it) } }
        } else {
            viewModelScope.launch { sayError(Res.string.error_io) }
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

    fun clearPageError() {
        _pageError.value = null
    }

    fun clearPasswordError() {
        _passwordState.update { it.copy(error = null) }
    }

    fun resetPasswordPage() {
        _passwordState.value = PasswordPageState()
    }

    fun backToPasswordForm() {
        _passwordState.update { it.copy(step = PasswordStep.FORM, error = null) }
    }

    /** "Haladás újrakezdése": the finished lessons and tests, the answers and the mistakes go. */
    fun resetProgress() = work {
        if (progress.resetProgress() is OperationResult.Success) {
            closed(SettingsDialog.RESET_CONFIRM)
            say(Res.string.settings_reset_done)
        } else {
            failed(SettingsDialog.RESET_CONFIRM, AuthError.UNKNOWN)
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
     * goes on the server for good, then here. A failure is said on the page; a success runs [onDeleted].
     */
    fun deleteAccount(password: String, onDeleted: () -> Unit = {}) = work { delete(DeletionProof.Password(password), onDeleted) }

    private suspend fun delete(proof: DeletionProof, onDeleted: () -> Unit = {}) {
        when (val result = accountSettings.deleteAccount(proof)) {
            is OperationResult.Success -> {
                _pageError.value = null
                onDeleted()
            }
            is OperationResult.Error -> _pageError.value = result.error
        }
    }

    /**
     * "A fiók törlése" of an account made with Google: its proof is a fresh Google sign-in of the same account. Once the
     * session is remembered (and the sync held), [openGoogle] starts that sign-in; the page waits until one of the Google
     * callbacks below comes. A failure to start is said on the page and Google is not opened.
     */
    fun startGoogleDeletion(openGoogle: () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        _pageError.value = null
        viewModelScope.launch {
            when (val started = accountSettings.startFreshSignInProof()) {
                is OperationResult.Success -> openGoogle()
                is OperationResult.Error -> {
                    _pageError.value = started.error
                    _busy.value = false
                }
            }
        }
    }

    /** The Google sign-in is back: a new session of the same account deletes it; another account is refused. */
    fun onGoogleDeletionSignedIn(onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                when (val proof = accountSettings.finishFreshSignInProof()) {
                    is OperationResult.Success -> delete(DeletionProof.FreshSignIn, onDeleted)
                    is OperationResult.Error -> _pageError.value = proof.error
                }
            } finally {
                _busy.value = false
            }
        }
    }

    /**
     * The Google sign-in failed: said on the page; nothing was deleted. A session of another account that came in anyway
     * is ended and this phone's put back; the sync goes on.
     */
    fun onGoogleDeletionFailed(error: AuthError) {
        _pageError.value = error
        endGoogleDeletion()
    }

    /** The Google account picker was closed: nothing happened (the proof is ended as after a failure). */
    fun onGoogleDeletionClosed() = endGoogleDeletion()

    private fun endGoogleDeletion() {
        // the cancel runs to its end even when the settings go away (it releases the sync hold)
        viewModelScope.launch { withContext(NonCancellable) { accountSettings.cancelFreshSignInProof() } }
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

    /** "Tartalom frissítése": checks for new releases on the server first when nothing is queued, then downloads. */
    fun updateContent() {
        if (_contentFeedback.value || contentUpdater?.state?.value?.downloading == true) return
        viewModelScope.launch {
            _contentFeedback.value = true
            val feedbackTimer = if (manualFeedbackMs > 0L) launch { delay(manualFeedbackMs) } else null
            try {
                val updater = contentUpdater ?: return@launch
                if (updater.state.value.pending.isEmpty() && syncEngine != null) {
                    when (val outcome = syncEngine.sync(SyncReason.MANUAL)) {
                        is SyncOutcome.Done -> updater.onReleases(outcome.contentReleases, allowMobileData = true)
                        is SyncOutcome.Failed -> updater.onCheckFailed(outcome.error.toContentError())
                        SyncOutcome.Queued, SyncOutcome.NotApplicable -> updater.updateNow()
                    }
                } else {
                    updater.updateNow()
                }
            } finally {
                feedbackTimer?.join()
                _contentFeedback.value = false
            }
        }
    }

    /** "Szinkronizálás": a full run now. */
    fun syncNow() {
        if (_syncFeedback.value || syncEngine?.state?.value?.running == true) return
        syncTriggers?.onManual()
        if (manualFeedbackMs > 0L) {
            viewModelScope.launch {
                _syncFeedback.value = true
                try { delay(manualFeedbackMs) } finally { _syncFeedback.value = false }
            }
        }
    }

    /** A new name, on this phone (synced later). A failure is said in the dialog; a success closes it with a snackbar. */
    fun saveNames(firstName: String, lastName: String) = work {
        when (val result = accountSettings.saveNames(firstName, lastName)) {
            is OperationResult.Success -> {
                closed(SettingsDialog.NAME)
                say(Res.string.settings_name_done)
            }
            is OperationResult.Error -> failed(SettingsDialog.NAME, result.error)
        }
    }

    /** Sends (or resends) the 6-digit reauthentication code to the account's e-mail. */
    fun sendPasswordCode() = work {
        when (val result = accountSettings.sendPasswordCode()) {
            is OperationResult.Success -> _passwordState.update { it.copy(step = PasswordStep.CODE, codeSent = true, error = null) }
            is OperationResult.Error -> _passwordState.update { it.copy(error = result.error) }
        }
    }

    /**
     * Changes the password. On a fresh session (< 24h) [code] is null and the change succeeds directly.
     * When the server returns [AuthError.REAUTHENTICATION_REQUIRED], a 6-digit code is sent automatically
     * and the page switches to [PasswordStep.CODE].
     */
    fun changePassword(oldPassword: String, newPassword: String, code: String? = null, onSaved: () -> Unit = {}) = work {
        when (val result = accountSettings.changePassword(oldPassword, code, newPassword)) {
            is OperationResult.Success -> {
                _passwordState.value = PasswordPageState()
                onSaved()
                say(Res.string.settings_password_done)
            }
            is OperationResult.Error -> {
                if (result.error == AuthError.REAUTHENTICATION_REQUIRED && code == null) {
                    val sent = accountSettings.sendPasswordCode()
                    _passwordState.value = PasswordPageState(
                        step = PasswordStep.CODE,
                        codeSent = sent is OperationResult.Success,
                        error = (sent as? OperationResult.Error)?.error,
                    )
                } else {
                    _passwordState.update { it.copy(error = result.error) }
                }
            }
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

    /** One change of the account or profile at a time. */
    private fun work(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try { block() } finally { _busy.value = false }
        }
    }

    /**
     * Turns the reminder on or off; the time is kept. Without a saved time the screen opens the reminder page
     * instead (returns false).
     */
    fun setReminder(enabled: Boolean): Boolean {
        val profile = state.value?.profile ?: return false
        if (enabled && profile.reminderTime == null) return false
        if (_busy.value) return true
        work {
            if (repository.saveReminder(enabled, profile.reminderTime) is OperationResult.Success) {
                say(if (enabled) Res.string.settings_reminder_on_saved else Res.string.settings_reminder_off_saved)
            } else {
                sayError(Res.string.error_io)
            }
        }
        return true
    }

    private fun SyncError.toContentError(): ContentError = when (this) {
        SyncError.NETWORK -> ContentError.NETWORK
        SyncError.NOT_SIGNED_IN, SyncError.WRONG_ACCOUNT -> ContentError.NOT_SIGNED_IN
        else -> ContentError.UNKNOWN
    }

    companion object {
        /** Minimum visible spinner duration for manual Sync / Content Update taps so fast checks feel responsive. */
        const val MANUAL_FEEDBACK_MS = 700L
    }
}
