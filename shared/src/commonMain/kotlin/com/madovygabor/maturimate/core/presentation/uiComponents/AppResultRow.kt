package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import com.madovygabor.maturimate.core.presentation.theme.extendedColors
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.utils.richText

/**
 * One task in the result of a round (docs/design-system/components/ResultRow.md): a check or an X and the question.
 * A wrong task opens on a tap and shows [lines]: the right answer, why not the chosen option, the explanation.
 *
 * @param title The question as plain text.
 * @param lines What opens under a wrong task; empty for a right one, which does not open.
 */
@Composable
fun AppResultRow(
    correct: Boolean,
    title: String,
    lines: List<FeedbackLine>,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    val layout = MaterialTheme.layout
    var open by remember { mutableStateOf(false) }
    val mark = if (correct) MaterialTheme.extendedColors.success else colors.error
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceContainerLow,
        contentColor = colors.onBackground,
        border = BorderStroke(layout.borderThin, colors.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .clickable(enabled = lines.isNotEmpty(), role = Role.Button) { open = !open }
                .heightIn(min = layout.touchTarget)
                .padding(spacing.space3),
            verticalArrangement = Arrangement.spacedBy(spacing.space2),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.space3)) {
                Icon(
                    if (correct) Icons.Rounded.Check else Icons.Rounded.Close,
                    contentDescription = null,
                    tint = mark,
                    modifier = Modifier.size(layout.icon),
                )
                Text(
                    title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = if (open) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (lines.isNotEmpty()) {
                    Icon(
                        if (open) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(layout.icon),
                    )
                }
            }
            if (open) {
                lines.forEach { line ->
                    Text(
                        buildAnnotatedString {
                            if (line.label != null) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(line.label + " ") }
                            append(richText(line.text))
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
