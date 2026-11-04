package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.madovygabor.maturimate.core.presentation.theme.extendedColors
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing

/** How a day of the week strip looks. */
enum class WeekDotState {
    /** The goal of the day was reached. */
    DONE,

    /** A day gone by without the goal. */
    MISSED,

    /** Today, goal not reached yet. */
    TODAY,

    /** A day still to come. */
    LATER,
}

/** One day of an [AppWeekStrip]: the short name ("H") and how it looks; [description] is for screen readers. */
data class WeekStripDay(val label: String, val state: WeekDotState, val description: String)

/**
 * The days of the current week, Monday first (docs/design-system/components/WeekStrip.md). A day whose goal was
 * reached has a gold circle with a check mark; today has an outline; days to come have a dashed-looking thin outline.
 */
@Composable
fun AppWeekStrip(days: List<WeekStripDay>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val layout = MaterialTheme.layout
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEach { day ->
            Column(
                modifier = Modifier.semantics { contentDescription = day.description },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space1),
            ) {
                val (fill, border) = when (day.state) {
                    WeekDotState.DONE -> MaterialTheme.extendedColors.brandGold to null
                    WeekDotState.MISSED -> colors.surfaceContainerHighest to null
                    WeekDotState.TODAY -> colors.surfaceContainerHigh to BorderStroke(layout.borderThick, colors.primary)
                    WeekDotState.LATER -> colors.surfaceContainerHigh to BorderStroke(layout.borderThin, colors.outlineVariant)
                }
                Surface(shape = CircleShape, color = fill, border = border, modifier = Modifier.size(layout.weekDot)) {
                    Box(contentAlignment = Alignment.Center) {
                        if (day.state == WeekDotState.DONE) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.extendedColors.onBrandGold,
                                modifier = Modifier.size(layout.iconSmall),
                            )
                        }
                    }
                }
                Text(
                    day.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (day.state == WeekDotState.TODAY) colors.onBackground else colors.onSurfaceVariant,
                )
            }
        }
    }
}
