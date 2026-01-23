package com.madovygabor.maturimate.core.ui.utils

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// A composable that displays a loading indicator or the main content based on the isLoading state.
@Composable
fun LoadingScreenWrapper(
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    loadingContent: @Composable () -> Unit = {
        // Default loading indicator
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
    Box(modifier = modifier.fillMaxSize()) {
        if (isLoading) {
            loadingContent()
        } else {
            content()
        }
    }
}