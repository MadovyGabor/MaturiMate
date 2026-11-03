package com.madovygabor.maturimate.features.learning.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.madovygabor.maturimate.core.presentation.theme.extendedTypography
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.uiComponents.AppButton
import com.madovygabor.maturimate.core.presentation.uiComponents.AppLessonBox
import com.madovygabor.maturimate.core.presentation.uiComponents.AppSegmentedProgress
import com.madovygabor.maturimate.core.presentation.uiComponents.LessonBoxType
import com.madovygabor.maturimate.core.presentation.utils.AppRichText
import com.madovygabor.maturimate.features.learning.domain.model.BlockType
import com.madovygabor.maturimate.features.learning.domain.model.LessonBlock
import com.madovygabor.maturimate.features.learning.domain.model.LessonPage
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.common_next
import maturimate.shared.generated.resources.learning_load_error
import maturimate.shared.generated.resources.learning_retry
import maturimate.shared.generated.resources.lesson_box_example
import maturimate.shared.generated.resources.lesson_box_note
import maturimate.shared.generated.resources.lesson_box_tip
import maturimate.shared.generated.resources.lesson_box_warning
import maturimate.shared.generated.resources.lesson_close
import maturimate.shared.generated.resources.lesson_contents
import maturimate.shared.generated.resources.lesson_finish
import maturimate.shared.generated.resources.lesson_minutes
import maturimate.shared.generated.resources.lesson_next_test
import maturimate.shared.generated.resources.lesson_page_description
import maturimate.shared.generated.resources.lesson_page_n
import maturimate.shared.generated.resources.lesson_page_position
import maturimate.shared.generated.resources.lesson_previous
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * A lesson, one page at a time (docs/learning/prototype.html). Next and previous page by the buttons or by
 * swiping; the table of contents is a sheet on a phone and a list at the side on a wide window.
 */
