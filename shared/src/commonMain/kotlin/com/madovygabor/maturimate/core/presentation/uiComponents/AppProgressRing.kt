package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
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
import com.madovygabor.maturimate.core.presentation.theme.layout

/**
 * Circular progress with the count in the middle (docs/design-system/components/ProgressRing.md).
 * Shows how many steps of a topic are done, for example "1/3".
 *
 * @param description What a screen reader says, for example "1 of 3 steps done".
 */
@Composable
fun AppProgressRing(
    done: Int,
    total: Int,
    description: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val stroke = MaterialTheme.layout.progressRingStroke
    val fraction = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .size(MaterialTheme.layout.progressRing)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(MaterialTheme.layout.progressRing)) {
            val width = stroke.toPx()
            val inset = width / 2
            val arcSize = Size(size.width - width, size.height - width)
            val style = Stroke(width = width, cap = StrokeCap.Round)
            drawArc(colors.surfaceContainerHighest, 0f, 360f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = style)
            if (fraction > 0f) {
                drawArc(colors.primary, -90f, 360f * fraction, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = style)
            }
        }
        Text("$done/$total", style = MaterialTheme.typography.labelSmall, color = colors.onBackground)
    }
}
