package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.semantics.Role
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing

/**
 * One choice from a short list, shown as a card: a badge, a title, a one-line description
 * and a mark (docs/design-system/components/ChoiceCard.md). Used for the teaching language
 * and the role in the onboarding.
 *
 * @param badge Short text in the badge, for example "HU".
 */
@Composable
fun AppChoiceCard(
    badge: String,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val layout = MaterialTheme.layout
    val spacing = MaterialTheme.spacing
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = layout.choiceCardMinHeight)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        shape = MaterialTheme.shapes.large,
        color = if (selected) colors.primaryContainer else colors.surfaceContainerLowest,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onBackground,
        border = if (selected) BorderStroke(layout.borderThick, colors.primary) else BorderStroke(layout.borderThin, colors.outline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.space4, vertical = spacing.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.space3),
        ) {
            Surface(
                modifier = Modifier.size(layout.badge),
                shape = MaterialTheme.shapes.medium,
                color = if (selected) colors.primary else colors.surfaceContainerHigh,
                contentColor = if (selected) colors.onPrimary else colors.onSurfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(badge, style = MaterialTheme.typography.titleSmall)
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.space1),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                )
            }
            // the mark shows the choice by shape as well as by color
            Surface(
                modifier = Modifier.size(layout.icon),
                shape = CircleShape,
                color = if (selected) colors.primary else colors.surfaceContainerLowest,
                contentColor = colors.onPrimary,
                border = if (selected) null else BorderStroke(layout.borderThick, colors.outline),
            ) {
                if (selected) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(layout.iconSmall))
                    }
                }
            }
        }
    }
}
