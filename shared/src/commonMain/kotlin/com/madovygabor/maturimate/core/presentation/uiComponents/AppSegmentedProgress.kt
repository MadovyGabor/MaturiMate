package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing

/**
 * A progress bar of separate segments, one per page of a lesson (docs/design-system/components/SegmentedProgress.md).
 * The segments up to and including [current] are filled.
 *
 * @param current Index of the page being read, from 0.
 * @param description What a screen reader says, for example "page 2 of 3".
 */
@Composable
fun AppSegmentedProgress(
    total: Int,
    current: Int,
    description: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier.fillMaxWidth().semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space1),
    ) {
        repeat(total) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(MaterialTheme.layout.progressBarHeight)
                    .clip(CircleShape)
                    .background(if (index <= current) colors.primary else colors.surfaceContainerHighest)
            )
        }
    }
}
