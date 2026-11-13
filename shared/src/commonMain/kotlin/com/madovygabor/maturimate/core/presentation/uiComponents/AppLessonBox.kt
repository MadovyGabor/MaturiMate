package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.madovygabor.maturimate.core.presentation.theme.extendedColors
import com.madovygabor.maturimate.core.presentation.theme.extendedTypography
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.utils.AppRichText

/** The kinds of box in a lesson page (`:::example`, `:::tip`, `:::note`, `:::warning`, `:::quote`). */
enum class LessonBoxType { EXAMPLE, TIP, NOTE, WARNING, QUOTE }

/**
 * A box in a lesson page: a label and a text (docs/design-system/components/LessonBox.md).
 *
 * @param label The name of the box kind, an app string ("Példa", "Tipp").
 * @param text The text of the content; paragraphs are separated by a blank line.
 */
@Composable
fun AppLessonBox(
    type: LessonBoxType,
    label: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    val container: Color
    val content: Color
    val icon: ImageVector?
    when (type) {
        LessonBoxType.EXAMPLE -> { container = colors.tertiaryContainer; content = colors.onTertiaryContainer; icon = null }
        LessonBoxType.TIP -> { container = colors.surfaceContainerLow; content = colors.onBackground; icon = Icons.Rounded.Lightbulb }
        LessonBoxType.NOTE -> { container = colors.secondaryContainer; content = colors.onSecondaryContainer; icon = Icons.Rounded.Info }
        LessonBoxType.WARNING -> { container = colors.errorContainer; content = colors.onErrorContainer; icon = Icons.Rounded.Warning }
        LessonBoxType.QUOTE -> { container = colors.surfaceContainerLow; content = colors.onBackground; icon = Icons.Rounded.FormatQuote }
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = content,
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            // a tip has a gold bar at its side; a quote has a secondary accent bar
            val barColor = when (type) {
                LessonBoxType.TIP -> MaterialTheme.extendedColors.brandGold
                LessonBoxType.QUOTE -> colors.secondary
                else -> null
            }
            if (barColor != null) {
                Box(Modifier.width(MaterialTheme.layout.accentBar).fillMaxHeight().background(barColor))
            }
            Column(
                modifier = Modifier.padding(horizontal = spacing.space3, vertical = spacing.space3),
                verticalArrangement = Arrangement.spacedBy(spacing.space2),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.space1)) {
                    if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(MaterialTheme.layout.iconSmall))
                    Text(label.uppercase(), style = MaterialTheme.typography.labelSmall)
                }
                AppRichText(text, MaterialTheme.extendedTypography.reading)
            }
        }
    }
}
