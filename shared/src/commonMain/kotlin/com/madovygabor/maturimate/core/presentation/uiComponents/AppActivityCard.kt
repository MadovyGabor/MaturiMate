package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing

/** The icon of an [AppActivityCard]: a lesson, a test of the path, or a practice round. */
enum class ActivityIcon { LESSON, TEST, PRACTICE }

/**
 * A finished lesson, test or practice round in a list (docs/design-system/components/ActivityCard.md): an icon (a
 * book in a circle for a lesson, a question mark in a rounded square for a test, a light bulb in a circle for
 * practice), the title, a line under it, and the result as a tag; [kind] ("Lecke", "Teszt", "Gyakorlás") is a second
 * tag above the result when the list mixes them.
 */
@Composable
fun AppActivityCard(
    title: String,
    meta: String,
    icon: ActivityIcon,
    result: String,
    resultStyle: TagStyle,
    modifier: Modifier = Modifier,
    kind: String? = null,
) {
    val spacing = MaterialTheme.spacing
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceContainerLow,
        border = BorderStroke(MaterialTheme.layout.borderThin, colors.outlineVariant),
    ) {
        Row(
            Modifier.heightIn(min = MaterialTheme.layout.choiceCardMinHeight).padding(horizontal = spacing.space3, vertical = spacing.space2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.space3),
        ) {
            Surface(shape = if (icon == ActivityIcon.TEST) MaterialTheme.shapes.small else MaterialTheme.shapes.extraLarge, color = colors.surfaceContainerHighest) {
                Icon(
                    when (icon) {
                        ActivityIcon.LESSON -> Icons.Rounded.MenuBook
                        ActivityIcon.TEST -> Icons.Rounded.Quiz
                        ActivityIcon.PRACTICE -> Icons.Rounded.Lightbulb
                    },
                    contentDescription = null,
                    modifier = Modifier.padding(spacing.space2).size(MaterialTheme.layout.iconSmall),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(meta, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(spacing.space1)) {
                kind?.let { AppTag(it) }
                AppTag(result, style = resultStyle)
            }
        }
    }
}