@Composable
fun LessonScreen(
    unitId: Long,
    onClose: () -> Unit,
    /** Opens the test that follows the lesson, after the lesson is closed. */
    onOpenTest: (Long) -> Unit,
    viewModel: LessonViewModel = koinViewModel(key = "lesson$unitId") { parametersOf(unitId) },
) {
    val state by viewModel.state.collectAsState()
    val close by rememberUpdatedState(onClose)
    val openTest by rememberUpdatedState(onOpenTest)
    // finishing the lesson saves it, closes the screen and goes on to the test that follows
    LaunchedEffect(viewModel) { viewModel.finished.collect { next -> close(); next?.let { openTest(it) } } }
    LessonContent(
        state = state,
        onClose = onClose,
        onNext = viewModel::next,
        onPrevious = viewModel::previous,
        onGoTo = viewModel::goTo,
        onFinish = viewModel::finish,
        onRetry = viewModel::load,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonContent(
    state: LessonUiState,
    onClose: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onGoTo: (Int) -> Unit,
    onFinish: () -> Unit,
    onRetry: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val layout = MaterialTheme.layout
    when (state) {
        LessonUiState.Loading -> Unit
        LessonUiState.Failed -> Column(Modifier.fillMaxSize().padding(spacing.space4)) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = stringResource(Res.string.lesson_close)) }
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = spacing.space6),
                verticalArrangement = Arrangement.spacedBy(spacing.space3),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(Res.string.learning_load_error), style = MaterialTheme.typography.bodyLarge)
                AppButton(stringResource(Res.string.learning_retry), onRetry)
            }
        }
        is LessonUiState.Ready -> BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            val wide = maxWidth >= layout.breakpointExpanded
            var contentsOpen by remember { mutableStateOf(false) }
            val swipeDistance = with(LocalDensity.current) { layout.touchTarget.toPx() }

            Row(Modifier.fillMaxSize()) {
                if (wide) {
                    Contents(
                        state = state,
                        onGoTo = onGoTo,
                        modifier = Modifier.width(layout.sidePanelWidth).fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .padding(spacing.space4),
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        // a swipe to the left goes to the next page, to the right to the previous one
                        .pointerInput(state.index) {
                            var drag = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { drag = 0f },
                                onDragEnd = { if (drag <= -swipeDistance) onNext() else if (drag >= swipeDistance) onPrevious() },
                                onHorizontalDrag = { _, amount -> drag += amount },
                            )
                        },
                    contentAlignment = Alignment.TopCenter,
                ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = layout.contentMaxWidth)
                        .fillMaxHeight()
                        .padding(spacing.space4),
                    verticalArrangement = Arrangement.spacedBy(spacing.space4),
                ) {
                    TopBar(state, wide, onClose, onOpenContents = { contentsOpen = true })
                    Text(
                        stringResource(Res.string.lesson_page_position, state.index + 1, state.pages.size) +
                            " · " + stringResource(Res.string.lesson_minutes, state.minutes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // a page is short enough to fit; if it does not, it scrolls
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(spacing.space4),
                    ) {
                        PageContent(state.page)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.space3), verticalAlignment = Alignment.CenterVertically) {
                        if (!state.isFirst) {
                            FilledTonalIconButton(onClick = onPrevious, modifier = Modifier.size(layout.touchTarget)) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(Res.string.lesson_previous))
                            }
                        }
                        AppButton(
                            text = stringResource(
                                when {
                                    !state.isLast -> Res.string.common_next
                                    state.nextTest != null -> Res.string.lesson_next_test
                                    else -> Res.string.lesson_finish
                                }
                            ),
                            onClick = if (state.isLast) onFinish else onNext,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                }
            }

            if (contentsOpen && !wide) {
                ModalBottomSheet(onDismissRequest = { contentsOpen = false }) {
                    Contents(
                        state = state,
                        onGoTo = { contentsOpen = false; onGoTo(it) },
                        modifier = Modifier.padding(horizontal = spacing.space4).padding(bottom = spacing.space6),
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBar(state: LessonUiState.Ready, wide: Boolean, onClose: () -> Unit, onOpenContents: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space3)) {
        IconButton(onClick = onClose) {
            Icon(Icons.Rounded.Close, contentDescription = stringResource(Res.string.lesson_close))
        }
        AppSegmentedProgress(
            total = state.pages.size,
            current = state.index,
            description = stringResource(Res.string.lesson_page_description, state.index + 1, state.pages.size),
            modifier = Modifier.weight(1f),
        )
        // on a wide window the contents are always at the side
        if (!wide) {
            IconButton(onClick = onOpenContents) {
                Icon(Icons.AutoMirrored.Rounded.FormatListBulleted, contentDescription = stringResource(Res.string.lesson_contents))
            }
        }
    }
}

/** The title, the paragraphs and the boxes of one page. */
@Composable
private fun PageContent(page: LessonPage) {
    page.title?.let { Text(it, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground) }
    page.blocks.forEach { block -> Block(block) }
}

@Composable
private fun Block(block: LessonBlock) {
    when (block.type) {
        BlockType.PARAGRAPH -> AppRichText(block.text, MaterialTheme.extendedTypography.reading, color = MaterialTheme.colorScheme.onBackground)
        BlockType.EXAMPLE -> AppLessonBox(LessonBoxType.EXAMPLE, stringResource(Res.string.lesson_box_example), block.text)
        BlockType.TIP -> AppLessonBox(LessonBoxType.TIP, stringResource(Res.string.lesson_box_tip), block.text)
        BlockType.NOTE -> AppLessonBox(LessonBoxType.NOTE, stringResource(Res.string.lesson_box_note), block.text)
        BlockType.WARNING -> AppLessonBox(LessonBoxType.WARNING, stringResource(Res.string.lesson_box_warning), block.text)
    }
}

/** The table of contents: every page by its title, the current one marked. */
@Composable
private fun Contents(state: LessonUiState.Ready, onGoTo: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
        Text(state.title, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        Text(stringResource(Res.string.lesson_contents), style = MaterialTheme.typography.headlineSmall, color = colors.onBackground)
        state.pages.forEachIndexed { index, page ->
            val current = index == state.index
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(min = MaterialTheme.layout.touchTarget),
                shape = MaterialTheme.shapes.medium,
                color = if (current) colors.primaryContainer else Color.Transparent,
                contentColor = if (current) colors.onPrimaryContainer else colors.onBackground,
                onClick = { onGoTo(index) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = spacing.space3, vertical = spacing.space2),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.space3),
                ) {
                    Surface(
                        modifier = Modifier.size(MaterialTheme.layout.stepDot),
                        shape = CircleShape,
                        color = if (current) colors.primary else colors.surfaceContainerHighest,
                        contentColor = if (current) colors.onPrimary else colors.onSurfaceVariant,
                    ) {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${index + 1}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Text(
                        page.title ?: stringResource(Res.string.lesson_page_n, index + 1),
                        style = if (current) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}
