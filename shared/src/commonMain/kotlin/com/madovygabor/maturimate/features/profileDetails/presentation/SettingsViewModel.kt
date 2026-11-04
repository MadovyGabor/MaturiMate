package com.madovygabor.maturimate.features.profileDetails.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madovygabor.maturimate.core.presentation.utils.retryReads
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingStep
import com.madovygabor.maturimate.features.appOnboarding.presentation.isAnswered
import com.madovygabor.maturimate.features.appOnboarding.presentation.onboardingSteps
import com.madovygabor.maturimate.features.auth.domain.AccountUser
import com.madovygabor.maturimate.features.auth.domain.AuthRepository
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
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
) : ViewModel() {

    /** Null until the profile is read, or when it cannot be read. */
    val state: StateFlow<SettingsUiState?> = repository.profile
        .map { profile ->
            profile?.let {
                SettingsUiState(
                    profile = it,
                    daysLeft = it.graduationDate?.takeIf { _ -> it.role == UserRole.STUDENT }?.let { date -> today.daysUntil(date).coerceAtLeast(0) },
                    // read with the profile, so a sign-up made from the profile shows here at once
                    account = auth?.currentUser(),
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
     * The school's language. Its subjects differ (Slovak at a Hungarian school is another exam), so a changed
     * language returns true and the screen asks for the subjects again.
     */
    fun setSchoolLanguage(language: TeachingLanguage, onChanged: () -> Unit) {
        if (state.value?.profile?.teachingLanguage == language) return
        viewModelScope.launch { repository.saveSchoolLanguage(language) }
        onChanged()
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
