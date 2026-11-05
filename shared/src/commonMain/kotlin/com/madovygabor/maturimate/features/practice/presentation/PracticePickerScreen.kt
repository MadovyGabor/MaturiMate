package com.madovygabor.maturimate.features.practice.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.KeyboardType
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.uiComponents.AppButton
import com.madovygabor.maturimate.core.presentation.uiComponents.AppCheckRow
import com.madovygabor.maturimate.core.presentation.uiComponents.AppChoiceChip
import com.madovygabor.maturimate.core.presentation.uiComponents.AppTextField
import com.madovygabor.maturimate.core.presentation.uiComponents.CheckRowStyle
import com.madovygabor.maturimate.features.appOnboarding.presentation.subjects.label
import com.madovygabor.maturimate.features.learning.presentation.LoadError
import com.madovygabor.maturimate.features.practice.domain.PracticeNode
import com.madovygabor.maturimate.features.practice.domain.PracticeSubject
import com.madovygabor.maturimate.features.userProfile.domain.CatalogSubject
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.common_back
import maturimate.shared.generated.resources.picker_collapse
import maturimate.shared.generated.resources.picker_custom
import maturimate.shared.generated.resources.picker_custom_invalid
import maturimate.shared.generated.resources.picker_custom_label
import maturimate.shared.generated.resources.picker_expand
import maturimate.shared.generated.resources.picker_lead
import maturimate.shared.generated.resources.picker_length_label
import maturimate.shared.generated.resources.picker_no_tasks
import maturimate.shared.generated.resources.picker_not_finished
import maturimate.shared.generated.resources.picker_pick_hint
import maturimate.shared.generated.resources.picker_select
import maturimate.shared.generated.resources.picker_selected
import maturimate.shared.generated.resources.picker_start
import maturimate.shared.generated.resources.picker_summary
import maturimate.shared.generated.resources.picker_tasks
import maturimate.shared.generated.resources.picker_title
import maturimate.shared.generated.resources.picker_topics
import maturimate.shared.generated.resources.picker_topics_done
import maturimate.shared.generated.resources.practice_free_start
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** What the picker screen can ask of its view-model. */
class PickerActions(
    val onBack: () -> Unit = {},
    val onToggle: (List<Long>) -> Unit = {},
    val onFoldSubject: (Long) -> Unit = {},
    val onFoldGroup: (Long) -> Unit = {},
    val onLength: (RoundLength) -> Unit = {},
    val onCustom: (String) -> Unit = {},
    val onStart: () -> Unit = {},
    val onRetry: () -> Unit = {},
)

/**
 * The practice picker (docs/practice/prototype.html): the finished topics of every subject to tick, the length of
 * the round, and the start. [onStart] gets the chosen topics and the number of questions.
 */
@Composable
fun PracticePickerScreen(onBack: () -> Unit, onStart: (PracticeStart) -> Unit, viewModel: PracticePickerViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsState()
    PickerContent(
        state,
        PickerActions(
            onBack = onBack,
            onToggle = viewModel::toggle,
            onFoldSubject = viewModel::foldSubject,
            onFoldGroup = viewModel::foldGroup,
            onLength = viewModel::selectLength,
            onCustom = viewModel::typeCustom,
            onStart = { (state as? PickerUiState.Ready)?.start()?.let(onStart) },
            onRetry = viewModel::refresh,
        ),
    )
}

@Composable
fun PickerContent(state: PickerUiState, actions: PickerActions) {
    val spacing = MaterialTheme.spacing
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = MaterialTheme.layout.contentMaxWidth).fillMaxSize()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(spacing.space4),
                verticalArrangement = Arrangement.spacedBy(spacing.space2),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(Res.string.common_back))
                    }
                    Text(stringResource(Res.string.picker_title), modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                }
                when (state) {
                    PickerUiState.Loading -> Unit
                    PickerUiState.Failed -> LoadError(actions.onRetry)
                    is PickerUiState.Ready -> {
                        Text(stringResource(Res.string.picker_lead), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        state.subjects.forEach { SubjectCard(it, state, actions) }
                    }
                }
            }
            if (state is PickerUiState.Ready) Footer(state, actions)
        }
    }
}

