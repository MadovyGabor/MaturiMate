package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.motion

/**
 * Horizontal progress bar (docs/design-system/components/ProgressBar.md).
 *
 * `primary` for steps; pass `extendedColors.brandGold` for the daily goal and the streak, and
 * then always show the value as text too, because gold on the light track has low contrast.
 *
 * A new [fraction] fills (or empties) the bar over `duration-medium`; with reduced motion it jumps.
 * When [initialFraction] differs from [fraction], the bar starts there and animates on arrival
 * without triggering an extra composition of the caller.
 *
 * @param fraction From 0 to 1.
 */
@Composable
fun AppProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    initialFraction: Float = fraction,
) {
    val motion = MaterialTheme.motion
    val target = fraction.coerceIn(0f, 1f)
    val start = if (motion.reduced) target else initialFraction.coerceIn(0f, 1f)
    val shown = remember { Animatable(start) }
    LaunchedEffect(target, motion.reduced) {
        if (motion.reduced) {
            shown.snapTo(target)
        } else if (shown.value != target) {
            shown.animateTo(target, motion.spec(motion.durationMedium))
        }
    }
    LinearProgressIndicator(
        progress = { shown.value },
        modifier = modifier.fillMaxWidth().height(MaterialTheme.layout.progressBarHeight),
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        strokeCap = StrokeCap.Round,
        // one continuous bar: no gap between the fill and the track, no stop dot
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}
