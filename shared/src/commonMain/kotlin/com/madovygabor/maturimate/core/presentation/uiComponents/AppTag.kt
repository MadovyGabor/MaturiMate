package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.madovygabor.maturimate.core.presentation.theme.extendedColors
import com.madovygabor.maturimate.core.presentation.theme.spacing

/** The look of an [AppTag]: a quiet label, a highlighted one, a done one. */
enum class TagStyle { NEUTRAL, HIGHLIGHT, SUCCESS }

/**
 * A small label such as "Hamarosan", "Következő", "Kész", "1 szó" (docs/design-system/components/Tag.md).
 * The text says what it means; the color only supports it.
 */
@Composable
fun AppTag(
    text: String,
    modifier: Modifier = Modifier,
    style: TagStyle = TagStyle.NEUTRAL,
) {
    val colors = MaterialTheme.colorScheme
    val extended = MaterialTheme.extendedColors
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = when (style) {
            TagStyle.NEUTRAL -> colors.tertiaryContainer
            TagStyle.HIGHLIGHT -> colors.primaryContainer
            TagStyle.SUCCESS -> extended.successContainer
        },
        contentColor = when (style) {
            TagStyle.NEUTRAL -> colors.onTertiaryContainer
            TagStyle.HIGHLIGHT -> colors.onPrimaryContainer
            TagStyle.SUCCESS -> extended.onSuccessContainer
        },
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.space2, vertical = MaterialTheme.spacing.space1 / 2),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}
