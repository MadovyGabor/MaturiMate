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

/**
 * The practice tab (docs/practice/prototype.html): the free practice card, which opens the picker, and a card
 * naming what comes next (the mistake practice, weak topics, old material; Milestone 9a.3 to 9a.5).
 */
@Composable
fun PracticeScreen(onOpenPicker: () -> Unit, viewModel: PracticeViewModel = koinViewModel()) {
    LaunchedEffect(Unit) { viewModel.refresh() }
    val state by viewModel.state.collectAsState()
    PracticeContent(state, onOpenPicker, onRetry = viewModel::refresh)
}

@Composable
fun PracticeContent(state: PracticeUiState, onOpenPicker: () -> Unit, onRetry: () -> Unit) {
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

/** What the tab will hold next; 9a.3 to 9a.5 replace it with their own cards. */
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
