package com.madovygabor.maturimate.features.appOnboarding.presentation.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingProgress
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingStep
import com.madovygabor.maturimate.features.appOnboarding.presentation.components.OnboardingAction
import com.madovygabor.maturimate.features.appOnboarding.presentation.goal.DEFAULT_GOAL
import com.madovygabor.maturimate.features.appOnboarding.presentation.goal.hoursPerYear
import com.madovygabor.maturimate.features.appOnboarding.presentation.components.OnboardingScaffold
import com.madovygabor.maturimate.features.appOnboarding.presentation.onboardingSteps
import com.madovygabor.maturimate.features.appOnboarding.presentation.reminder.hoursAndMinutes
import com.madovygabor.maturimate.features.appOnboarding.presentation.subjects.label
import com.madovygabor.maturimate.features.userProfile.domain.CatalogSubject
import com.madovygabor.maturimate.features.userProfile.domain.SubjectLevel
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.todayIn
import maturimate.composeapp.generated.resources.Res
import maturimate.composeapp.generated.resources.common_next
import maturimate.composeapp.generated.resources.goal_minutes
import maturimate.composeapp.generated.resources.plan_change_later
import maturimate.composeapp.generated.resources.plan_daily_goal
import maturimate.composeapp.generated.resources.plan_days
import maturimate.composeapp.generated.resources.plan_learner_year
import maturimate.composeapp.generated.resources.plan_reminder
import maturimate.composeapp.generated.resources.plan_student_total
import maturimate.composeapp.generated.resources.plan_title
import maturimate.composeapp.generated.resources.plan_until_exam
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** A subject in the plan, with its level when it has one. */
data class PlanSubject(val subject: CatalogSubject, val level: SubjectLevel?)

/** The answers turned into a plan (docs/onboarding/README.md, screen 8). */
data class PlanUiState(
    val role: UserRole? = null,
    /** Days until the exam; students only. */
    val daysLeft: Int? = null,
    val dailyGoalMinutes: Int = DEFAULT_GOAL,
    val subjects: List<PlanSubject> = emptyList(),
    /** The reminder time when the reminder is on. */
    val reminder: LocalTime? = null,
) {
    /** Practice until the exam at the daily goal, in whole hours. */
    val hoursUntilExam: Int? get() = daysLeft?.let { (it * dailyGoalMinutes + 30) / 60 }
}

/** Reads the saved answers for the plan; nothing is saved here. */
@OptIn(ExperimentalTime::class)
class PlanViewModel(
    repository: UserProfileRepository,
    today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
) : ViewModel() {
    val state: StateFlow<PlanUiState?> = repository.profile
        .map { profile ->
            PlanUiState(
                role = profile?.role,
                daysLeft = profile?.graduationDate?.takeIf { profile.role == UserRole.STUDENT }?.let { today.daysUntil(it).coerceAtLeast(0) },
                dailyGoalMinutes = profile?.dailyGoalMinutes ?: DEFAULT_GOAL,
                subjects = profile?.subjects.orEmpty().mapNotNull { saved -> CatalogSubject.byId(saved.subjectId)?.let { PlanSubject(it, saved.level) } },
                reminder = profile?.reminderTime?.takeIf { profile.reminderEnabled },
            )
        }
        .catch { emit(PlanUiState()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

/** The plan step: days until the exam, the daily goal, the subjects and the reminder. */
@Composable
fun PlanScreen(
    role: UserRole?,
    onBack: () -> Unit,
    onNext: () -> Unit,
    viewModel: PlanViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    PlanContent(state ?: PlanUiState(role = role), onBack, onNext)
}

@Composable
fun PlanContent(state: PlanUiState, onBack: () -> Unit, onNext: () -> Unit) {
    OnboardingScaffold(
        actions = listOf(OnboardingAction(stringResource(Res.string.common_next), onNext)),
        progress = OnboardingProgress(OnboardingStep.PLAN, onboardingSteps(state.role), onBack),
    ) {
        Text(stringResource(Res.string.plan_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        val minutes = state.dailyGoalMinutes
        when (state.role) {
            UserRole.STUDENT -> state.daysLeft?.let { days ->
                val hours = state.hoursUntilExam ?: 0
                PlanCard(
                    label = stringResource(Res.string.plan_until_exam),
                    value = pluralStringResource(Res.plurals.plan_days, days, days),
                    note = pluralStringResource(Res.plurals.plan_student_total, hours, minutes, hours),
                )
            }
            UserRole.LEARNER -> {
                val hours = hoursPerYear(minutes)
                PlanCard(
                    label = stringResource(Res.string.plan_daily_goal),
                    value = pluralStringResource(Res.plurals.goal_minutes, minutes, minutes),
                    note = pluralStringResource(Res.plurals.plan_learner_year, hours, hours),
                )
            }
            else -> Unit
        }
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space2)) {
            state.subjects.forEach { planned ->
                val name = stringResource(planned.subject.label)
                Fact(Icons.AutoMirrored.Rounded.MenuBook, planned.level?.let { "$name (${it.name})" } ?: name)
            }
            state.reminder?.let { Fact(Icons.Rounded.Notifications, stringResource(Res.string.plan_reminder, it.hoursAndMinutes())) }
        }
        Text(stringResource(Res.string.plan_change_later), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The plan's main number, for example the days until the exam. */
@Composable
private fun PlanCard(label: String, value: String, note: String) {
    val spacing = MaterialTheme.spacing
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.space1),
        ) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** One line of the plan with an icon. */
@Composable
private fun Fact(icon: ImageVector, text: String) {
    val spacing = MaterialTheme.spacing
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.space4, vertical = spacing.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.space3),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(MaterialTheme.layout.iconSmall))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
@Preview
private fun PlanContentPreview() {
    AppTheme(darkTheme = false) {
        PlanContent(
            PlanUiState(
                role = UserRole.STUDENT, daysLeft = 135, dailyGoalMinutes = 10,
                subjects = listOf(PlanSubject(CatalogSubject.HUNGARIAN, null), PlanSubject(CatalogSubject.ENGLISH, SubjectLevel.B2)),
                reminder = LocalTime(18, 0),
            ),
            {}, {},
        )
    }
}
