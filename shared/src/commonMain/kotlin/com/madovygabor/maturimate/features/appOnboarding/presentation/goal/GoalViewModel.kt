package com.madovygabor.maturimate.features.appOnboarding.presentation.goal

import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingQuestionViewModel
import com.madovygabor.maturimate.features.userProfile.domain.StudyEstimate
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import kotlinx.coroutines.flow.first

/** The daily goals offered, in minutes; [DEFAULT_GOAL] is preselected. */
val GOAL_OPTIONS = listOf(5, 10, 15, 20)
const val DEFAULT_GOAL = 10

/** Practice in a year at this daily goal, in whole hours (the rounding of [StudyEstimate]). */
fun hoursPerYear(minutesPerDay: Int): Int = StudyEstimate.hours(minutesPerDay, DAYS_PER_YEAR)

private const val DAYS_PER_YEAR = 365

/** The daily goal question (students and learners); 10 minutes is preselected. */
class GoalViewModel(
    repository: UserProfileRepository,
    snackbarController: SnackbarController,
) : OnboardingQuestionViewModel<Int>(
    snackbarController,
    load = { repository.profile.first()?.dailyGoalMinutes ?: DEFAULT_GOAL },
    save = repository::saveDailyGoal,
)
