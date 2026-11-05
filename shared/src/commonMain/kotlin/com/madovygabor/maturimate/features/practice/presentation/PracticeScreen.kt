package com.madovygabor.maturimate.features.practice.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.uiComponents.AppButton
import com.madovygabor.maturimate.core.presentation.uiComponents.AppTag
import com.madovygabor.maturimate.core.presentation.uiComponents.TagStyle
import com.madovygabor.maturimate.features.learning.presentation.LoadError
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.nav_practice
import maturimate.shared.generated.resources.practice_free_none
import maturimate.shared.generated.resources.practice_free_start
import maturimate.shared.generated.resources.practice_free_text
import maturimate.shared.generated.resources.practice_free_title
import maturimate.shared.generated.resources.practice_soon_text
import maturimate.shared.generated.resources.practice_tab_subtitle
import maturimate.shared.generated.resources.subjects_coming_soon
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.madovygabor.maturimate.core.presentation.uiComponents.AppButtonVariant
import maturimate.shared.generated.resources.practice_mistakes_title
import maturimate.shared.generated.resources.mistakes_card_text
import maturimate.shared.generated.resources.mistakes_card_none
import maturimate.shared.generated.resources.mistakes_all
import maturimate.shared.generated.resources.mistakes_by_topic
import org.jetbrains.compose.resources.pluralStringResource
import androidx.compose.foundation.layout.Row
import com.madovygabor.maturimate.core.presentation.theme.extendedColors
import com.madovygabor.maturimate.core.presentation.uiComponents.AppProgressBar
import com.madovygabor.maturimate.features.appOnboarding.presentation.subjects.label
import com.madovygabor.maturimate.features.practice.domain.ScoreSummary
import com.madovygabor.maturimate.features.practice.domain.TopicScore
import com.madovygabor.maturimate.features.practice.domain.TopicScores
import com.madovygabor.maturimate.features.userProfile.domain.CatalogSubject
import maturimate.shared.generated.resources.scores_title
import maturimate.shared.generated.resources.scores_empty
import maturimate.shared.generated.resources.scores_weak
import maturimate.shared.generated.resources.scores_strong
import maturimate.shared.generated.resources.scores_right_now
import maturimate.shared.generated.resources.scores_practice

/**
 * The practice tab (docs/practice/prototype.html): the free practice card, which opens the picker, and a card
 * naming what comes next (the mistake practice, weak topics, old material; Milestone 9a.3 to 9a.5).
 */
@Composable
fun PracticeScreen(
    onOpenPicker: () -> Unit,
    onOpenMistakePicker: () -> Unit = {},
    onStartAllMistakes: (count: Int) -> Unit = {},
    onPracticeTopic: (topicId: Long, count: Int) -> Unit = { _, _ -> },
    viewModel: PracticeViewModel = koinViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    val state by viewModel.state.collectAsState()
    PracticeContent(
        state,
        onOpenPicker,
        onRetry = viewModel::refresh,
        onOpenMistakePicker = onOpenMistakePicker,
        onStartAllMistakes = { onStartAllMistakes(PickerUiState.ALL_MISTAKES_ROUND) },
        onPracticeTopic = { onPracticeTopic(it, PickerUiState.TOPIC_ROUND) },
    )
}

@Composable
fun PracticeContent(
    state: PracticeUiState,
    onOpenPicker: () -> Unit,
    onRetry: () -> Unit,
    onOpenMistakePicker: () -> Unit = {},
    onStartAllMistakes: () -> Unit = {},
    onPracticeTopic: (topicId: Long) -> Unit = {},
) {
    val spacing = MaterialTheme.spacing
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = MaterialTheme.layout.contentMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.space4),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.space1)) {
                Text(stringResource(Res.string.nav_practice), modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineMedium)
                Text(stringResource(Res.string.practice_tab_subtitle), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when (state) {
                PracticeUiState.Loading -> Unit
                PracticeUiState.Failed -> LoadError(onRetry)
                is PracticeUiState.Ready -> {
                    FreePracticeCard(state.canPractice, onOpenPicker)
                    MistakeCard(state.mistakes, onStartAllMistakes, onOpenMistakePicker)
                    ScoresCard(state.scores, onPracticeTopic)
                    ComingSoonCard()
                }
            }
        }
    }
}

