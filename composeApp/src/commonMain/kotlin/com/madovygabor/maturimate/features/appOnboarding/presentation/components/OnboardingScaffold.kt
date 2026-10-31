package com.madovygabor.maturimate.features.appOnboarding.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.madovygabor.maturimate.core.presentation.systemPadding
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.uiComponents.AppLogo
import maturimate.composeapp.generated.resources.Res
import maturimate.composeapp.generated.resources.app_name
import maturimate.composeapp.generated.resources.onboarding_side_tagline
import org.jetbrains.compose.resources.stringResource

/**
 * The frame of every onboarding screen, following docs/onboarding/prototype.html:
 * one column on a phone; a centered column from `breakpoint-medium`; from
 * `breakpoint-expanded` two panes, with the logo and a short text on the left.
 *
 * @param actions The buttons at the bottom, stacked; the main one first.
 * @param content The screen's own content, scrolled when it does not fit.
 */
@Composable
fun OnboardingScaffold(
    actions: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemPadding(),
    ) {
        val layout = MaterialTheme.layout
        if (maxWidth >= layout.breakpointExpanded) {
            Row(Modifier.fillMaxSize()) {
                SidePanel(Modifier.width(layout.sidePanelWidth).fillMaxHeight())
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                MainColumn(wide = true, actions = actions, content = content)
            }
        } else {
            MainColumn(wide = maxWidth >= layout.breakpointMedium, actions = actions, content = content)
        }
    }
}

@Composable
private fun MainColumn(
    wide: Boolean,
    actions: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val sideMargin = if (wide) spacing.space6 else spacing.space4
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .widthIn(max = MaterialTheme.layout.contentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = sideMargin, vertical = spacing.space8),
            verticalArrangement = Arrangement.spacedBy(spacing.space4),
            content = content,
        )
        Column(
            modifier = Modifier
                .widthIn(max = MaterialTheme.layout.contentMaxWidth)
                .fillMaxWidth()
                .padding(start = sideMargin, end = sideMargin, top = spacing.space3, bottom = spacing.space6),
            verticalArrangement = Arrangement.spacedBy(spacing.space2),
            content = actions,
        )
    }
}

@Composable
private fun SidePanel(modifier: Modifier) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = spacing.space8, vertical = spacing.space8),
        verticalArrangement = Arrangement.spacedBy(spacing.space6),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.space3),
        ) {
            AppLogo(
                size = MaterialTheme.layout.logoSmall,
                contentDescription = null,
                tileShape = MaterialTheme.shapes.medium,
            )
            Text(
                text = stringResource(Res.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            text = stringResource(Res.string.onboarding_side_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
