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
    /** Icons (Material Symbols Rounded). */
    val icon: Dp = 24.dp,
    /** Small icons, for example the check mark inside a choice. */
    val iconSmall: Dp = 20.dp,
    /** Square badge at the start of a choice card. */
    val badge: Dp = 44.dp,
    /** Smallest height of a choice card. */
    val choiceCardMinHeight: Dp = 64.dp,
    /** Smallest width of a button that does not fill the row. */
    val buttonMinWidth: Dp = 160.dp,
    /** Height of a progress bar. */
    val progressBarHeight: Dp = 8.dp,
    /** Numbered dot of a step in the onboarding step list. */
    val stepDot: Dp = 26.dp,
    /** Border of an unselected card or field. */
    val borderThin: Dp = 1.dp,
    /** Border of a selected card or option. */
    val borderThick: Dp = 2.dp,
    /** Logo on the welcome screen. */
    val logoLarge: Dp = 168.dp,
    /** Logo in the side panel. */
    val logoSmall: Dp = 52.dp,
    /** Thickness of the vertical line of a learning path. */
    val pathLine: Dp = 4.dp,
    /** Node of a lesson (circle) or a test (rounded square) on the learning path. */
    val pathNode: Dp = 42.dp,
    /** Node of a mini test on the learning path. */
    val pathNodeSmall: Dp = 34.dp,
    /** Size of the progress ring of a topic. */
    val progressRing: Dp = 40.dp,
    /** Thickness of the progress ring. */
    val progressRingStroke: Dp = 4.dp,
    /** Size of the daily goal ring on the home screen. */
    val goalRing: Dp = 88.dp,
    /** Thickness of the daily goal ring. */
    val goalRingStroke: Dp = 10.dp,
    /** Circle of a day in the week strip of the home screen. */
    val weekDot: Dp = 30.dp,
    /** Width of the gold bar at the side of a tip box in a lesson. */
    val accentBar: Dp = 4.dp,
    /** Smallest height of an answer option of a multiple-choice task. */
    val answerOptionMinHeight: Dp = 56.dp,
    /** Smallest height of the big true or false answer. */
    val trueFalseCardMinHeight: Dp = 140.dp,
    /** Smallest width of a gap filled from a word list. */
    val gapSlotMinWidth: Dp = 96.dp,
    /** Width of a gap filled by typing. */
    val gapFieldWidth: Dp = 128.dp,
    /** Height of a gap in a sentence, filled from a list or by typing. */
    val gapSlotHeight: Dp = 40.dp,
)

private val AppLayout = Layout()

/** The layout values, next to [MaterialTheme.shapes] and spacing. */
val MaterialTheme.layout: Layout
    @Composable
    @ReadOnlyComposable
    get() = AppLayout
