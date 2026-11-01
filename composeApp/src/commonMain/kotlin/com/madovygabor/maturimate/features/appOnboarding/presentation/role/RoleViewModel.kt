package com.madovygabor.maturimate.features.appOnboarding.presentation.role

import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingQuestionViewModel
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import kotlinx.coroutines.flow.first

/** The role question; the answer decides which questions follow. */
class RoleViewModel(
    repository: UserProfileRepository,
    snackbarController: SnackbarController,
) : OnboardingQuestionViewModel<UserRole>(
    snackbarController,
    load = { repository.profile.first()?.role },
    save = repository::saveRole,
)
