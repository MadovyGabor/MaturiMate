package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.madovygabor.maturimate.core.presentation.theme.extendedColors
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing

/** Progress of a subject or of one of its parts, ready to draw: the share done and its two lines of counts. */
data class ProgressLine(
    val title: String,
    /** From 0 to 1. */
    val fraction: Float,
    /** The share as text, for example "48%". */
    val percent: String,
    /** For example "Leckék 8 / 16" and "Tesztek 2 / 5". */
    val details: List<String>,
)

/**
 * The progress of one subject (docs/design-system/components/SubjectProgressCard.md): the name, the share done
 * with a gold bar, and the counts under it. A tap opens the big parts of the subject, each in a small card of its
 * own; another tap closes them. Without [subject] progress (no content yet) the card shows [comingSoon] instead.
 */
@Composable
fun AppSubjectProgressCard(
    subject: ProgressLine,
    parts: List<ProgressLine>,
    expanded: Boolean,
    onToggle: () -> Unit,
    openLabel: String,
    closeLabel: String,
    modifier: Modifier = Modifier,
    comingSoon: String? = null,
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    val canOpen = comingSoon == null && parts.isNotEmpty()
    Surface(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerLow,
        border = BorderStroke(MaterialTheme.layout.borderThin, colors.outlineVariant),
    ) {
        Column {
            val header = if (canOpen) {
                Modifier
                    .clickable(role = Role.Button, onClickLabel = if (expanded) closeLabel else openLabel, onClick = onToggle)
                    .semantics { stateDescription = if (expanded) closeLabel else openLabel }
            } else Modifier
            Column(header.padding(spacing.space4), verticalArrangement = Arrangement.spacedBy(spacing.space3)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.space2)) {
                    Text(subject.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (comingSoon != null) {
                        AppTag(comingSoon)
                    } else {
                        Text(subject.percent, style = MaterialTheme.typography.titleLarge)
                        if (canOpen) {
                            val turn by animateFloatAsState(if (expanded) 180f else 0f)
                            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.rotate(turn))
                        }
                    }
                }
                if (comingSoon == null) {
                    AppProgressBar(subject.fraction, color = MaterialTheme.extendedColors.brandGold)
                    Details(subject.details)
                }
            }
            AnimatedVisibility(expanded && canOpen) {
                Column {
                    HorizontalDivider(color = colors.outlineVariant)
                    Column(Modifier.padding(spacing.space3), verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
                        parts.forEach { part -> PartCard(part) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PartCard(part: ProgressLine) {
    val spacing = MaterialTheme.spacing
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(horizontal = spacing.space4, vertical = spacing.space3), verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(part.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(part.percent, style = MaterialTheme.typography.titleSmall)
            }
            AppProgressBar(part.fraction, color = MaterialTheme.extendedColors.brandGold)
            Details(part.details)
        }
    }
}

@Composable
private fun Details(details: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space3)) {
        details.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
