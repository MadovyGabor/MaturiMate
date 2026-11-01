package com.madovygabor.maturimate.features.appOnboarding.presentation.language

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.uiComponents.AppChoiceCard
import com.madovygabor.maturimate.features.appOnboarding.presentation.LocalEditingAnswer
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingProgress
import com.madovygabor.maturimate.features.appOnboarding.presentation.nextLabel
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingStep
import com.madovygabor.maturimate.features.appOnboarding.presentation.QuestionUiState
import com.madovygabor.maturimate.features.appOnboarding.presentation.components.OnboardingAction
import com.madovygabor.maturimate.features.appOnboarding.presentation.onboardingSteps
import com.madovygabor.maturimate.features.appOnboarding.presentation.components.OnboardingScaffold
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.language_hu
import maturimate.shared.generated.resources.language_hu_desc
import maturimate.shared.generated.resources.language_sk
import maturimate.shared.generated.resources.language_sk_desc
import maturimate.shared.generated.resources.language_title
import maturimate.shared.generated.resources.language_why
import maturimate.shared.generated.resources.onboarding_change_later
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

/**
 * Step 1 of the onboarding: the teaching language of the student's school,
 * which also sets the language of the app.
 */
@Composable
fun LanguageScreen(
    onBack: () -> Unit,
    /** Called with the role saved before, if any: it decides the next step. */
    onNext: (UserRole?) -> Unit,
    viewModel: LanguageViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val role by viewModel.role.collectAsState()
    LanguageContent(
        state = state,
        role = role,
        onSelected = viewModel::onSelected,
        onBack = onBack,
        onNext = { viewModel.onNext { onNext(role) } },
    )
}

@Composable
fun LanguageContent(
    state: QuestionUiState<TeachingLanguage>,
    /** The saved role, if any: it sets the number of steps. */
    role: UserRole?,
    onSelected: (TeachingLanguage) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    OnboardingScaffold(
        actions = listOf(OnboardingAction(stringResource(nextLabel(LocalEditingAnswer.current)), onNext, enabled = state.canContinue)),
        progress = OnboardingProgress(OnboardingStep.LANGUAGE, onboardingSteps(role), onBack),
    ) {
        Text(
            text = stringResource(Res.string.language_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(Res.string.language_why),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier.selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space3),
        ) {
            // each option is written in its own language, whatever the app language is
            AppChoiceCard(
                badge = TeachingLanguage.HU.name,
                title = stringResource(Res.string.language_hu),
                description = stringResource(Res.string.language_hu_desc),
                selected = state.selected == TeachingLanguage.HU,
                onClick = { onSelected(TeachingLanguage.HU) },
            )
            AppChoiceCard(
                badge = TeachingLanguage.SK.name,
                title = stringResource(Res.string.language_sk),
                description = stringResource(Res.string.language_sk_desc),
                selected = state.selected == TeachingLanguage.SK,
                onClick = { onSelected(TeachingLanguage.SK) },
            )
        }
        if (!LocalEditingAnswer.current) Text(
            text = stringResource(Res.string.onboarding_change_later),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
@Preview
private fun LanguageContentPreview() {
    AppTheme(darkTheme = false) {
        LanguageContent(QuestionUiState(selected = TeachingLanguage.HU), role = null, onSelected = {}, onBack = {}, onNext = {})
    }
}