/** The gold card of the free practice; its button is off until a topic is finished. */
@Composable
private fun FreePracticeCard(canPractice: Boolean, onOpenPicker: () -> Unit) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
        Column(Modifier.padding(spacing.space4), verticalArrangement = Arrangement.spacedBy(spacing.space3)) {
            Text(stringResource(Res.string.practice_free_title), modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(if (canPractice) Res.string.practice_free_text else Res.string.practice_free_none),
                style = MaterialTheme.typography.bodyMedium,
            )
            AppButton(stringResource(Res.string.practice_free_start), onOpenPicker, modifier = Modifier.fillMaxWidth(), enabled = canPractice)
        }
    }
}

/**
 * The mistake practice: how many tasks were answered wrong last, a round of them ("Összes hiba", at most
 * [PickerUiState.ALL_MISTAKES_ROUND]) or the picker of the topics with mistakes. With no mistake both are off.
 */
@Composable
private fun MistakeCard(mistakes: Int, onAll: () -> Unit, onPick: () -> Unit) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerLow,
        border = BorderStroke(MaterialTheme.layout.borderThin, colors.outlineVariant),
    ) {
        Column(Modifier.padding(spacing.space4), verticalArrangement = Arrangement.spacedBy(spacing.space3)) {
            Text(stringResource(Res.string.practice_mistakes_title), modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            Text(
                if (mistakes > 0) pluralStringResource(Res.plurals.mistakes_card_text, mistakes, mistakes)
                else stringResource(Res.string.mistakes_card_none),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            AppButton(
                stringResource(Res.string.mistakes_all, mistakes),
                onAll,
                modifier = Modifier.fillMaxWidth(),
                // the free practice has the one main button of the tab; this is the secondary one
                variant = AppButtonVariant.TONAL,
                enabled = mistakes > 0,
            )
            AppButton(
                stringResource(Res.string.mistakes_by_topic),
                onPick,
                modifier = Modifier.fillMaxWidth(),
                variant = AppButtonVariant.OUTLINED,
                enabled = mistakes > 0,
            )
        }
    }
}

/**
 * The weak and the strong topics (9a.4): each with its subject, a bar with the percent (the number is always written,
 * the colour only supports it) and how many of the tasks seen are right now; a weak topic has "Gyakorlás", which asks
 * all its mistakes and fills the round with its other tasks.
 */
@Composable
private fun ScoresCard(scores: ScoreSummary, onPracticeTopic: (Long) -> Unit) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerLow,
        border = BorderStroke(MaterialTheme.layout.borderThin, colors.outlineVariant),
    ) {
        Column(Modifier.padding(spacing.space4), verticalArrangement = Arrangement.spacedBy(spacing.space3)) {
            Text(stringResource(Res.string.scores_title), modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            if (scores.isEmpty) {
                Text(
                    stringResource(Res.string.scores_empty, TopicScores.MIN_SEEN),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                return@Column
            }
            if (scores.weak.isNotEmpty()) {
                GroupTitle(stringResource(Res.string.scores_weak))
                scores.weak.forEach { ScoreRow(it, weak = true, onPractice = { onPracticeTopic(it.topic.id) }) }
            }
            if (scores.strong.isNotEmpty()) {
                GroupTitle(stringResource(Res.string.scores_strong))
                scores.strong.forEach { ScoreRow(it, weak = false, onPractice = {}) }
            }
        }
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ScoreRow(score: TopicScore, weak: Boolean, onPractice: () -> Unit) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    val subject = CatalogSubject.byId(score.subject.id)?.let { stringResource(it.label) } ?: score.subject.nameHu
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = colors.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = spacing.space4, vertical = spacing.space3), verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(score.topic.title, style = MaterialTheme.typography.titleSmall)
                    Text(subject, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                Text("${score.percent}%", style = MaterialTheme.typography.titleMedium)
            }
            AppProgressBar(score.percent / 100f, color = if (weak) colors.error else MaterialTheme.extendedColors.success)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.scores_right_now, score.right, score.seen),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                if (weak) AppButton(stringResource(Res.string.scores_practice), onPractice, variant = AppButtonVariant.TONAL, compact = true)
            }
        }
    }
}

/** What the tab will hold next; 9a.5 replaces it with its own card. */
@Composable
private fun ComingSoonCard() {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerLow,
        border = BorderStroke(MaterialTheme.layout.borderThin, colors.outlineVariant),
    ) {
        Column(Modifier.padding(spacing.space4), verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
            AppTag(stringResource(Res.string.subjects_coming_soon), style = TagStyle.HIGHLIGHT)
            Text(stringResource(Res.string.practice_soon_text), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}
