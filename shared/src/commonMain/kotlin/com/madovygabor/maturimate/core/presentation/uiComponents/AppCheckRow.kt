package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing

/** How a [AppCheckRow] looks: the header of a subject card, a group inside it (a big part), or one item (a topic). */
enum class CheckRowStyle { SUBJECT, GROUP, ITEM }

/**
 * A row with a tick box (docs/design-system/components/CheckRow.md): a title, an optional second line and tag, and,
 * when [expanded] is given, a fold arrow. The tick box has three states, so a group whose items are only partly
 * ticked shows it. An item row is ticked by a tap anywhere on it; a subject or group row ticks with its box and folds
 * with the rest of the row, so the two are separate touch targets. A disabled row is greyed and cannot be ticked.
 */
@Composable
fun AppCheckRow(
    title: String,
    state: ToggleableState,
    onToggle: () -> Unit,
    /** What the tick box does, for screen readers, for example "Szóképek kiválasztása". */
    checkLabel: String,
    modifier: Modifier = Modifier,
    style: CheckRowStyle = CheckRowStyle.ITEM,
    subtitle: String? = null,
    tag: String? = null,
    enabled: Boolean = true,
    /** Null: no fold arrow. */
    expanded: Boolean? = null,
    onExpand: () -> Unit = {},
    expandLabel: String = "",
    collapseLabel: String = "",
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    val selected = style == CheckRowStyle.ITEM && state == ToggleableState.On && enabled
    val faded = if (enabled) Modifier else Modifier.alpha(DISABLED_ALPHA)

    // an item is one big target; a header ticks with its box and folds with the rest
    val rowAction = when {
        style == CheckRowStyle.ITEM -> Modifier.triStateToggleable(state = state, enabled = enabled, role = Role.Checkbox, onClick = onToggle)
            .semantics { contentDescription = checkLabel }
        expanded != null -> Modifier.clickable(role = Role.Button, onClickLabel = if (expanded) collapseLabel else expandLabel, onClick = onExpand)
            .semantics { stateDescription = if (expanded) collapseLabel else expandLabel }
        else -> Modifier
    }

    Surface(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) colors.secondaryContainer else Color.Transparent,
        contentColor = if (selected) colors.onSecondaryContainer else colors.onSurface,
    ) {
        Row(
            Modifier.defaultMinSize(minHeight = MaterialTheme.layout.touchTarget).then(faded),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (style == CheckRowStyle.ITEM) {
                // the row itself is the target: the box only shows the state
                Row(rowAction.padding(end = spacing.space3).weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.padding(spacing.space3)) { TriStateCheckbox(state = state, onClick = null, enabled = enabled) }
                    Texts(title, subtitle, style, Modifier.weight(1f))
                    if (tag != null) AppTag(tag)
                }
            } else {
                TriStateCheckbox(
                    state = state,
                    onClick = if (enabled) onToggle else null,
                    enabled = enabled,
                    modifier = Modifier.semantics { contentDescription = checkLabel },
                )
                Row(
                    rowAction.weight(1f).defaultMinSize(minHeight = MaterialTheme.layout.touchTarget).padding(end = spacing.space2),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.space2),
                ) {
                    Texts(title, subtitle, style, Modifier.weight(1f))
                    if (tag != null) AppTag(tag)
                    if (expanded != null) {
                        val turn by animateFloatAsState(if (expanded) 180f else 0f)
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.rotate(turn))
                    }
                }
            }
        }
    }
}

@Composable
private fun Texts(title: String, subtitle: String?, style: CheckRowStyle, modifier: Modifier) {
    val typography = MaterialTheme.typography
    Column(modifier) {
        when (style) {
            CheckRowStyle.SUBJECT -> Text(title, style = typography.titleLarge)
            CheckRowStyle.GROUP -> Text(title.uppercase(), style = typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            CheckRowStyle.ITEM -> Text(title, style = typography.titleSmall)
        }
        if (subtitle != null) {
            Text(subtitle, style = typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The look of a row that cannot be ticked (a topic not finished yet), as for a "Hamarosan" settings row. */
private const val DISABLED_ALPHA = 0.55f
