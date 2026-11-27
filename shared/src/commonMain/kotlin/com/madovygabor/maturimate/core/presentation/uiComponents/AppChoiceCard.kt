package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
    val cardShape = MaterialTheme.shapes.large
    val badgeShape = MaterialTheme.shapes.medium
    val colorSpec = motion.spec<Color>(motion.durationShort)
    val source = remember { MutableInteractionSource() }
    val container = animateColorAsState(if (selected) colors.primaryContainer else colors.surfaceContainerLowest, colorSpec, label = "container")
    val content = animateColorAsState(if (selected) colors.onPrimaryContainer else colors.onBackground, colorSpec, label = "content")
    val descriptionColor = animateColorAsState(if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant, colorSpec, label = "description")
    val borderColor = animateColorAsState(if (selected) colors.primary else colors.outline, colorSpec, label = "borderColor")
    val borderWidth = animateDpAsState(if (selected) layout.borderThick else layout.borderThin, motion.spec(motion.durationShort), label = "borderWidth")
    val badgeContainer = animateColorAsState(if (selected) colors.primary else colors.surfaceContainerHigh, colorSpec, label = "badge")
    val badgeContent = animateColorAsState(if (selected) colors.onPrimary else colors.onSurfaceVariant, colorSpec, label = "badgeContent")
    val markColor = animateColorAsState(if (selected) colors.primary else colors.surfaceContainerLowest, colorSpec, label = "mark")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(source)
            .heightIn(min = layout.choiceCardMinHeight)
            .clip(cardShape)
            .drawBehind { drawRect(container.value) }
            .selectable(selected = selected, interactionSource = source, indication = LocalIndication.current, onClick = onClick, role = Role.RadioButton)
            .drawWithContent {
                drawContent()
                val strokePx = borderWidth.value.toPx()
                val cornerPx = cardShape.topStart.toPx(size, this)
                val half = strokePx / 2f
                drawRoundRect(
                    color = borderColor.value,
                    topLeft = Offset(half, half),
                    size = Size(size.width - strokePx, size.height - strokePx),
                    cornerRadius = CornerRadius((cornerPx - half).coerceAtLeast(0f)),
                    style = Stroke(width = strokePx),
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.space4, vertical = spacing.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.space3),
        ) {
            Box(
                modifier = Modifier
                    .size(layout.badge)
                    .clip(badgeShape)
                    .drawBehind { drawRect(badgeContainer.value) },
                contentAlignment = Alignment.Center,
            ) {
                if (badgeIcon != null) {
                    AnimatedTintedIcon(badgeIcon, badgeContent, Modifier.size(layout.icon))
                } else if (badge != null) {
                    BasicText(badge, style = MaterialTheme.typography.titleSmall, color = { badgeContent.value })
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.space1),
            ) {
                BasicText(title, style = MaterialTheme.typography.titleMedium, color = { content.value })
                // an empty description leaves only the title (the language choice of the settings)
                if (description.isNotEmpty()) {
                    BasicText(
                        description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = { descriptionColor.value },
                    )
                }
            }
            // the mark shows the choice by shape as well as by color
            Box(
                modifier = Modifier
                    .size(layout.icon)
                    .clip(CircleShape)
                    .drawBehind {
                        drawCircle(markColor.value)
                        if (!selected) {
                            val strokePx = layout.borderThick.toPx()
                            drawCircle(
                                color = colors.outline,
                                radius = (size.minDimension - strokePx) / 2f,
                                style = Stroke(width = strokePx),
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = selected,
                    enter = fadeIn(motion.spec(motion.durationShort)),
                    exit = fadeOut(motion.spec(motion.durationShort)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(layout.iconSmall))
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedTintedIcon(icon: ImageVector, tint: State<Color>, modifier: Modifier) {
    Icon(icon, contentDescription = null, tint = tint.value, modifier = modifier)
}
