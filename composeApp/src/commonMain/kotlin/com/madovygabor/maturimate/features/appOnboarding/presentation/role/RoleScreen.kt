package com.madovygabor.maturimate.features.appOnboarding.presentation.role

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.CoPresent
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.uiComponents.AppChoiceCard
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingProgress
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingStep
import com.madovygabor.maturimate.features.appOnboarding.presentation.QuestionUiState
import com.madovygabor.maturimate.features.appOnboarding.presentation.components.OnboardingAction
import com.madovygabor.maturimate.features.appOnboarding.presentation.components.OnboardingScaffold
import com.madovygabor.maturimate.features.appOnboarding.presentation.onboardingSteps
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import maturimate.composeapp.generated.resources.Res
import maturimate.composeapp.generated.resources.common_next
import maturimate.composeapp.generated.resources.onboarding_change_later
import maturimate.composeapp.generated.resources.role_learner
import maturimate.composeapp.generated.resources.role_learner_desc
import maturimate.composeapp.generated.resources.role_student
import maturimate.composeapp.generated.resources.role_student_desc
import maturimate.composeapp.generated.resources.role_teacher
import maturimate.composeapp.generated.resources.role_teacher_desc
import maturimate.composeapp.generated.resources.role_title
import maturimate.composeapp.generated.resources.role_why
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

/**
 * Step 2 of the onboarding: what the app is for. The answer decides which questions follow;
 * a student goes on with [onStudentNext], learners and teachers finish here for now.
 */
@Composable
fun RoleScreen(
    onBack: () -> Unit,
    onStudentNext: () -> Unit,
    viewModel: RoleViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    RoleContent(
        state = state,
        onSelected = viewModel::onSelected,
        onBack = onBack,
        onNext = { viewModel.onNext { role -> if (role == UserRole.STUDENT) onStudentNext() } },
    )
}

private data class RoleOption(val role: UserRole, val icon: ImageVector, val title: StringResource, val description: StringResource)

private val roleOptions = listOf(
    RoleOption(UserRole.STUDENT, Icons.Rounded.School, Res.string.role_student, Res.string.role_student_desc),
    RoleOption(UserRole.LEARNER, Icons.AutoMirrored.Rounded.MenuBook, Res.string.role_learner, Res.string.role_learner_desc),
    RoleOption(UserRole.TEACHER, Icons.Rounded.CoPresent, Res.string.role_teacher, Res.string.role_teacher_desc),
)

@Composable
fun RoleContent(
    state: QuestionUiState<UserRole>,
    onSelected: (UserRole) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    OnboardingScaffold(
        actions = listOf(OnboardingAction(stringResource(Res.string.common_next), onNext, enabled = state.canContinue)),
        // the step count follows the role as soon as one is picked
        progress = OnboardingProgress(OnboardingStep.ROLE, onboardingSteps(state.selected), onBack),
    ) {
        Text(
            text = stringResource(Res.string.role_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(Res.string.role_why),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space3),
        ) {
            roleOptions.forEach { option ->
                AppChoiceCard(
                    badgeIcon = option.icon,
                    title = stringResource(option.title),
                    description = stringResource(option.description),
                    selected = state.selected == option.role,
                    onClick = { onSelected(option.role) },
                )
            }
        }
        Text(
            text = stringResource(Res.string.onboarding_change_later),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
@Preview
private fun RoleContentPreview() {
    AppTheme(darkTheme = false) {
        RoleContent(QuestionUiState(selected = UserRole.STUDENT), onSelected = {}, onBack = {}, onNext = {})
    }
}
