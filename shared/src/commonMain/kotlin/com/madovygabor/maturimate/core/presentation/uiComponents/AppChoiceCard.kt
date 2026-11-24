package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.motion
import com.madovygabor.maturimate.core.presentation.theme.pressScale
import com.madovygabor.maturimate.core.presentation.theme.spacing

/**
 * One choice from a short list, shown as a card: a badge, a title, a one-line description
 * and a mark (docs/design-system/components/ChoiceCard.md). Used for the teaching language
 * and the role in the onboarding. Choosing it fades the colors and the border over `duration-short`; a press shrinks it
 * a little (`press-scale`).
 *
 * @param badge Short text in the badge, for example "HU"; or give [badgeIcon] instead.
 * @param badgeIcon An icon in the badge, for example the student's cap.
 */
@Composable
fun AppChoiceCard(
    badge: String? = null,
    badgeIcon: ImageVector? = null,
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val layout = MaterialTheme.layout
    val spacing = MaterialTheme.spacing
    val motion = MaterialTheme.motion
    val colorSpec = motion.spec<Color>(motion.durationShort)
    val source = remember { MutableInteractionSource() }
    val container by animateColorAsState(if (selected) colors.primaryContainer else colors.surfaceContainerLowest, colorSpec, label = "container")
    val content by animateColorAsState(if (selected) colors.onPrimaryContainer else colors.onBackground, colorSpec, label = "content")
    val descriptionColor by animateColorAsState(if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant, colorSpec, label = "description")
    val borderColor by animateColorAsState(if (selected) colors.primary else colors.outline, colorSpec, label = "borderColor")
    val borderWidth by animateDpAsState(if (selected) layout.borderThick else layout.borderThin, motion.spec(motion.durationShort), label = "borderWidth")
    val badgeContainer by animateColorAsState(if (selected) colors.primary else colors.surfaceContainerHigh, colorSpec, label = "badge")
    val badgeContent by animateColorAsState(if (selected) colors.onPrimary else colors.onSurfaceVariant, colorSpec, label = "badgeContent")
    val markColor by animateColorAsState(if (selected) colors.primary else colors.surfaceContainerLowest, colorSpec, label = "mark")
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(source)
            .heightIn(min = layout.choiceCardMinHeight)
            .selectable(selected = selected, interactionSource = source, indication = LocalIndication.current, onClick = onClick, role = Role.RadioButton),
        shape = MaterialTheme.shapes.large,
        color = container,
        contentColor = content,
        border = BorderStroke(borderWidth, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.space4, vertical = spacing.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.space3),
        ) {
            Surface(
                modifier = Modifier.size(layout.badge),
                shape = MaterialTheme.shapes.medium,
                color = badgeContainer,
                contentColor = badgeContent,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (badgeIcon != null) {
                        Icon(badgeIcon, contentDescription = null, modifier = Modifier.size(layout.icon))
                    } else if (badge != null) {
                        Text(badge, style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.space1),
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                // an empty description leaves only the title (the language choice of the settings)
                if (description.isNotEmpty()) {
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = descriptionColor,
                    )
                }
            }
            // the mark shows the choice by shape as well as by color
            Surface(
                modifier = Modifier.size(layout.icon),
                shape = CircleShape,
                color = markColor,
                contentColor = colors.onPrimary,
                border = if (selected) null else BorderStroke(layout.borderThick, colors.outline),
            ) {
                AnimatedVisibility(
                    visible = selected,
                    enter = fadeIn(motion.spec(motion.durationShort)),
                    exit = fadeOut(motion.spec(motion.durationShort)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(layout.iconSmall))
                    }
                }
            }
        }
    }
}
