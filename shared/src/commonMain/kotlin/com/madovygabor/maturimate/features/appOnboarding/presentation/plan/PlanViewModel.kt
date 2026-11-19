package com.madovygabor.maturimate.features.appOnboarding.presentation.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madovygabor.maturimate.features.appOnboarding.presentation.goal.DEFAULT_GOAL
import com.madovygabor.maturimate.features.userProfile.domain.CatalogSubject
import com.madovygabor.maturimate.features.userProfile.domain.ExamCalendar
import com.madovygabor.maturimate.features.userProfile.domain.ExamDateRepository
import com.madovygabor.maturimate.features.userProfile.domain.StudyEstimate
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/** The answers turned into a plan (docs/onboarding/README.md, screen 8). */
data class PlanUiState(
    val role: UserRole? = null,
    /** The exam date, days left and hours; students with an exam date only. */
    val estimate: StudyEstimate? = null,
    val dailyGoalMinutes: Int = DEFAULT_GOAL,
    val subjects: List<CatalogSubject> = emptyList(),
    /** The reminder time when the reminder is on. */
    val reminder: LocalTime? = null,
)

/** Reads the saved answers for the plan; nothing is saved here. */
class PlanViewModel(
    repository: UserProfileRepository,
    /** The official exam dates; without them the plan counts to the rule's date. */
    examDates: ExamDateRepository? = null,
    today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
) : ViewModel() {
    val state: StateFlow<PlanUiState?> = combine(repository.profile, examDates?.dates ?: flowOf(emptyList())) { profile, dates ->
        val minutes = profile?.dailyGoalMinutes ?: DEFAULT_GOAL
        PlanUiState(
            role = profile?.role,
            estimate = profile?.graduationDate?.takeIf { profile.role == UserRole.STUDENT }
                ?.let { StudyEstimate.of(it, ExamCalendar(dates), today, minutes) },
            dailyGoalMinutes = minutes,
            subjects = profile?.subjects.orEmpty().mapNotNull { saved -> CatalogSubject.byId(saved.subjectId) },
            reminder = profile?.reminderTime?.takeIf { profile.reminderEnabled },
        )
    }
        .catch { emit(PlanUiState()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
