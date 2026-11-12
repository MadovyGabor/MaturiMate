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
import com.madovygabor.maturimate.features.learning.domain.ProgressRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.madovygabor.maturimate.features.auth.domain.AuthRepository
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
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
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** The learning answers of the settings, each opening its onboarding question, in the order of the onboarding. */
val LEARNING_STEPS = listOf(
    OnboardingStep.ROLE,
    OnboardingStep.SUBJECTS,
    OnboardingStep.DATE,
    OnboardingStep.GOAL,
)

data class SettingsUiState(
    val profile: UserProfile,
    /** Days until the exam, for students with an exam date. */
    val daysLeft: Int?,
    /** The signed-in account; empty for a guest. */
    val account: AccountUser? = null,
    /** How the upload of the progress stands. */
    val sync: SyncState = SyncState(),
    /** Whether new content waits, downloads or is up to date. */
    val content: ContentUpdateState = ContentUpdateState(),
) {
    /** The learning answers this role has. */
    val learningSteps: List<OnboardingStep> get() = LEARNING_STEPS.filter { it in onboardingSteps(profile.role) }

    /** A guest: no account and no name yet. */
    val isGuest: Boolean get() = account == null && profile.firstName == null
}

/** The settings: shows the saved answers and changes the ones that need no question screen. */
@OptIn(ExperimentalTime::class)
class SettingsViewModel(
    private val repository: UserProfileRepository,
    private val today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
    private val auth: AuthRepository? = null,
    private val accountSettings: AccountSettings? = null,
    private val progress: ProgressRepository? = null,
    private val syncEngine: SyncEngine? = null,
    private val syncTriggers: SyncTriggers? = null,
    private val contentUpdater: ContentUpdater? = null,
) : ViewModel() {

    private val _busy = MutableStateFlow(false)

    /** True while a change of the account runs; the dialogs wait for it. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** Null until the profile is read, or when it cannot be read. */
    val state: StateFlow<SettingsUiState?> = combine(
        repository.profile,
        syncEngine?.state ?: flowOf(SyncState()),
        contentUpdater?.state ?: flowOf(ContentUpdateState()),
    ) { profile, sync, content ->
            profile?.let {
                SettingsUiState(
                    profile = it,
                    daysLeft = it.graduationDate?.takeIf { _ -> it.role == UserRole.STUDENT }?.let { date -> today.daysUntil(date).coerceAtLeast(0) },
                    // read with the profile, so a sign-up made from the profile shows here at once
                    account = auth?.currentUser(),
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

    /** The app's labels in [language]; the app is rebuilt in it. */
    fun setAppLanguage(language: TeachingLanguage) {
        viewModelScope.launch { repository.saveUiLanguage(language) }
    }

    /**
     * The school's language. Its subjects differ (Slovak at a Hungarian school is another exam), so for a changed
     * language [onChanged] runs and the screen asks for the subjects again.
     */
    fun setSchoolLanguage(language: TeachingLanguage, onChanged: () -> Unit) {
        if (state.value?.profile?.teachingLanguage == language) return
        viewModelScope.launch { repository.saveSchoolLanguage(language) }
        onChanged()
    }

    /** "Haladás újrakezdése": the finished lessons and tests, the answers and the mistakes go. */
    fun resetProgress(onDone: (Boolean) -> Unit) = work {
        onDone(progress?.resetProgress() is OperationResult.Success)
    }

    /**
     * Uploads what waits, signs out, and the local rows go: the app starts again with the welcome screen. [onDone] gets the
     * error, if any; [AuthError.NETWORK] means the upload failed and nothing was removed, unless [force].
     */
    fun signOut(force: Boolean = false, onDone: (AuthError?) -> Unit) = work {
        onDone((accountSettings?.signOut(force) as? OperationResult.Error)?.error)
    }

    /** "A fiók törlése": on the server for good, then here. [onDone] gets the error, if any. */
    fun deleteAccount(onDone: (AuthError?) -> Unit) = work {
        onDone((accountSettings?.deleteAccount() as? OperationResult.Error)?.error)
    }

    /** "Adataim letöltése": [onDone] gets where the file went, or the error. */
    fun exportData(onDone: (String?, AuthError?) -> Unit) = work {
        when (val result = accountSettings?.exportData()) {
            is OperationResult.Success -> onDone(result.data, null)
            is OperationResult.Error -> onDone(null, result.error)
            null -> onDone(null, AuthError.UNKNOWN)
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

    /** A new name, on the server and on this phone. [onDone] gets the error, if any. */
    fun saveNames(firstName: String, lastName: String, onDone: (AuthError?) -> Unit) = work {
        onDone((accountSettings?.saveNames(firstName, lastName) as? OperationResult.Error)?.error)
    }

    /** A new password after the old one is checked. [onDone] gets the error, if any. */
    fun changePassword(oldPassword: String, newPassword: String, onDone: (AuthError?) -> Unit) = work {
        onDone((accountSettings?.changePassword(oldPassword, newPassword) as? OperationResult.Error)?.error)
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
        viewModelScope.launch { repository.saveReminder(enabled, profile.reminderTime) }
        return true
    }
}
