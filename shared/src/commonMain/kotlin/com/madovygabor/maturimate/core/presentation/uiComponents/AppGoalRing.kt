package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.madovygabor.maturimate.core.presentation.theme.extendedColors
import com.madovygabor.maturimate.core.presentation.theme.layout

/**
 * The daily goal as a ring (docs/design-system/components/GoalRing.md): the gold arc fills as the day's minutes
 * add up; when the goal is reached the ring turns green with a check mark.
 *
 * @param fraction How much of the goal is done, from 0 to 1.
 * @param value The minutes studied today, in the middle.
 * @param caption Under the value, for example "/ 10 perc".
 * @param description What a screen reader says, for example "6 of 10 minutes".
 */
@Composable
fun AppGoalRing(
    fraction: Float,
    reached: Boolean,
    value: String,
    caption: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val extended = MaterialTheme.extendedColors
    val layout = MaterialTheme.layout
    val arc = if (reached) extended.success else extended.brandGold
    Box(
        modifier = modifier.size(layout.goalRing).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(layout.goalRing)) {
            val width = layout.goalRingStroke.toPx()
            val inset = width / 2
            val arcSize = Size(size.width - width, size.height - width)
            val style = Stroke(width = width, cap = StrokeCap.Round)
            drawArc(colors.surfaceContainerHighest, 0f, 360f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = style)
            val sweep = if (reached) 360f else 360f * fraction.coerceIn(0f, 1f)
            if (sweep > 0f) drawArc(arc, -90f, sweep, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = style)
        }
        if (reached) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = extended.success, modifier = Modifier.size(layout.icon))
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value, style = MaterialTheme.typography.titleLarge, color = colors.onBackground)
                Text(caption, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
    }
}
