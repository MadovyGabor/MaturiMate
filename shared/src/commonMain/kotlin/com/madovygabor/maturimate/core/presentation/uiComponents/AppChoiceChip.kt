package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.motion
import com.madovygabor.maturimate.core.presentation.theme.pressScale

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
    val motion = MaterialTheme.motion
    val colorSpec = motion.spec<Color>(motion.durationShort)
    val source = remember { MutableInteractionSource() }
    // one animated color for both states: the chip's own colors would jump
    val container by animateColorAsState(if (selected) colors.primaryContainer else Color.Transparent, colorSpec, label = "container")
    val labelColor by animateColorAsState(if (selected) colors.onPrimaryContainer else colors.onBackground, colorSpec, label = "label")
    val borderColor by animateColorAsState(if (selected) colors.primaryContainer else colors.outline, colorSpec, label = "border")
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelLarge) },
        modifier = modifier
            .pressScale(source)
            .heightIn(min = MaterialTheme.layout.touchTarget)
            .semantics { role = Role.RadioButton },
        interactionSource = source,
        leadingIcon = if (selected) {
            { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(MaterialTheme.layout.iconSmall)) }
        } else {
            null
        },
        shape = MaterialTheme.shapes.small,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = container,
            labelColor = labelColor,
            selectedContainerColor = container,
            selectedLabelColor = labelColor,
            selectedLeadingIconColor = labelColor,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = borderColor,
            borderWidth = MaterialTheme.layout.borderThin,
            selectedBorderWidth = MaterialTheme.layout.borderThin,
            selectedBorderColor = borderColor,
        ),
    )
}
