package com.madovygabor.maturimate.features.learning.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
import com.madovygabor.maturimate.core.presentation.theme.extendedColors
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.uiComponents.AnswerState
import com.madovygabor.maturimate.core.presentation.uiComponents.AppAnswerOption
import com.madovygabor.maturimate.core.presentation.uiComponents.AppButton
import com.madovygabor.maturimate.core.presentation.uiComponents.AppButtonVariant
import com.madovygabor.maturimate.core.presentation.uiComponents.AppFeedbackSheet
import com.madovygabor.maturimate.core.presentation.uiComponents.AppProgressBar
import com.madovygabor.maturimate.core.presentation.uiComponents.AppTextField
import com.madovygabor.maturimate.core.presentation.uiComponents.AppTrueFalseCard
import com.madovygabor.maturimate.core.presentation.uiComponents.FeedbackLine
import com.madovygabor.maturimate.core.presentation.utils.AppRichText
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
import maturimate.shared.generated.resources.test_hint
import maturimate.shared.generated.resources.test_hint_request
import maturimate.shared.generated.resources.test_right_answer
import maturimate.shared.generated.resources.test_wrong
import maturimate.shared.generated.resources.test_why_not
import maturimate.shared.generated.resources.test_your_answer
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

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
        onClose = onClose,
        onSelect = viewModel::select,
        onType = viewModel::type,
        onHint = viewModel::showHint,
        onCheck = viewModel::check,
        onNext = viewModel::next,
        onRetry = viewModel::load,
    )
}

@Composable
fun TestContent(
    state: TestUiState,
    onClose: () -> Unit,
    onSelect: (String) -> Unit,
    onType: (String) -> Unit,
    onHint: () -> Unit,
    onCheck: () -> Unit,
    onNext: () -> Unit,
    onRetry: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val layout = MaterialTheme.layout
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
        when (state) {
            TestUiState.Loading -> Unit
            TestUiState.Failed, TestUiState.Empty -> Column(Modifier.fillMaxSize().padding(spacing.space4)) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = stringResource(Res.string.lesson_close)) }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = spacing.space6),
                    verticalArrangement = Arrangement.spacedBy(spacing.space3),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (state == TestUiState.Empty) {
                        Text(stringResource(Res.string.test_empty), style = MaterialTheme.typography.bodyLarge)
                    } else {
                        Text(stringResource(Res.string.learning_load_error), style = MaterialTheme.typography.bodyLarge)
                        AppButton(stringResource(Res.string.learning_retry), onRetry)
                    }
                }
            }
            is TestUiState.Ready -> BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = layout.contentMaxWidth).fillMaxHeight()) {
                    Column(
                        modifier = Modifier.weight(1f).padding(horizontal = spacing.space4).padding(top = spacing.space4),
                        verticalArrangement = Arrangement.spacedBy(spacing.space4),
                    ) {
                        TopBar(state, onClose)
                        Text(state.title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TaskBody(state, onSelect, onType, onHint)
                    }
                    if (state.checked) {
                        Feedback(state, onNext)
                    } else {
                        AppButton(
                            text = stringResource(Res.string.test_check),
                            onClick = onCheck,
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

/** The question and the answer of the task, by its format. */
@Composable
private fun ColumnScope.TaskBody(state: TestUiState.Ready, onSelect: (String) -> Unit, onType: (String) -> Unit, onHint: () -> Unit) {
    val spacing = MaterialTheme.spacing
    val task = state.task
    val question = @Composable { AppRichText(task.questionText, MaterialTheme.typography.headlineSmall) }
    when (task.format) {
        TaskFormat.TRUE_FALSE -> {
            question()
            // the two big answers sit at the bottom, with some room above the check button
            Spacer(Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = spacing.space8),
                horizontalArrangement = Arrangement.spacedBy(spacing.space3),
            ) {
                val options = task.options.filter { it.role == OptionRole.ANSWER }
                options.forEachIndexed { index, option ->
                    AppTrueFalseCard(
                        label = option.text.replaceFirstChar { it.uppercase() },
                        icon = if (index == 0) Icons.Rounded.Check else Icons.Rounded.Close,
                        state = answerState(state, option.label),
                        onClick = { onSelect(option.label) },
                        enabled = !state.checked,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        TaskFormat.SHORT_ANSWER -> Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(spacing.space4),
        ) {
            question()
            AppTextField(
                value = state.typed,
                onValueChange = onType,
                label = stringResource(Res.string.test_your_answer),
                enabled = true,
                readOnly = state.checked,
                correct = state.result?.correct,
            )
            val hint = state.hint
            if (hint != null && !state.checked) {
                if (state.hintShown) {
                    Text(
                        stringResource(Res.string.test_hint, hint.firstLetter.uppercase(), hint.length),
                        style = MaterialTheme.typography.labelLarge,
                    )
                } else {
                    AppButton(stringResource(Res.string.test_hint_request), onHint, variant = AppButtonVariant.TEXT)
                }
            }
        }
        else -> Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(spacing.space4),
        ) {
            question()
            Column(verticalArrangement = Arrangement.spacedBy(spacing.space3)) {
                task.options.filter { it.role == OptionRole.ANSWER }.forEach { option ->
                    AppAnswerOption(
                        letter = option.label,
                        text = option.text,
                        state = answerState(state, option.label),
                        onClick = { onSelect(option.label) },
                        enabled = !state.checked,
                    )
                }
            }
        }
    }
}

/** Before checking only the chosen option is marked; after it the chosen and the right ones are, the rest is dimmed. */
private fun answerState(state: TestUiState.Ready, label: String): AnswerState {
    if (!state.checked) return if (state.selected == label) AnswerState.SELECTED else AnswerState.DEFAULT
    val right = state.task.key.any { it.optionLabel == label }
    return when {
        right -> AnswerState.CORRECT
        state.selected == label -> AnswerState.WRONG
        else -> AnswerState.DIMMED
    }
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

/** The right answer as text: the key option for a choice task, the first accepted answer for a written one. */
private fun correctAnswerText(task: Task): String? =
    if (task.format == TaskFormat.SHORT_ANSWER) task.acceptedAnswers.firstOrNull()?.text
    else task.key.firstOrNull()?.let { key -> task.options.firstOrNull { it.label == key.optionLabel && it.role == OptionRole.ANSWER }?.text }
