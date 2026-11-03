package com.madovygabor.maturimate.features.learning.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.madovygabor.maturimate.core.presentation.theme.extendedColors
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.uiComponents.AppButton
import com.madovygabor.maturimate.core.presentation.uiComponents.AppButtonVariant
import com.madovygabor.maturimate.core.presentation.uiComponents.AppProgressBar
import com.madovygabor.maturimate.features.appOnboarding.presentation.subjects.label
import com.madovygabor.maturimate.features.userProfile.domain.CatalogSubject
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.learning_load_error
import maturimate.shared.generated.resources.learning_retry
import maturimate.shared.generated.resources.learning_steps_done
import maturimate.shared.generated.resources.learning_subject_parts
import maturimate.shared.generated.resources.learning_subjects_empty
import maturimate.shared.generated.resources.learning_subjects_intro
import maturimate.shared.generated.resources.learning_subjects_title
import maturimate.shared.generated.resources.subjects_coming_soon
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** The subjects tab: the user's subjects with their progress. */
@Composable
fun SubjectListScreen(
    onSubjectClick: (CatalogSubject) -> Unit,
    viewModel: SubjectListViewModel = koinViewModel(),
) {
    // read again whenever the screen comes back, so progress made deeper in shows here
    LaunchedEffect(Unit) { viewModel.refresh() }
    val state by viewModel.state.collectAsState()
    SubjectListContent(state, onSubjectClick, onRetry = viewModel::refresh)
}

@Composable
fun SubjectListContent(
    state: SubjectListUiState,
    onSubjectClick: (CatalogSubject) -> Unit,
    onRetry: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val layout = MaterialTheme.layout
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val columns = if (maxWidth >= layout.breakpointMedium) 2 else 1
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.widthIn(max = layout.contentMaxWidth * 2),
            contentPadding = PaddingValues(spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.space3),
            horizontalArrangement = Arrangement.spacedBy(spacing.space3),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
                    Text(stringResource(Res.string.learning_subjects_title), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(Res.string.learning_subjects_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when (state) {
                SubjectListUiState.Loading -> Unit
                SubjectListUiState.Failed -> item(span = { GridItemSpan(maxLineSpan) }) { LoadError(onRetry) }
                is SubjectListUiState.Ready -> {
                    if (state.cards.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(stringResource(Res.string.learning_subjects_empty), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    items(state.cards, key = { it.subject.id }) { card ->
                        SubjectCardItem(card, onClick = { onSubjectClick(card.subject) })
                    }
                }
            }
        }
    }
}

/** One subject; without installed content it is greyed with a "Hamarosan" tag and cannot be opened. */
@Composable
private fun SubjectCardItem(card: SubjectCard, onClick: () -> Unit) {
    val spacing = MaterialTheme.spacing
    val progress = card.progress
    LearningCard(onClick = if (progress != null) onClick else null, enabled = progress != null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.space3)) {
            Text(
                stringResource(card.subject.label),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
            )
            if (progress != null) {
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
            } else {
                Tag(stringResource(Res.string.subjects_coming_soon))
            }
        }
        if (progress != null) {
            Text(
                stringResource(Res.string.learning_subject_parts, progress.parts),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AppProgressBar(progress.steps.fraction, color = MaterialTheme.extendedColors.brandGold)
            // a gold bar always shows its value as text (design system)
            Text(
                stringResource(Res.string.learning_steps_done, progress.steps.done, progress.steps.total),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun LoadError(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.space6),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space3),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(Res.string.learning_load_error), style = MaterialTheme.typography.bodyLarge)
        AppButton(stringResource(Res.string.learning_retry), onRetry, variant = AppButtonVariant.OUTLINED)
    }
}

@Composable
internal fun Tag(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.space2, vertical = MaterialTheme.spacing.space1 / 2),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** A card of the lists: `surface-container-low`, `radius-lg`, clickable when [onClick] is given. */
@Composable
internal fun LearningCard(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    val shape = MaterialTheme.shapes.large
    val border = BorderStroke(MaterialTheme.layout.borderThin, colors.outlineVariant)
    val body = @Composable {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = MaterialTheme.layout.choiceCardMinHeight).padding(spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.space2),
        ) { content() }
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            enabled = enabled,
            shape = shape,
            color = colors.surfaceContainerLow,
            contentColor = colors.onBackground,
            border = border,
        ) { body() }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = colors.surfaceContainerLow,
            contentColor = colors.onSurfaceVariant,
            border = border,
        ) { body() }
    }
}
