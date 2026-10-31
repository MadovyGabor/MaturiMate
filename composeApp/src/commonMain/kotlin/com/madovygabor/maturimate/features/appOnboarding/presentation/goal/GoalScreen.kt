package com.madovygabor.maturimate.features.appOnboarding.presentation.goal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.uiComponents.AppChoiceChip
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingProgress
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingQuestionViewModel
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingStep
import com.madovygabor.maturimate.features.appOnboarding.presentation.QuestionUiState
import com.madovygabor.maturimate.features.appOnboarding.presentation.components.OnboardingAction
import com.madovygabor.maturimate.features.appOnboarding.presentation.components.OnboardingScaffold
import com.madovygabor.maturimate.features.appOnboarding.presentation.onboardingSteps
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import kotlinx.coroutines.flow.first
import maturimate.composeapp.generated.resources.Res
import maturimate.composeapp.generated.resources.common_next
import maturimate.composeapp.generated.resources.goal_minutes
import maturimate.composeapp.generated.resources.goal_title
import maturimate.composeapp.generated.resources.goal_why
import maturimate.composeapp.generated.resources.goal_year_hint
import maturimate.composeapp.generated.resources.onboarding_change_later
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

/** The daily goals offered, in minutes; [DEFAULT_GOAL] is preselected. */
val GOAL_OPTIONS = listOf(5, 10, 15, 20)
const val DEFAULT_GOAL = 10

/** Practice in a year at this daily goal, in whole hours. */
fun hoursPerYear(minutesPerDay: Int): Int = (minutesPerDay * 365 + 30) / 60

/** The daily goal question (students and learners); 10 minutes is preselected. */
class GoalViewModel(
    repository: UserProfileRepository,
    snackbarController: SnackbarController,
) : OnboardingQuestionViewModel<Int>(
    snackbarController,
    load = { repository.profile.first()?.dailyGoalMinutes ?: DEFAULT_GOAL },
    save = repository::saveDailyGoal,
)

/** The daily goal step: how many minutes a day, with what that means in a year. */
@Composable
fun GoalScreen(
    role: UserRole?,
    onBack: () -> Unit,
    onNext: () -> Unit,
    viewModel: GoalViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    GoalContent(state, role, viewModel::onSelected, onBack, onNext = { viewModel.onNext { onNext() } })
}

@Composable
fun GoalContent(
    state: QuestionUiState<Int>,
    role: UserRole?,
    onSelected: (Int) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    OnboardingScaffold(
        actions = listOf(OnboardingAction(stringResource(Res.string.common_next), onNext, enabled = state.canContinue)),
        progress = OnboardingProgress(OnboardingStep.GOAL, onboardingSteps(role), onBack),
    ) {
        Text(stringResource(Res.string.goal_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(stringResource(Res.string.goal_why), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // two chips a row on a phone, all four from the medium width, as in the prototype
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val perRow = if (maxWidth >= MaterialTheme.layout.breakpointMedium) GOAL_OPTIONS.size else 2
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space2),
            ) {
                GOAL_OPTIONS.chunked(perRow).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space2)) {
                        row.forEach { minutes ->
                            AppChoiceChip(
                                label = pluralStringResource(Res.plurals.goal_minutes, minutes, minutes),
                                selected = state.selected == minutes,
                                onClick = { onSelected(minutes) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
        state.selected?.let { minutes ->
            val hours = hoursPerYear(minutes)
            Text(
                pluralStringResource(Res.plurals.goal_year_hint, hours, minutes, hours),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(stringResource(Res.string.onboarding_change_later), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
@Preview
private fun GoalContentPreview() {
    AppTheme(darkTheme = false) { GoalContent(QuestionUiState(10), UserRole.STUDENT, {}, {}, {}) }
}