/** One subject: its header (tick, name, "3 / 5 téma kész", fold) and, when open, its parts and topics. */
@Composable
private fun SubjectCard(subject: PracticeSubject, state: PickerUiState.Ready, actions: PickerActions) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    val name = CatalogSubject.byId(subject.subject.id)?.let { stringResource(it.label) } ?: subject.subject.nameHu
    val leafIds = subject.parts.flatMap { it.leafIds }
    val leaves = allNodes(subject.parts).filter { it.children.isEmpty() }
    val open = subject.subject.id in state.openSubjects
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerLow,
        border = BorderStroke(MaterialTheme.layout.borderThin, colors.outlineVariant),
    ) {
        Column(Modifier.padding(vertical = spacing.space1)) {
            AppCheckRow(
                title = name,
                state = state.stateOf(leafIds),
                onToggle = { actions.onToggle(leafIds) },
                checkLabel = stringResource(Res.string.picker_select, name),
                style = CheckRowStyle.SUBJECT,
                subtitle = stringResource(Res.string.picker_topics_done, leaves.count { it.finished }, leaves.size),
                tag = selectedTag(state.chosen(leafIds)),
                enabled = leafIds.isNotEmpty(),
                expanded = open,
                onExpand = { actions.onFoldSubject(subject.subject.id) },
                expandLabel = stringResource(Res.string.picker_expand),
                collapseLabel = stringResource(Res.string.picker_collapse),
            )
            if (open) {
                HorizontalDivider(color = colors.outlineVariant)
                Column(Modifier.padding(horizontal = spacing.space2, vertical = spacing.space2), verticalArrangement = Arrangement.spacedBy(spacing.space1)) {
                    subject.parts.forEach { Node(it, state, actions) }
                }
            }
        }
    }
}

/** A part or a topic with topics below it (a group that folds), or a topic to tick. */
@Composable
private fun Node(node: PracticeNode, state: PickerUiState.Ready, actions: PickerActions) {
    val title = node.topic.title
    if (node.children.isEmpty()) {
        AppCheckRow(
            title = title,
            state = if (node.topic.id in state.selected) ToggleableState.On else ToggleableState.Off,
            onToggle = { actions.onToggle(node.leafIds) },
            checkLabel = stringResource(Res.string.picker_select, title),
            subtitle = when {
                !node.finished -> stringResource(Res.string.picker_not_finished)
                node.taskCount == 0 -> stringResource(Res.string.picker_no_tasks)
                else -> pluralStringResource(Res.plurals.picker_tasks, node.taskCount, node.taskCount)
            },
            enabled = node.leafIds.isNotEmpty(),
        )
        return
    }
    val open = node.topic.id !in state.closedGroups
    AppCheckRow(
        title = title,
        state = state.stateOf(node.leafIds),
        onToggle = { actions.onToggle(node.leafIds) },
        checkLabel = stringResource(Res.string.picker_select, title),
        style = CheckRowStyle.GROUP,
        tag = selectedTag(state.chosen(node.leafIds)),
        enabled = node.leafIds.isNotEmpty(),
        expanded = open,
        onExpand = { actions.onFoldGroup(node.topic.id) },
        expandLabel = stringResource(Res.string.picker_expand),
        collapseLabel = stringResource(Res.string.picker_collapse),
    )
    if (open) {
        Column(Modifier.padding(start = MaterialTheme.spacing.space3), verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space1)) {
            node.children.forEach { Node(it, state, actions) }
        }
    }
}

@Composable
private fun selectedTag(count: Int): String? = if (count > 0) stringResource(Res.string.picker_selected, count) else null

/** The bottom of the picker: what is chosen, the length of the round, and the start. */
@Composable
private fun Footer(state: PickerUiState.Ready, actions: PickerActions) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = colors.outlineVariant)
        Column(Modifier.padding(spacing.space4), verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
            Text(
                if (state.selected.isEmpty()) stringResource(Res.string.picker_pick_hint)
                else stringResource(
                    Res.string.picker_summary,
                    pluralStringResource(Res.plurals.picker_topics, state.selected.size, state.selected.size),
                    pluralStringResource(Res.plurals.picker_tasks, state.poolSize, state.poolSize),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Text(stringResource(Res.string.picker_length_label), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.space2)) {
                RoundLength.entries.forEach { length ->
                    AppChoiceChip(
                        label = length.count?.toString() ?: stringResource(Res.string.picker_custom),
                        selected = state.length == length,
                        onClick = { actions.onLength(length) },
                    )
                }
            }
            if (state.length == RoundLength.CUSTOM) {
                AppTextField(
                    value = state.customText,
                    onValueChange = actions.onCustom,
                    label = stringResource(Res.string.picker_custom_label),
                    modifier = Modifier.fillMaxWidth(),
                    error = if (state.customInvalid) stringResource(Res.string.picker_custom_invalid) else null,
                    keyboardType = KeyboardType.Number,
                )
            }
            // the number on the button: the round as it will be, or the asked length while nothing is chosen
            val shown = if (state.selected.isNotEmpty() && state.poolSize > 0) state.questions else state.wanted ?: 0
            AppButton(
                if (shown > 0) pluralStringResource(Res.plurals.picker_start, shown, shown) else stringResource(Res.string.practice_free_start),
                actions.onStart,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canStart,
            )
        }
    }
}
