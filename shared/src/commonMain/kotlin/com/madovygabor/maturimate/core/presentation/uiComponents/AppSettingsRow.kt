package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing

/** What sits at the end of an [AppSettingsRow]. */
sealed interface SettingsTrailing {
    /** An arrow: the row opens something. */
    data object Arrow : SettingsTrailing

    /** Nothing: the row only shows a value. */
    data object None : SettingsTrailing

    /** A switch; a tap on the row turns it. */
    data class Toggle(val checked: Boolean) : SettingsTrailing

    /** A tag such as "Hamarosan"; the row does nothing yet and is greyed. */
    data class Soon(val text: String) : SettingsTrailing
}

/**
 * A titled group of [AppSettingsRow]s in one card (docs/design-system/components/SettingsRow.md).
 * The rows are divided by thin lines.
 */
@Composable
fun AppSettingsGroup(title: String, icon: ImageVector, modifier: Modifier = Modifier, rows: @Composable ColumnScope.() -> Unit) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
        Row(
            Modifier.padding(horizontal = spacing.space1).semantics { heading() },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.space2),
        ) {
            Icon(icon, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(MaterialTheme.layout.iconSmall))
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.onSurfaceVariant)
        }
        Surface(
            Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = colors.surfaceContainerLow,
            border = BorderStroke(MaterialTheme.layout.borderThin, colors.outlineVariant),
        ) {
            Column(content = rows)
        }
    }
}

/**
 * One setting: an icon, the name, the current value under it, and an arrow, a switch or a "Hamarosan" tag.
 * [danger] rows (reset, sign-out) are red. Put [divider] on every row but the first of a group.
 */
@Composable
fun AppSettingsRow(
    icon: ImageVector,
    title: String,
    value: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    trailing: SettingsTrailing = if (onClick != null) SettingsTrailing.Arrow else SettingsTrailing.None,
    danger: Boolean = false,
    divider: Boolean = true,
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    val soon = trailing is SettingsTrailing.Soon
    val action = onClick?.takeUnless { soon }
    if (divider) HorizontalDivider(color = colors.outlineVariant)
    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (action == null) Modifier
                else Modifier.clickable(role = if (trailing is SettingsTrailing.Toggle) Role.Switch else Role.Button, onClick = action)
            )
            .heightIn(min = MaterialTheme.layout.choiceCardMinHeight)
            .padding(horizontal = spacing.space4, vertical = spacing.space2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.space3),
    ) {
        val faded = if (soon) Modifier.alpha(DISABLED_ALPHA) else Modifier
        Surface(
            faded,
            shape = MaterialTheme.shapes.small,
            color = if (danger) colors.errorContainer else colors.surfaceContainerHighest,
            contentColor = if (danger) colors.error else colors.onSurfaceVariant,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(spacing.space2).size(MaterialTheme.layout.iconSmall))
        }
        Column(faded.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = if (danger) colors.error else colors.onSurface)
            value?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
        }
        when (trailing) {
            SettingsTrailing.Arrow -> Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = colors.onSurfaceVariant)
            SettingsTrailing.None -> Unit
            // the row takes the tap, so the switch itself is only a picture of the state
            is SettingsTrailing.Toggle -> Switch(checked = trailing.checked, onCheckedChange = null)
            is SettingsTrailing.Soon -> AppTag(trailing.text)
        }
    }
}

/** A row of a later step: seen, so it is not forgotten, but not usable yet. */
private const val DISABLED_ALPHA = 0.55f
