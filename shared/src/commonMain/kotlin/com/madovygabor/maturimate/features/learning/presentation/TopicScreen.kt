package com.madovygabor.maturimate.features.learning.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.uiComponents.AppPathStep
import com.madovygabor.maturimate.core.presentation.uiComponents.AppProgressRing
import com.madovygabor.maturimate.core.presentation.uiComponents.PathStepState
import com.madovygabor.maturimate.core.presentation.uiComponents.PathStepStyle
import com.madovygabor.maturimate.features.appOnboarding.presentation.subjects.label
import com.madovygabor.maturimate.features.learning.domain.model.LearningUnit
import com.madovygabor.maturimate.features.learning.domain.model.PathStep
import com.madovygabor.maturimate.features.learning.domain.model.StepKind
import com.madovygabor.maturimate.features.learning.domain.model.StepState
import com.madovygabor.maturimate.features.learning.domain.model.TopicLevel
import com.madovygabor.maturimate.features.learning.domain.model.TopicSummary
import com.madovygabor.maturimate.features.userProfile.domain.CatalogSubject
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.common_back
import maturimate.shared.generated.resources.learning_parts_title
import maturimate.shared.generated.resources.learning_step_done
import maturimate.shared.generated.resources.learning_step_lesson_meta
import maturimate.shared.generated.resources.learning_step_next
import maturimate.shared.generated.resources.learning_step_tasks_meta
import maturimate.shared.generated.resources.learning_steps_done
import maturimate.shared.generated.resources.learning_topics_hint
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * The parts of a subject, the topics below a topic, or the path of a topic. [subject] is the subject
 * the user opened; its name is the title of the top level and the label above a part.
 */
@Composable
fun TopicScreen(
    target: TopicTarget,
    subject: CatalogSubject?,
    onBack: () -> Unit,
    onTopicClick: (TopicSummary) -> Unit,
    viewModel: TopicViewModel = koinViewModel(key = target.toString()) { parametersOf(target) },
) {
    // read again whenever the screen comes back, so progress made deeper in shows here
    LaunchedEffect(Unit) { viewModel.refresh() }
    val state by viewModel.state.collectAsState()
    TopicContent(
        state = state,
        subjectName = subject?.let { stringResource(it.label) },
        onBack = onBack,
        onTopicClick = onTopicClick,
        onStepClick = { viewModel.onStepClicked() },
        onRetry = viewModel::refresh,
    )
}

@Composable
fun TopicContent(
    state: TopicUiState,
    subjectName: String?,
    onBack: () -> Unit,
    onTopicClick: (TopicSummary) -> Unit,
    onStepClick: (PathStep) -> Unit,
    onRetry: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val layout = MaterialTheme.layout
    when (state) {
        TopicUiState.Loading -> Unit
        TopicUiState.Failed -> Column(Modifier.padding(spacing.space4)) {
            BackRow(label = subjectName, onBack = onBack)
            LoadError(onRetry)
        }
        is TopicUiState.Ready -> when (val level = state.level) {
            is TopicLevel.Branch -> BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
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
                            BackRow(label = level.parentTitle ?: subjectName, onBack = onBack)
                            Text(level.title ?: stringResource(Res.string.learning_parts_title), style = MaterialTheme.typography.headlineMedium)
                            Text(
                                stringResource(Res.string.learning_topics_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(level.topics, key = { it.topic.id }) { summary ->
                        TopicRow(summary, onClick = { onTopicClick(summary) })
                    }
                    level.finalTest?.let { step ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Step(step, isFirst = true, isLast = true, onClick = { onStepClick(step) })
                        }
                    }
                }
            }
            is TopicLevel.Path -> BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LazyColumn(
                    modifier = Modifier.widthIn(max = layout.contentMaxWidth),
                    contentPadding = PaddingValues(spacing.space4),
                ) {
                    item {
                        Column(
                            modifier = Modifier.padding(bottom = spacing.space4),
                            verticalArrangement = Arrangement.spacedBy(spacing.space2),
                        ) {
                            BackRow(label = level.parentTitle ?: subjectName, onBack = onBack)
                            Text(level.title, style = MaterialTheme.typography.headlineMedium)
                            Text(
                                stringResource(Res.string.learning_steps_done, level.steps.count { it.state == StepState.DONE }, level.steps.size),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(level.steps, key = { it.unit.id }) { step ->
                        val index = level.steps.indexOf(step)
                        Step(step, isFirst = index == 0, isLast = index == level.steps.lastIndex, onClick = { onStepClick(step) })
                    }
                }
            }
        }
    }
}

@Composable
private fun Step(step: PathStep, isFirst: Boolean, isLast: Boolean, onClick: () -> Unit) {
    val unit = step.unit
    val meta = when (unit) {
        is LearningUnit.Summary -> stringResource(Res.string.learning_step_lesson_meta, unit.estimatedMinutes)
        is LearningUnit.Practice -> stringResource(Res.string.learning_step_tasks_meta, unit.roundSize)
    }
    AppPathStep(
        style = when (step.kind) {
            StepKind.LESSON -> PathStepStyle.LESSON
            StepKind.MINI_TEST -> PathStepStyle.MINI
            StepKind.TOPIC_TEST, StepKind.FINAL_TEST -> PathStepStyle.TEST
        },
        state = when (step.state) {
            StepState.DONE -> PathStepState.DONE
            StepState.CURRENT -> PathStepState.CURRENT
            StepState.TODO -> PathStepState.TODO
        },
        title = unit.title,
        meta = meta,
        stateLabel = when (step.state) {
            StepState.DONE -> stringResource(Res.string.learning_step_done)
            StepState.CURRENT -> stringResource(Res.string.learning_step_next)
            StepState.TODO -> null
        },
        isFirst = isFirst,
        isLast = isLast,
        onClick = onClick,
    )
}

/** A topic of a list: the progress ring, its title and how many steps are done. */
@Composable
private fun TopicRow(summary: TopicSummary, onClick: () -> Unit) {
    val spacing = MaterialTheme.spacing
    val description = stringResource(Res.string.learning_steps_done, summary.steps.done, summary.steps.total)
    LearningCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.space3)) {
            AppProgressRing(summary.steps.done, summary.steps.total, description)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.space1)) {
                Text(summary.topic.title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun BackRow(label: String?, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(Res.string.common_back))
        }
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
