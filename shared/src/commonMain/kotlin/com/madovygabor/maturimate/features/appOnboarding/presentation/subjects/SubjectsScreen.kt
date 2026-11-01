package com.madovygabor.maturimate.features.appOnboarding.presentation.subjects

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.uiComponents.AppChoiceChip
import com.madovygabor.maturimate.features.appOnboarding.presentation.LocalEditingAnswer
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingProgress
import com.madovygabor.maturimate.features.appOnboarding.presentation.nextLabel
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingStep
import com.madovygabor.maturimate.features.appOnboarding.presentation.components.OnboardingAction
import com.madovygabor.maturimate.features.appOnboarding.presentation.components.OnboardingScaffold
import com.madovygabor.maturimate.features.appOnboarding.presentation.onboardingSteps
import com.madovygabor.maturimate.features.userProfile.domain.CatalogSubject
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.level_unknown
import maturimate.shared.generated.resources.onboarding_change_later
import maturimate.shared.generated.resources.subject_anj
import maturimate.shared.generated.resources.subject_anj_b1
import maturimate.shared.generated.resources.subject_anj_b2
import maturimate.shared.generated.resources.subject_hu_mjl
import maturimate.shared.generated.resources.subject_hu_sjsl
import maturimate.shared.generated.resources.subject_mat
import maturimate.shared.generated.resources.subject_sk_sjl
import maturimate.shared.generated.resources.subjects_compulsory
import maturimate.shared.generated.resources.subjects_english_level
import maturimate.shared.generated.resources.subjects_level_unknown_hint
import maturimate.shared.generated.resources.subjects_title_learner
import maturimate.shared.generated.resources.subjects_title_student
import maturimate.shared.generated.resources.subjects_title_teacher
import maturimate.shared.generated.resources.subjects_why_other
import maturimate.shared.generated.resources.subjects_why_student
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

/** Step 3 of the onboarding: the subjects, and the English level when English is chosen. */
@Composable
fun SubjectsScreen(
    onBack: () -> Unit,
    onNext: (UserRole?) -> Unit,
    viewModel: SubjectsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    SubjectsContent(
        state = state,
        onToggled = viewModel::onToggled,
        onLevelSelected = viewModel::onLevelSelected,
        onBack = onBack,
        onNext = { viewModel.onNext(onNext) },
    )
}

/** The subject's name in the app language. */
val CatalogSubject.label: StringResource
    get() = when (this) {
        CatalogSubject.HUNGARIAN -> Res.string.subject_hu_mjl
        CatalogSubject.SLOVAK_HU -> Res.string.subject_hu_sjsl
        CatalogSubject.SLOVAK -> Res.string.subject_sk_sjl
        CatalogSubject.ENGLISH_B1 -> Res.string.subject_anj_b1
        CatalogSubject.ENGLISH_B2 -> Res.string.subject_anj_b2
        CatalogSubject.MATHEMATICS -> Res.string.subject_mat
    }

@Composable
fun SubjectsContent(
    state: SubjectsUiState,
    onToggled: (CatalogSubject) -> Unit,
    onLevelSelected: (LevelChoice) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val title = when (state.role) {
        UserRole.TEACHER -> Res.string.subjects_title_teacher
        UserRole.LEARNER -> Res.string.subjects_title_learner
        else -> Res.string.subjects_title_student
    }
    val why = if (state.role == UserRole.STUDENT) Res.string.subjects_why_student else Res.string.subjects_why_other
    OnboardingScaffold(
        actions = listOf(OnboardingAction(stringResource(nextLabel(LocalEditingAnswer.current)), onNext, enabled = state.canContinue)),
        progress = OnboardingProgress(OnboardingStep.SUBJECTS, onboardingSteps(state.role), onBack),
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(stringResource(why), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space3)) {
            state.items.forEach { item ->
                SubjectCard(
                    item = item,
                    onToggled = { onToggled(item.subject) },
                    level = state.level,
                    onLevelSelected = onLevelSelected,
                )
            }
        }
        if (!LocalEditingAnswer.current) Text(
            stringResource(Res.string.onboarding_change_later),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A subject with a check mark, its tags, and the level choice under English. */
@Composable
private fun SubjectCard(
    item: SubjectItem,
    onToggled: () -> Unit,
    level: LevelChoice?,
    onLevelSelected: (LevelChoice) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val layout = MaterialTheme.layout
    val spacing = MaterialTheme.spacing
    // the background stays light when chosen, so the level chips inside stay readable;
    // the check mark and the border show the choice
    Surface(
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerLowest,
        contentColor = colors.onBackground,
        border = if (item.selected) BorderStroke(layout.borderThick, colors.primary) else BorderStroke(layout.borderThin, colors.outline),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = layout.choiceCardMinHeight)
                    .toggleable(value = item.selected, enabled = !item.locked, role = Role.Checkbox, onValueChange = { onToggled() })
                    .padding(horizontal = spacing.space4, vertical = spacing.space3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.space3),
            ) {
                CheckMark(item.selected)
                // the name gets the whole width; "Kötelező" goes under it, so long names do not break
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.space1)) {
                    Text(stringResource(if (item.asksLevel) Res.string.subject_anj else item.subject.label), style = MaterialTheme.typography.titleMedium)
                    if (item.locked) Compulsory()
                }
            }
            if (item.asksLevel && item.selected) {
                Column(
                    modifier = Modifier.padding(start = spacing.space4, end = spacing.space4, bottom = spacing.space4),
                    verticalArrangement = Arrangement.spacedBy(spacing.space2),
                ) {
                    Text(stringResource(Res.string.subjects_english_level), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.space2)) {
                        AppChoiceChip("B1", level == LevelChoice.B1, { onLevelSelected(LevelChoice.B1) })
                        AppChoiceChip("B2", level == LevelChoice.B2, { onLevelSelected(LevelChoice.B2) })
                        AppChoiceChip(stringResource(Res.string.level_unknown), level == LevelChoice.UNKNOWN, { onLevelSelected(LevelChoice.UNKNOWN) })
                    }
                    if (level == LevelChoice.UNKNOWN) {
                        Text(stringResource(Res.string.subjects_level_unknown_hint), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

/** Square check mark: filled with a check when chosen; the state is shown by shape too. */
@Composable
private fun CheckMark(checked: Boolean) {
    val colors = MaterialTheme.colorScheme
    val layout = MaterialTheme.layout
    Surface(
        modifier = Modifier.size(layout.icon),
        shape = MaterialTheme.shapes.extraSmall,
        color = if (checked) colors.primary else Color.Transparent,
        contentColor = colors.onPrimary,
        border = if (checked) null else BorderStroke(layout.borderThick, colors.outline),
    ) {
        if (checked) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(layout.iconSmall))
            }
        }
    }
}

/** "Kötelező" with a lock, under the subject name. */
@Composable
private fun Compulsory() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space1),
    ) {
        Icon(
            Icons.Rounded.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(MaterialTheme.layout.iconSmall),
        )
        Text(stringResource(Res.string.subjects_compulsory), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
@Preview
private fun SubjectsContentPreview() {
    AppTheme(darkTheme = false) {
        SubjectsContent(
            state = SubjectsUiState(
                role = UserRole.STUDENT,
                items = (CatalogSubject.forLanguage(TeachingLanguage.HU) - CatalogSubject.ENGLISH_B2).map {
                    SubjectItem(it, it.compulsory, it.compulsory, it == CatalogSubject.HUNGARIAN, asksLevel = it.level != null)
                },
                level = LevelChoice.UNKNOWN,
            ),
            onToggled = {}, onLevelSelected = {}, onBack = {}, onNext = {},
        )
    }
}
