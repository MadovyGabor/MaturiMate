package com.madovygabor.maturimate.features.appOnboarding.presentation.date

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.uiComponents.AppChoiceCard
import com.madovygabor.maturimate.core.presentation.uiComponents.AppDatePickerDialog
import com.madovygabor.maturimate.core.presentation.utils.longDate
import com.madovygabor.maturimate.features.appOnboarding.presentation.LocalEditingAnswer
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingProgress
import com.madovygabor.maturimate.features.appOnboarding.presentation.nextLabel
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingStep
import com.madovygabor.maturimate.features.appOnboarding.presentation.QuestionUiState
import com.madovygabor.maturimate.features.appOnboarding.presentation.components.OnboardingAction
import com.madovygabor.maturimate.features.appOnboarding.presentation.components.OnboardingScaffold
import com.madovygabor.maturimate.features.appOnboarding.presentation.onboardingSteps
import com.madovygabor.maturimate.features.userProfile.domain.ExamDates
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import maturimate.composeapp.generated.resources.Res
import maturimate.composeapp.generated.resources.common_cancel
import maturimate.composeapp.generated.resources.common_ok
import maturimate.composeapp.generated.resources.date_days_left
import maturimate.composeapp.generated.resources.date_other
import maturimate.composeapp.generated.resources.date_other_desc
import maturimate.composeapp.generated.resources.date_picker_title
import maturimate.composeapp.generated.resources.date_spring
import maturimate.composeapp.generated.resources.date_title
import maturimate.composeapp.generated.resources.date_why
import maturimate.composeapp.generated.resources.date_written_days
import maturimate.composeapp.generated.resources.onboarding_change_later
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

/** Step 4 for students: the exam date, from two spring exams or the calendar. */
@Composable
fun DateScreen(
    onBack: () -> Unit,
    onNext: () -> Unit,
    viewModel: DateViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    DateContent(
        state = state,
        today = viewModel.today,
        suggestions = viewModel.suggestions,
        onSelected = viewModel::onSelected,
        onBack = onBack,
        onNext = { viewModel.onNext { onNext() } },
    )
}

@Composable
fun DateContent(
    state: QuestionUiState<LocalDate>,
    today: LocalDate,
    suggestions: List<LocalDate>,
    onSelected: (LocalDate) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    val custom = state.selected?.takeIf { it !in suggestions }

    OnboardingScaffold(
        actions = listOf(OnboardingAction(stringResource(nextLabel(LocalEditingAnswer.current)), onNext, enabled = state.canContinue)),
        progress = OnboardingProgress(OnboardingStep.DATE, onboardingSteps(UserRole.STUDENT), onBack),
    ) {
        Text(stringResource(Res.string.date_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(stringResource(Res.string.date_why), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space3),
        ) {
            suggestions.forEach { exam ->
                val days = today.daysUntil(exam)
                AppChoiceCard(
                    // the last two digits of the year, as in the prototype
                    badge = (exam.year % 100).toString().padStart(2, '0'),
                    title = stringResource(Res.string.date_spring, exam.year),
                    description = pluralStringResource(Res.plurals.date_written_days, days, longDate(exam), days),
                    selected = state.selected == exam,
                    onClick = { onSelected(exam) },
                )
            }
            AppChoiceCard(
                badgeIcon = Icons.Rounded.CalendarMonth,
                title = stringResource(Res.string.date_other),
                description = custom?.let {
                    val days = today.daysUntil(it)
                    pluralStringResource(Res.plurals.date_days_left, days, longDate(it), days)
                } ?: stringResource(Res.string.date_other_desc),
                selected = custom != null,
                onClick = { picking = true },
            )
        }
        if (!LocalEditingAnswer.current) Text(stringResource(Res.string.onboarding_change_later), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (picking) {
        AppDatePickerDialog(
            title = stringResource(Res.string.date_picker_title),
            confirmText = stringResource(Res.string.common_ok),
            dismissText = stringResource(Res.string.common_cancel),
            initial = state.selected,
            earliest = today,
            latest = ExamDates.latest(today),
            onConfirm = { date -> onSelected(date); picking = false },
            onDismiss = { picking = false },
        )
    }
}

@Composable
@Preview
private fun DateContentPreview() {
    val today = LocalDate(2026, 10, 31)
    AppTheme(darkTheme = false) {
        DateContent(QuestionUiState(ExamDates.nextSpringExams(today).first()), today, ExamDates.nextSpringExams(today), {}, {}, {})
    }
}
