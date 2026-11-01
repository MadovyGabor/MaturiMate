package com.madovygabor.maturimate.features.profileDetails.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingStep
import com.madovygabor.maturimate.features.appOnboarding.presentation.isAnswered
import com.madovygabor.maturimate.features.appOnboarding.presentation.onboardingSteps
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** The onboarding answers the profile shows, each opening its question to change it. */
val PROFILE_STEPS = listOf(
    OnboardingStep.LANGUAGE,
    OnboardingStep.ROLE,
    OnboardingStep.SUBJECTS,
    OnboardingStep.DATE,
    OnboardingStep.GOAL,
    OnboardingStep.REMINDER,
)

data class ProfileUiState(
    val profile: UserProfile,
    /** Days until the exam, for students with an exam date. */
    val daysLeft: Int?,
) {
    /** The answers this role has, in the order of the onboarding. */
    val steps: List<OnboardingStep> get() = PROFILE_STEPS.filter { it in onboardingSteps(profile.role) }
}

/** Shows the saved answers; the question screens change them. */
@OptIn(ExperimentalTime::class)
class ProfileViewModel(
    repository: UserProfileRepository,
    private val today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
) : ViewModel() {

    /** Null until the profile is read, or when it cannot be read. */
    val state: StateFlow<ProfileUiState?> = repository.profile
        .map { profile ->
            profile?.let {
                ProfileUiState(
                    profile = it,
                    daysLeft = it.graduationDate?.takeIf { _ -> it.role == UserRole.STUDENT }?.let { date -> today.daysUntil(date).coerceAtLeast(0) },
                )
            }
        }
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
}
