package com.madovygabor.maturimate.features.learning.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.TextUnit
import com.madovygabor.maturimate.core.presentation.theme.extendedColors
import com.madovygabor.maturimate.core.presentation.theme.extendedTypography
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.uiComponents.AnswerState
import com.madovygabor.maturimate.core.presentation.uiComponents.AppAnswerOption
import com.madovygabor.maturimate.core.presentation.uiComponents.AppButton
import com.madovygabor.maturimate.core.presentation.uiComponents.AppButtonVariant
import com.madovygabor.maturimate.core.presentation.uiComponents.AppChoiceChip
import com.madovygabor.maturimate.core.presentation.uiComponents.AppFeedbackSheet
import com.madovygabor.maturimate.core.presentation.uiComponents.AppGapField
import com.madovygabor.maturimate.core.presentation.uiComponents.AppGapSlot
import com.madovygabor.maturimate.core.presentation.uiComponents.AppMatchPair
import com.madovygabor.maturimate.core.presentation.uiComponents.AppProgressBar
import com.madovygabor.maturimate.core.presentation.uiComponents.AppTextField
import com.madovygabor.maturimate.core.presentation.uiComponents.AppTrueFalseCard
import com.madovygabor.maturimate.core.presentation.uiComponents.FeedbackLine
import com.madovygabor.maturimate.core.presentation.utils.AppRichText
import com.madovygabor.maturimate.core.presentation.utils.TextPart
import com.madovygabor.maturimate.core.presentation.utils.richText
import com.madovygabor.maturimate.core.presentation.utils.splitGaps
import com.madovygabor.maturimate.features.learning.domain.model.OptionRole
import com.madovygabor.maturimate.features.learning.domain.model.Task
import com.madovygabor.maturimate.features.learning.domain.model.TaskFormat
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.common_next
import maturimate.shared.generated.resources.learning_load_error
import maturimate.shared.generated.resources.learning_retry
import maturimate.shared.generated.resources.lesson_close
import maturimate.shared.generated.resources.lesson_page_position
import maturimate.shared.generated.resources.test_check
import maturimate.shared.generated.resources.test_correct
import maturimate.shared.generated.resources.test_empty
import maturimate.shared.generated.resources.test_finish
import maturimate.shared.generated.resources.test_gap_choice_hint
import maturimate.shared.generated.resources.test_gap_description
import maturimate.shared.generated.resources.test_gap_fill_hint
import maturimate.shared.generated.resources.test_hint
import maturimate.shared.generated.resources.test_hint_request
import maturimate.shared.generated.resources.test_match_hint
import maturimate.shared.generated.resources.test_order_hint
import maturimate.shared.generated.resources.test_pick_placeholder
import maturimate.shared.generated.resources.test_right_answer
import maturimate.shared.generated.resources.test_wrong
import maturimate.shared.generated.resources.test_why_not
import maturimate.shared.generated.resources.test_your_answer
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** What the screen of a test can ask of its ViewModel. */
class TestActions(
    val onClose: () -> Unit = {},
    val onSelect: (String) -> Unit = {},
    val onType: (String) -> Unit = {},
    val onHint: () -> Unit = {},
    val onCheck: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onPick: (String) -> Unit = {},
    val onClear: (Int) -> Unit = {},
    val onActivate: (Int) -> Unit = {},
    val onTypeIn: (Int, String) -> Unit = { _, _ -> },
)

/** A test: its tasks one after the other, with feedback after each (docs/learning/prototype.html). */
@Composable
fun TestScreen(
    unitId: Long,
    onClose: () -> Unit,
    viewModel: TestSessionViewModel = koinViewModel(key = "test$unitId") { parametersOf(unitId) },
) {
    val state by viewModel.state.collectAsState()
    val close by rememberUpdatedState(onClose)
    // after the last task the screen closes
    LaunchedEffect(viewModel) { viewModel.finished.collect { close() } }
    TestContent(
        state = state,
        actions = TestActions(
            onClose = onClose,
            onSelect = viewModel::select,
            onType = viewModel::type,
            onHint = viewModel::showHint,
            onCheck = viewModel::check,
            onNext = viewModel::next,
            onRetry = viewModel::load,
            onPick = viewModel::pick,
            onClear = viewModel::clear,
            onActivate = viewModel::activate,
            onTypeIn = viewModel::typeIn,
        ),
    )
}

