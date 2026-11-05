package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing

/**
 * One number of an [AppStatCard]: a value made of numbers and units ("12ó 40p"), a label under it, and an icon.
 * [parts] alternate a number and its unit; the units are drawn smaller. [description] is read out instead.
 */
data class StatItem(
    val icon: ImageVector,
    val parts: List<String>,
    val label: String,
    val description: String,
)

/**
 * A few numbers side by side in one card, divided by thin lines (docs/design-system/components/StatCard.md),
 * for example the streak, the study time and the solved tasks of the profile.
 */
@Composable
fun AppStatCard(items: List<StatItem>, modifier: Modifier = Modifier) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = colors.surfaceContainerHigh) {
        Row(Modifier.height(IntrinsicSize.Min).padding(vertical = spacing.space3)) {
            items.forEachIndexed { index, item ->
                if (index > 0) VerticalDivider(Modifier.fillMaxHeight(), color = colors.outlineVariant)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = spacing.space2)
                        .clearAndSetSemantics { contentDescription = item.description },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(spacing.space1),
                ) {
                    Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
                        Icon(item.icon, contentDescription = null, modifier = Modifier.padding(spacing.space2).size(MaterialTheme.layout.icon))
                    }
                    Text(value(item.parts, colors.onSurfaceVariant), style = MaterialTheme.typography.headlineSmall, maxLines = 1, textAlign = TextAlign.Center)
                    Text(item.label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** The numbers in the value style, the units after them smaller and quieter. */
@Composable
private fun value(parts: List<String>, unitColor: androidx.compose.ui.graphics.Color): AnnotatedString {
    val unitStyle = MaterialTheme.typography.labelLarge
    return buildAnnotatedString {
        parts.forEachIndexed { index, part ->
            if (index % 2 == 0) {
                if (index > 0) append(' ')
                append(part)
            } else {
                withStyle(SpanStyle(fontSize = unitStyle.fontSize, fontWeight = unitStyle.fontWeight, color = unitColor)) { append(part) }
            }
        }
    }
}
