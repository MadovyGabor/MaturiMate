package com.madovygabor.maturimate.core.presentation

import androidx.compose.ui.Modifier

/** Keeps clear of all system bars. */
expect fun Modifier.systemPadding(): Modifier

/**
 * Keeps clear of the status bar and the sides only. The bottom is left to the screen, so a bar at the bottom can
 * reach behind the system navigation bar instead of leaving a strip of another color.
 */
expect fun Modifier.systemPaddingExceptBottom(): Modifier

/** Keeps clear of the system navigation bar at the bottom, for a screen without a bar of its own. */
expect fun Modifier.systemBottomPadding(): Modifier
