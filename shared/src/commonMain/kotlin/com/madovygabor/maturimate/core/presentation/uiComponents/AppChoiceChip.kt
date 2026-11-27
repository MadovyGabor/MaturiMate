package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.motion
import com.madovygabor.maturimate.core.presentation.theme.pressScale
import com.madovygabor.maturimate.core.presentation.theme.spacing

/**
 * A short option from a small set, for example the daily goal or the English level
 * (docs/design-system/components/ChoiceChip.md). Only one chip of a group is selected. Choosing it fades the colors
 * over `duration-short`; a press shrinks it a little (`press-scale`).
 */
@Composable
fun AppChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val layout = MaterialTheme.layout
    val spacing = MaterialTheme.spacing
    val motion = MaterialTheme.motion
    val chipShape = MaterialTheme.shapes.small
    val colorSpec = motion.spec<Color>(motion.durationShort)
    val source = remember { MutableInteractionSource() }
    val container = animateColorAsState(if (selected) colors.primaryContainer else Color.Transparent, colorSpec, label = "container")
    val labelColor = animateColorAsState(if (selected) colors.onPrimaryContainer else colors.onBackground, colorSpec, label = "label")
    val borderColor = animateColorAsState(if (selected) colors.primaryContainer else colors.outline, colorSpec, label = "border")
    Row(
        modifier = modifier
            .pressScale(source)
            .heightIn(min = layout.touchTarget)
            .clip(chipShape)
            .drawBehind {
                drawRect(container.value)
                val strokePx = layout.borderThin.toPx()
                val cornerPx = chipShape.topStart.toPx(size, this)
                val half = strokePx / 2f
                drawRoundRect(
                    color = borderColor.value,
                    topLeft = Offset(half, half),
                    size = Size(size.width - strokePx, size.height - strokePx),
                    cornerRadius = CornerRadius((cornerPx - half).coerceAtLeast(0f)),
                    style = Stroke(width = strokePx),
                )
            }
            .selectable(
                selected = selected,
                interactionSource = source,
                indication = LocalIndication.current,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.space2, Alignment.CenterHorizontally),
    ) {
        if (selected) {
            AnimatedChipCheck(labelColor, Modifier.size(layout.iconSmall))
        }
        BasicText(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = { labelColor.value },
            maxLines = 1,
        )
    }
}

@Composable
private fun AnimatedChipCheck(tint: State<Color>, modifier: Modifier) {
    Icon(Icons.Rounded.Check, contentDescription = null, tint = tint.value, modifier = modifier)
}
