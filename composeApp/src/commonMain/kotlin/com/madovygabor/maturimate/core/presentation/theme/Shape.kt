package com.madovygabor.maturimate.core.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Values from docs/design-system/tokens.json (radius, spacing). Change them there first.

/** The Material 3 shape scale. radius-full (pill) is set by the components that use it. */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** The spacing steps of the design system. */
@Immutable
data class Spacing(
    /** Between an icon and its text. */
    val space1: Dp = 4.dp,
    /** Gap between chips, small inner padding. */
    val space2: Dp = 8.dp,
    /** Gap between list items and answer options. */
    val space3: Dp = 12.dp,
    /** Card padding, screen side margin. */
    val space4: Dp = 16.dp,
    /** Gap between sections. */
    val space6: Dp = 24.dp,
    /** Gap between large blocks. */
    val space8: Dp = 32.dp,
)

private val AppSpacing = Spacing()

/** The spacing steps, next to [MaterialTheme.shapes]. */
val MaterialTheme.spacing: Spacing
    @Composable
    @ReadOnlyComposable
    get() = AppSpacing
