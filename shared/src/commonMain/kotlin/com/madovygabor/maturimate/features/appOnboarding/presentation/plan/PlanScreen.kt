package com.madovygabor.maturimate.features.appOnboarding.presentation.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.utils.longDate
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingProgress
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingStep
import com.madovygabor.maturimate.features.appOnboarding.presentation.components.OnboardingAction
import com.madovygabor.maturimate.features.appOnboarding.presentation.components.OnboardingScaffold
import com.madovygabor.maturimate.features.appOnboarding.presentation.goal.hoursPerYear
import com.madovygabor.maturimate.features.appOnboarding.presentation.onboardingSteps
import com.madovygabor.maturimate.features.appOnboarding.presentation.reminder.hoursAndMinutes
import com.madovygabor.maturimate.features.appOnboarding.presentation.subjects.label
import com.madovygabor.maturimate.features.userProfile.domain.CatalogSubject
import com.madovygabor.maturimate.features.userProfile.domain.StudyEstimate
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.common_next
import maturimate.shared.generated.resources.plan_change_later
import maturimate.shared.generated.resources.plan_daily_goal
import maturimate.shared.generated.resources.plan_days_unit
import maturimate.shared.generated.resources.plan_hours_unit
import maturimate.shared.generated.resources.plan_minutes_unit
import maturimate.shared.generated.resources.plan_student_daily
import maturimate.shared.generated.resources.plan_total_label
import maturimate.shared.generated.resources.plan_year_label
import maturimate.shared.generated.resources.plan_exam_date
import maturimate.shared.generated.resources.plan_reminder
import maturimate.shared.generated.resources.plan_student_week
import maturimate.shared.generated.resources.plan_title
import maturimate.shared.generated.resources.plan_until_exam
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel


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
            UserRole.STUDENT -> state.estimate?.let { estimate ->
                val days = estimate.daysLeft
                val hours = estimate.totalHours
                // the two big numbers: the days until the exam and all the practice on the way; the rest is smaller
                PlanCard(
                    first = PlanNumber(stringResource(Res.string.plan_until_exam), days, pluralStringResource(Res.plurals.plan_days_unit, days)),
                    second = PlanNumber(stringResource(Res.string.plan_total_label), hours, pluralStringResource(Res.plurals.plan_hours_unit, hours)),
                    date = stringResource(Res.string.plan_exam_date, longDate(estimate.examDate)),
                )
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space2)) {
                    Fact(Icons.Rounded.Timer, pluralStringResource(Res.plurals.plan_student_daily, minutes, minutes))
                    Fact(Icons.Rounded.Schedule, pluralStringResource(Res.plurals.plan_student_week, estimate.hoursPerWeek, estimate.hoursPerWeek))
                }
            }
            UserRole.LEARNER -> {
                val hours = hoursPerYear(minutes)
                PlanCard(
                    first = PlanNumber(stringResource(Res.string.plan_daily_goal), minutes, pluralStringResource(Res.plurals.plan_minutes_unit, minutes)),
                    second = PlanNumber(stringResource(Res.string.plan_year_label), hours, pluralStringResource(Res.plurals.plan_hours_unit, hours)),
                )
            }
            else -> Unit
        }
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space2)) {
            state.subjects.forEach { planned ->
                Fact(Icons.AutoMirrored.Rounded.MenuBook, stringResource(planned.label))
            }
            state.reminder?.let { Fact(Icons.Rounded.Notifications, stringResource(Res.string.plan_reminder, it.hoursAndMinutes())) }
        }
        Text(stringResource(Res.string.plan_change_later), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One of the big numbers of the plan: [number] with its [unit] and a short [label], for example 104, "nap", "Érettségiig". */
private data class PlanNumber(val label: String, val number: Int, val unit: String)

/** The plan's main numbers side by side, with the exam [date] under them when there is one. */
@Composable
private fun PlanCard(first: PlanNumber, second: PlanNumber, date: String? = null) {
    val spacing = MaterialTheme.spacing
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.space4),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.space4)) {
                BigNumber(first, Modifier.weight(1f))
                BigNumber(second, Modifier.weight(1f))
            }
            if (date != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.space2),
                ) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(MaterialTheme.layout.iconSmall))
                    Text(date, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
    }
}

/** A big number with its unit next to it on one baseline, and the label above; read as one phrase by a screen reader. */
@Composable
private fun BigNumber(value: PlanNumber, modifier: Modifier) {
    Column(modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space1)) {
        Text(value.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space2)) {
            Text(value.number.toString(), modifier = Modifier.alignByBaseline(), style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(value.unit, modifier = Modifier.alignByBaseline(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
        }
    }
}

@Composable
@Preview
private fun PlanContentPreview() {
    AppTheme(darkTheme = false) {
        PlanContent(
            PlanUiState(
                role = UserRole.STUDENT, estimate = StudyEstimate(LocalDate(2027, 3, 15), 135, 23, 1), dailyGoalMinutes = 10,
                subjects = listOf(CatalogSubject.HUNGARIAN, CatalogSubject.ENGLISH_B2),
                reminder = LocalTime(18, 0),
            ),
            {}, {},
        )
    }
}
