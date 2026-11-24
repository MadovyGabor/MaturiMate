package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.remember
import com.madovygabor.maturimate.core.presentation.theme.pressScale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.minimumInteractiveComponentSize
import com.madovygabor.maturimate.core.presentation.theme.Layout
import com.madovygabor.maturimate.core.presentation.theme.spacing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.common_loading
import org.jetbrains.compose.resources.stringResource
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.madovygabor.maturimate.core.presentation.theme.layout

/** The variants of [AppButton]. */
enum class AppButtonVariant {
    /** The screen's one main action. */
    FILLED,

    /** A secondary action next to the main one, for example "Összes hiba" on the practice tab. */
    TONAL,

    /** A third-level action, for example "Folytatás Google-lel". */
    OUTLINED,

    /** A quiet action, for example "Már van fiókom". */
    TEXT,
}

/**
 * Pill-shaped button of the design system (docs/design-system/components/Button.md). A press shrinks it a little (`press-scale`).
 *
 * @param text A verb that says what happens: "Tovább", "Kezdjük".
 */
@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AppButtonVariant = AppButtonVariant.FILLED,
    enabled: Boolean = true,
    /** A slimmer button for inside a card: [Layout.buttonCompactHeight] tall, still [Layout.touchTarget] to tap. */
    compact: Boolean = false,
    /** Work is under way: a spinner before the text, and the button does not react (the text stays, so the width does not jump). */
    loading: Boolean = false,
) {
    val layout = MaterialTheme.layout
    val source = remember { MutableInteractionSource() }
    val base = modifier.pressScale(source)
    val sizedModifier = if (compact) {
        base.minimumInteractiveComponentSize().heightIn(min = layout.buttonCompactHeight)
    } else base.heightIn(min = layout.touchTarget)
    val padding = if (compact) PaddingValues(horizontal = MaterialTheme.spacing.space4) else ButtonDefaults.ContentPadding
    val loadingText = if (loading) stringResource(Res.string.common_loading) else null
    val label = @Composable {
        Row(
            modifier = Modifier.semantics { if (loadingText != null) stateDescription = loadingText },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space2),
        ) {
            if (loading) CircularProgressIndicator(
                modifier = Modifier.size(layout.iconSmall),
                strokeWidth = layout.borderThick,
                color = LocalContentColor.current,
            )
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
    val click: () -> Unit = if (loading) ({}) else onClick
    when (variant) {
        AppButtonVariant.FILLED -> Button(
            onClick = click,
            modifier = sizedModifier,
            interactionSource = source,
            enabled = enabled,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            contentPadding = padding,
            content = { label() },
        )
        AppButtonVariant.TONAL -> FilledTonalButton(
            onClick = click,
            modifier = sizedModifier,
            interactionSource = source,
            enabled = enabled,
            shape = CircleShape,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            contentPadding = padding,
            content = { label() },
        )
        AppButtonVariant.OUTLINED -> OutlinedButton(
            onClick = click,
            modifier = sizedModifier,
            interactionSource = source,
            enabled = enabled,
            shape = CircleShape,
            border = BorderStroke(MaterialTheme.layout.borderThin, MaterialTheme.colorScheme.outline),
            contentPadding = padding,
            content = { label() },
        )
        AppButtonVariant.TEXT -> TextButton(
            onClick = click,
            modifier = sizedModifier,
            interactionSource = source,
            enabled = enabled,
            shape = CircleShape,
            contentPadding = padding,
            content = { label() },
        )
    }
}
