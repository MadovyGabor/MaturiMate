package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import com.madovygabor.maturimate.core.presentation.theme.extendedColors
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing

/** The look of a step of a learning path. A final test uses [TEST]. */
enum class PathStepStyle { LESSON, MINI, TEST }

enum class PathStepState { DONE, CURRENT, TODO }

/**
 * One step of a learning path: a node on the vertical line and a card
 * (docs/design-system/components/PathStep.md). The line is drawn by the steps themselves: it is gold
 * down to the current step and grey below it, so a column of steps (without gaps between them)
 * shows the progress.
 *
 * @param stateLabel A small label for a done or current step ("Kész", "Következő"); null for a step still ahead.
 * @param meta One line under the title, for example "3 perc" or "5 feladat".
 * @param isFirst The line starts at the node.
 * @param isLast The line ends at the node.
 */
@Composable
fun AppPathStep(
    style: PathStepStyle,
    state: PathStepState,
    title: String,
    meta: String,
    stateLabel: String?,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val gold = MaterialTheme.extendedColors.brandGold
    val layout = MaterialTheme.layout
    val spacing = MaterialTheme.spacing
    val nodeSize = if (style == PathStepStyle.MINI) layout.pathNodeSmall else layout.pathNode
    val lineAbove = if (state == PathStepState.TODO) colors.surfaceContainerHighest else gold
    val lineBelow = if (state == PathStepState.DONE) gold else colors.surfaceContainerHighest

    Row(modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // the node column: the line above and below the node, and the node in the middle
        Box(modifier = Modifier.width(layout.pathNode + spacing.space2).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Column(Modifier.fillMaxHeight().width(layout.pathLine)) {
                Box(Modifier.weight(1f).fillMaxWidth().background(if (isFirst) Color.Transparent else lineAbove))
                Box(Modifier.weight(1f).fillMaxWidth().background(if (isLast) Color.Transparent else lineBelow))
            }
            PathNode(style, state, nodeSize, halo = spacing.space2)
        }
        Surface(
            modifier = Modifier
                .weight(1f)
                .alpha(if (state == PathStepState.TODO) 0.6f else 1f)
                .padding(start = spacing.space3, top = spacing.space2, bottom = spacing.space2)
                .clip(MaterialTheme.shapes.medium)
                .clickable(role = Role.Button, onClick = onClick),
            shape = MaterialTheme.shapes.medium,
            color = when (style) {
                PathStepStyle.LESSON -> colors.surfaceContainerLow
                PathStepStyle.MINI -> colors.tertiaryContainer
                PathStepStyle.TEST -> colors.primaryContainer
            },
            contentColor = when (style) {
                PathStepStyle.LESSON -> colors.onBackground
                PathStepStyle.MINI -> colors.onTertiaryContainer
                PathStepStyle.TEST -> colors.onPrimaryContainer
            },
            border = if (state == PathStepState.CURRENT) BorderStroke(layout.borderThick, colors.primary)
            else if (style == PathStepStyle.TEST) null
            else BorderStroke(layout.borderThin, colors.outlineVariant),
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = spacing.space3,
                    vertical = if (style == PathStepStyle.MINI) spacing.space2 else spacing.space3,
                ),
                verticalArrangement = Arrangement.spacedBy(spacing.space1),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.space2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title,
                        modifier = Modifier.weight(1f),
                        style = if (style == PathStepStyle.MINI) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                    )
                    if (stateLabel != null) StateTag(stateLabel, done = state == PathStepState.DONE)
                }
                Text(meta, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PathNode(style: PathStepStyle, state: PathStepState, size: Dp, halo: Dp) {
    val colors = MaterialTheme.colorScheme
    val layout = MaterialTheme.layout
    val shape: Shape = if (style == PathStepStyle.TEST) MaterialTheme.shapes.medium else CircleShape
    val fill = when (state) {
        PathStepState.DONE -> colors.primary
        PathStepState.CURRENT -> MaterialTheme.extendedColors.brandGold
        PathStepState.TODO -> colors.surfaceContainerHigh
    }
    val content = when (state) {
        PathStepState.DONE -> colors.onPrimary
        PathStepState.CURRENT -> MaterialTheme.extendedColors.onBrandGold
        PathStepState.TODO -> colors.onSurfaceVariant
    }
    // a halo around the current step, so it stands out by shape as well as by color
    Box(
        modifier = if (state == PathStepState.CURRENT) {
            Modifier.size(size + halo).clip(shape).background(colors.primaryContainer)
        } else Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(size),
            shape = shape,
            color = fill,
            contentColor = content,
            border = when (state) {
                PathStepState.DONE -> null
                PathStepState.CURRENT -> BorderStroke(layout.borderThick, colors.primary)
                PathStepState.TODO -> BorderStroke(layout.borderThick, colors.outline)
            },
        ) {
            Box(contentAlignment = Alignment.Center) {
                // a step still ahead is locked until the ones before it are done
                val icon = if (state == PathStepState.DONE) Icons.Rounded.Check else if (state == PathStepState.TODO) Icons.Rounded.Lock else when (style) {
                    PathStepStyle.LESSON -> Icons.AutoMirrored.Rounded.MenuBook
                    PathStepStyle.MINI -> Icons.Rounded.Quiz
                    PathStepStyle.TEST -> Icons.Rounded.EmojiEvents
                }
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(if (style == PathStepStyle.MINI) layout.iconSmall else layout.icon),
                )
            }
        }
    }
}

@Composable
private fun StateTag(label: String, done: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (done) MaterialTheme.extendedColors.successContainer else colors.primaryContainer,
        contentColor = if (done) MaterialTheme.extendedColors.onSuccessContainer else colors.onPrimaryContainer,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.space2, vertical = MaterialTheme.spacing.space1 / 2),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
