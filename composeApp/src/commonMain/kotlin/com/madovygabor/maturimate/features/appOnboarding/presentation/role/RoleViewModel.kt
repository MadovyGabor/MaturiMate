package com.madovygabor.maturimate.features.appOnboarding.presentation.role

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingQuestionViewModel
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import kotlinx.coroutines.flow.first
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The role question. Learners and teachers finish the onboarding here for now: their next
 * questions come in steps 5.7 to 5.12, and finishing it takes the app to the home screen.
 */
@OptIn(ExperimentalTime::class)
class RoleViewModel(
    repository: UserProfileRepository,
    snackbarController: SnackbarController,
) : OnboardingQuestionViewModel<UserRole>(
    snackbarController,
    load = { repository.profile.first()?.role },
    save = { role ->
        val saved = repository.saveRole(role)
        if (saved is OperationResult.Success && role != UserRole.STUDENT) {
            repository.completeOnboarding(Clock.System.now())
        } else {
            saved
        }
    },
)
