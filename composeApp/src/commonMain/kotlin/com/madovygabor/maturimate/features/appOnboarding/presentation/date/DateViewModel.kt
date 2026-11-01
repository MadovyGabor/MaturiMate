package com.madovygabor.maturimate.features.appOnboarding.presentation.date

import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingQuestionViewModel
import com.madovygabor.maturimate.features.userProfile.domain.ExamDates
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The exam date question (students only). The next spring exam is preselected; a saved date
 * that has passed in the meantime is replaced by it.
 */
@OptIn(ExperimentalTime::class)
class DateViewModel(
    repository: UserProfileRepository,
    snackbarController: SnackbarController,
    val today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
) : OnboardingQuestionViewModel<LocalDate>(
    snackbarController,
    load = {
        repository.profile.first()?.graduationDate?.takeIf { ExamDates.isValid(it, today) }
            ?: ExamDates.nextSpringExams(today).first()
    },
    save = repository::saveGraduationDate,
) {
    /** The two spring exams offered as cards. */
    val suggestions: List<LocalDate> = ExamDates.nextSpringExams(today)
}
