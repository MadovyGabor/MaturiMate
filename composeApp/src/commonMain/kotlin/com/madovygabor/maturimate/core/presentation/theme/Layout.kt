package com.madovygabor.maturimate.core.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Values from docs/design-system/tokens.json (layout). Change them there first.

/** Window breakpoints and the fixed sizes of the layout. */
@Immutable
data class Layout(
    /** From this window width: a centered column instead of the full width. */
    val breakpointMedium: Dp = 600.dp,
    /** From this window width: two panes, for example the onboarding side panel. */
    val breakpointExpanded: Dp = 840.dp,
    /** Widest column of onboarding questions and forms. */
    val contentMaxWidth: Dp = 560.dp,
    /** Width of the side panel in the two-pane layout. */
    val sidePanelWidth: Dp = 360.dp,
    /** Smallest height of anything tappable. */
    val touchTarget: Dp = 48.dp,
    /** Logo on the welcome screen. */
    val logoLarge: Dp = 168.dp,
    /** Logo in the side panel. */
    val logoSmall: Dp = 52.dp,
)

private val AppLayout = Layout()

/** The layout values, next to [MaterialTheme.shapes] and spacing. */
val MaterialTheme.layout: Layout
    @Composable
    @ReadOnlyComposable
    get() = AppLayout
