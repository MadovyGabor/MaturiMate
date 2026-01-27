package com.madovygabor.maturimate.core.presentation.utils

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A styled layout container that applies a surface background, rounded corners, and padding.
 * Designed to group related UI elements within a distinct visual section.
 *
 * @param modifier The modifier to be applied to the container.
 * @param verticalSpacing The vertical spacing between elements inside the container.
 * @param content The Composable content to be displayed inside.
 */
@Composable
fun SurfaceContainer(
    modifier: Modifier = Modifier,
    verticalSpacing: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing)
    ) {
        content()
    }
}