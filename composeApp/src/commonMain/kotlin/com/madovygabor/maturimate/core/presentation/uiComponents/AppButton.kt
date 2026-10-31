package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.madovygabor.maturimate.core.presentation.theme.layout

/** The variants of [AppButton] built so far; tonal and outlined come when a screen needs them. */
enum class AppButtonVariant {
    /** The screen's one main action. */
    FILLED,

    /** A quiet action, for example "Már van fiókom". */
    TEXT,
}

/**
 * Pill-shaped button of the design system (docs/design-system/components/Button.md).
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
) {
    val sizedModifier = modifier.heightIn(min = MaterialTheme.layout.touchTarget)
    val label = @Composable { Text(text, style = MaterialTheme.typography.labelLarge) }
    when (variant) {
        AppButtonVariant.FILLED -> Button(
            onClick = onClick,
            modifier = sizedModifier,
            enabled = enabled,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            content = { label() },
        )
        AppButtonVariant.TEXT -> TextButton(
            onClick = onClick,
            modifier = sizedModifier,
            enabled = enabled,
            shape = CircleShape,
            content = { label() },
        )
    }
}
