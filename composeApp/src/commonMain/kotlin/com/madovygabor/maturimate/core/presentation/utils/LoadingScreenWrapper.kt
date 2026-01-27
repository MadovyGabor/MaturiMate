package com.madovygabor.maturimate.core.presentation.utils

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * A reusable wrapper that toggles between a loading state and the actual UI content.
 * * @param isLoading Boolean flag to determine which content to display.
 * @param modifier Modifier applied to the outer container.
 * @param loadingContent Custom UI for the loading state. Defaults to a centered [CircularProgressIndicator].
 * @param content The main UI content to be displayed when loading is finished.
 */
@Composable
fun LoadingScreenWrapper(
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    loadingContent: @Composable () -> Unit = {
        // Standard Material 3 Loading Indicator
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(50.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp
            )
        }
    },
    content: @Composable () -> Unit
) {
    // The wrapper fills the available space by default
    Box(modifier = modifier.fillMaxSize()) {
        if (isLoading) {
            loadingContent()
        } else {
            content()
        }
    }
}