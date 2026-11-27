package com.madovygabor.maturimate.features.profileDetails.presentation

import androidx.compose.runtime.Immutable
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingStep
import com.madovygabor.maturimate.features.appOnboarding.presentation.onboardingSteps
import com.madovygabor.maturimate.features.auth.domain.AccountUser
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.content.domain.ContentUpdateState
import com.madovygabor.maturimate.features.sync.domain.SyncState
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import kotlinx.datetime.LocalDate

/** The learning answers of the settings, in the order of the onboarding. */
val LEARNING_STEPS = listOf(
    OnboardingStep.ROLE,
    OnboardingStep.SUBJECTS,
    OnboardingStep.DATE,
    OnboardingStep.GOAL,
)

/** Short confirmation, choice, or name edit dialogs over the settings list. */
enum class SettingsDialog { NAME, RESET, RESET_CONFIRM, SIGN_OUT, SIGN_OUT_UNSYNCED, EXPORT, APP_LANGUAGE }

/** Steps of the password change page: form first; e-mail code only when the server demands reauthentication. */
enum class PasswordStep { FORM, CODE }

/** State of the password change page. */
@Immutable
data class PasswordPageState(
    val step: PasswordStep = PasswordStep.FORM,
    val codeSent: Boolean = false,
    val error: AuthError? = null,
)

/** Which dialog is open and how its last action ended; it lives here, so a rotation does not close the dialog. */
@Immutable
data class SettingsDialogState(
    val dialog: SettingsDialog? = null,
    val error: AuthError? = null,
)

@Immutable
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
    /** Fallback sync state for static previews/tests when `SettingsViewModel.syncState` is not passed. */
    val sync: SyncState = SyncState(),
    /** Fallback content state for static previews/tests when `SettingsViewModel.contentState` is not passed. */
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
