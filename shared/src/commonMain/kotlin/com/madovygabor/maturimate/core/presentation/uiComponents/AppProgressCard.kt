package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.madovygabor.maturimate.core.presentation.theme.extendedColors
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing

/** The progress shown at the bottom of an [AppProgressCard]: the bar and its value as text ("2 / 8"). */
data class CardProgress(val fraction: Float, val label: String)

/**
 * A big card of a list the student picks from: a subject, a part, a topic (docs/design-system/components/ProgressCard.md).
 * It shows the name, an optional line about it, what is in it, and how far the student has got.
 *
 * @param subtitle A line under the name, for example what the part is about.
 * @param meta What is in it, for example "2 téma · 8 lépés".
 * @param progress The gold bar with its value as text; null when there is nothing to show yet.
 * @param tag A small label instead of the arrow, for example "Hamarosan"; the card then cannot be opened.
 * @param compact A smaller card for the topics inside a part: less padding and a smaller name.
 * @param onClick Opens it; null for a card that cannot be opened.
 */
@Composable
fun AppProgressCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    meta: String? = null,
    progress: CardProgress? = null,
    tag: String? = null,
    onClick: (() -> Unit)? = null,
    compact: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    val layout = MaterialTheme.layout
    val body = @Composable {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(min = if (compact) layout.answerOptionMinHeight else layout.choiceCardMinHeight).padding(if (compact) spacing.space3 else spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.space2),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.space3)) {
                Text(title, modifier = Modifier.weight(1f), style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall)
                if (tag != null) AppTag(tag) else if (onClick != null) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
            }
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            if (meta != null) Text(meta, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            if (progress != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.space3)) {
                    // the gold bar always has its value as text beside it (design system)
                    Column(Modifier.weight(1f)) { AppProgressBar(progress.fraction, color = MaterialTheme.extendedColors.brandGold) }
                    Text(progress.label, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                }
            }
        }
    }
    val border = BorderStroke(layout.borderThin, colors.outlineVariant)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = colors.surfaceContainerLow,
            contentColor = colors.onBackground,
            border = border,
        ) { body() }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = colors.surfaceContainerLow,
            contentColor = colors.onSurfaceVariant,
            border = border,
        ) { body() }
    }
}