@Composable
fun TestContent(state: TestUiState, actions: TestActions) {
    val spacing = MaterialTheme.spacing
    val layout = MaterialTheme.layout
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
        when (state) {
            TestUiState.Loading -> Unit
            TestUiState.Failed, TestUiState.Empty -> Column(Modifier.fillMaxSize().padding(spacing.space4)) {
                IconButton(onClick = actions.onClose) { Icon(Icons.Rounded.Close, contentDescription = stringResource(Res.string.lesson_close)) }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = spacing.space6),
                    verticalArrangement = Arrangement.spacedBy(spacing.space3),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (state == TestUiState.Empty) {
                        Text(stringResource(Res.string.test_empty), style = MaterialTheme.typography.bodyLarge)
                    } else {
                        Text(stringResource(Res.string.learning_load_error), style = MaterialTheme.typography.bodyLarge)
                        AppButton(stringResource(Res.string.learning_retry), actions.onRetry)
                    }
                }
            }
            is TestUiState.Ready -> BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = layout.contentMaxWidth).fillMaxHeight()) {
                    Column(
                        modifier = Modifier.weight(1f).padding(horizontal = spacing.space4).padding(top = spacing.space4),
                        verticalArrangement = Arrangement.spacedBy(spacing.space4),
                    ) {
                        TopBar(state, actions.onClose)
                        Text(state.title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TaskBody(state, actions)
                    }
                    if (state.checked) {
                        Feedback(state, actions.onNext)
                    } else {
                        AppButton(
                            text = stringResource(Res.string.test_check),
                            onClick = actions.onCheck,
                            enabled = state.canCheck,
                            modifier = Modifier.fillMaxWidth().padding(spacing.space4),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(state: TestUiState.Ready, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space3)) {
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = stringResource(Res.string.lesson_close)) }
        Box(Modifier.weight(1f)) {
            AppProgressBar(state.index.toFloat() / state.tasks.size, color = MaterialTheme.extendedColors.brandGold)
        }
        Text(
            stringResource(Res.string.lesson_page_position, state.index + 1, state.tasks.size),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val Task.answerOptions get() = options.filter { it.role == OptionRole.ANSWER }

/** The question and the answer of the task, by its format. */
@Composable
private fun ColumnScope.TaskBody(state: TestUiState.Ready, actions: TestActions) {
    val spacing = MaterialTheme.spacing
    val task = state.task
    val question = @Composable { AppRichText(task.questionText, MaterialTheme.typography.headlineSmall) }
    val hint = @Composable { text: String ->
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    when (task.format) {
        TaskFormat.TRUE_FALSE -> {
            question()
            // the two big answers sit at the bottom, with some room above the check button
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = spacing.space8),
                horizontalArrangement = Arrangement.spacedBy(spacing.space3),
            ) {
                task.answerOptions.forEachIndexed { index, option ->
                    AppTrueFalseCard(
                        label = option.text.replaceFirstChar { it.uppercase() },
                        icon = if (index == 0) Icons.Rounded.Check else Icons.Rounded.Close,
                        state = optionState(state, option.label),
                        onClick = { actions.onSelect(option.label) },
                        enabled = !state.checked,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        TaskFormat.SHORT_ANSWER -> Scrolling {
            question()
            AppTextField(
                value = state.typed,
                onValueChange = actions.onType,
                label = stringResource(Res.string.test_your_answer),
                enabled = true,
                readOnly = state.checked,
                correct = state.result?.correct,
            )
            val answerHint = state.hint
            if (answerHint != null && !state.checked) {
                if (state.hintShown) {
                    Text(
                        stringResource(Res.string.test_hint, answerHint.firstLetter.uppercase(), answerHint.length),
                        style = MaterialTheme.typography.labelLarge,
                    )
                } else {
                    AppButton(stringResource(Res.string.test_hint_request), actions.onHint, variant = AppButtonVariant.TEXT)
                }
            }
        }
        TaskFormat.MATCHING -> Scrolling {
            question()
            hint(stringResource(Res.string.test_match_hint))
            val placeholder = stringResource(Res.string.test_pick_placeholder)
            Column(verticalArrangement = Arrangement.spacedBy(spacing.space3)) {
                state.slots.forEach { slot ->
                    val key = task.key.first { it.slot == slot }
                    val prompt = task.options.first { it.role == OptionRole.PROMPT && it.label == key.promptLabel }
                    AppMatchPair(
                        prompt = prompt.text,
                        answer = state.choices[slot]?.let { label -> task.answerOptions.firstOrNull { it.label == label }?.text },
                        placeholder = placeholder,
                        active = state.activeSlot == slot,
                        state = slotState(state, slot),
                        onClick = { actions.onActivate(slot) },
                        enabled = !state.checked,
                    )
                }
            }
            Chips {
                task.answerOptions.forEach { option ->
                    AppChoiceChip(option.text, selected = state.activeSlot?.let { state.choices[it] } == option.label, onClick = { actions.onPick(option.label) })
                }
            }
        }
        TaskFormat.ORDERING -> Scrolling {
            question()
            hint(stringResource(Res.string.test_order_hint))
            Column(verticalArrangement = Arrangement.spacedBy(spacing.space3)) {
                task.answerOptions.forEach { option ->
                    val position = state.choices.entries.firstOrNull { it.value == option.label }?.key
                    AppAnswerOption(
                        letter = position?.toString().orEmpty(),
                        text = option.text,
                        state = if (position == null) AnswerState.DEFAULT else slotState(state, position, picked = AnswerState.SELECTED),
                        onClick = { actions.onPick(option.label) },
                        enabled = !state.checked,
                    )
                }
            }
        }
        TaskFormat.GAP_CHOICE -> Scrolling {
            hint(stringResource(Res.string.test_gap_choice_hint))
            GapSentence(task.questionText) { number ->
                val word = state.choices[number]?.let { label -> task.answerOptions.firstOrNull { it.label == label }?.text }
                AppGapSlot(
                    number = number,
                    word = word,
                    state = if (word == null) AnswerState.DEFAULT else slotState(state, number, picked = AnswerState.SELECTED),
                    onClick = { actions.onClear(number) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.checked,
                )
            }
            Chips {
                task.answerOptions.forEach { option ->
                    AppChoiceChip(option.text, selected = option.label in state.choices.values, onClick = { actions.onPick(option.label) })
                }
            }
        }
        TaskFormat.GAP_FILL -> Scrolling {
            hint(stringResource(Res.string.test_gap_fill_hint))
            GapSentence(task.questionText) { number ->
                AppGapField(
                    value = state.texts[number].orEmpty(),
                    onValueChange = { actions.onTypeIn(number, it) },
                    description = stringResource(Res.string.test_gap_description, number),
                    state = slotState(state, number),
                    readOnly = state.checked,
                )
            }
        }
        else -> Scrolling {
            question()
            Column(verticalArrangement = Arrangement.spacedBy(spacing.space3)) {
                task.answerOptions.forEach { option ->
                    AppAnswerOption(
                        letter = option.label,
                        text = option.text,
                        state = optionState(state, option.label),
                        onClick = { actions.onSelect(option.label) },
                        enabled = !state.checked,
                    )
                }
            }
        }
    }
}

/** A column that scrolls when the task is longer than the screen. */
@Composable
private fun ColumnScope.Scrolling(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space4),
        content = content,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Chips(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space2),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space2),
    ) { content() }
}

/**
 * The sentence of a gap task, with a gap where the text has `[[n]]`. The gaps sit in the line of text as inline
 * content, so the text flows around them. `*italic*` markers must stay inside one text piece (not span a gap).
 */
@Composable
private fun GapSentence(text: String, gap: @Composable (Int) -> Unit) {
    val layout = MaterialTheme.layout
    val density = LocalDensity.current
    val width: TextUnit = with(density) { layout.gapFieldWidth.toSp() }
    val height: TextUnit = with(density) { layout.gapSlotHeight.toSp() }
    val parts = splitGaps(text)
    val annotated = buildAnnotatedString {
        parts.forEach { part ->
            when (part) {
                is TextPart.Text -> append(richText(part.text))
                is TextPart.Gap -> appendInlineContent("gap${part.number}", "[${part.number}]")
            }
        }
    }
    val inline = parts.filterIsInstance<TextPart.Gap>().associate { part ->
        "gap${part.number}" to InlineTextContent(Placeholder(width, height, PlaceholderVerticalAlign.Center)) { gap(part.number) }
    }
    // the line height follows the gaps, so it is left unset
    Text(annotated, inlineContent = inline, style = MaterialTheme.extendedTypography.reading.copy(lineHeight = TextUnit.Unspecified))
}

/** The state of an option of a multiple-choice or true-or-false task. */
private fun optionState(state: TestUiState.Ready, label: String): AnswerState {
    if (!state.checked) return if (state.selected == label) AnswerState.SELECTED else AnswerState.DEFAULT
    val right = state.task.key.any { it.optionLabel == label }
    return when {
        right -> AnswerState.CORRECT
        state.selected == label -> AnswerState.WRONG
        else -> AnswerState.DIMMED
    }
}

/** The state of one slot (a pair, a position, a gap): right or wrong after checking, [picked] before. */
private fun slotState(state: TestUiState.Ready, slot: Int, picked: AnswerState = AnswerState.DEFAULT): AnswerState {
    val result = state.result ?: return picked
    return if (result.slots[slot] == true) AnswerState.CORRECT else AnswerState.WRONG
}

@Composable
private fun Feedback(state: TestUiState.Ready, onNext: () -> Unit) {
    val task = state.task
    val correct = state.result?.correct == true
    val lines = buildList {
        if (!correct) {
            correctAnswerText(task)?.let { add(FeedbackLine(stringResource(Res.string.test_right_answer), it)) }
            state.chosenOption?.why?.let { add(FeedbackLine(stringResource(Res.string.test_why_not), it)) }
        }
        task.explanation?.let { add(FeedbackLine(null, it)) }
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        AppFeedbackSheet(
            correct = correct,
            title = stringResource(if (correct) Res.string.test_correct else Res.string.test_wrong),
            lines = lines,
            buttonText = stringResource(if (state.isLast) Res.string.test_finish else Res.string.common_next),
            onNext = onNext,
        )
    }
}

/** The right answer as text, by format: the pairs, the order, the words of the gaps, or the accepted answer. */
private fun correctAnswerText(task: Task): String? {
    fun text(label: String) = task.answerOptions.firstOrNull { it.label == label }?.text
    val key = task.key.sortedBy { it.slot }
    return when (task.format) {
        TaskFormat.SHORT_ANSWER -> task.acceptedAnswers.firstOrNull()?.text
        TaskFormat.GAP_FILL -> task.acceptedAnswers.groupBy { it.slot }.toSortedMap().values.joinToString(", ") { it.first().text }
        TaskFormat.MATCHING -> key.joinToString("; ") { slot ->
            val prompt = task.options.firstOrNull { it.role == OptionRole.PROMPT && it.label == slot.promptLabel }?.text
            "$prompt → ${text(slot.optionLabel)}"
        }
        TaskFormat.ORDERING -> key.joinToString(" → ") { text(it.optionLabel).orEmpty() }
        TaskFormat.GAP_CHOICE -> key.joinToString(", ") { text(it.optionLabel).orEmpty() }
        else -> key.firstOrNull()?.let { text(it.optionLabel) }
    }
}
