package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.madovygabor.maturimate.core.presentation.theme.spacing

/**
 * A small pill with an icon and one short fact, for example the streak or the days until the exam
 * (docs/design-system/components/InfoChip.md). Not clickable; for a label without an icon use [AppTag].
 *
 * @param highlighted The gold look, for a fact that is alive (a running streak); otherwise a quiet one.
 */
@Composable
fun AppInfoChip(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = if (highlighted) colors.primaryContainer else colors.tertiaryContainer,
        contentColor = if (highlighted) colors.onPrimaryContainer else colors.onTertiaryContainer,
    ) {
        Row(
            modifier = Modifier.heightIn(min = spacing.space8).padding(horizontal = spacing.space3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.space1),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(spacing.space4 + spacing.space1 / 2))
            Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
