package com.madovygabor.maturimate.core.presentation.utils.snackbar

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject

/**
 * A root-level wrapper that provides a global [Scaffold] with [SnackbarHost] support.
 * It listens to the [SnackbarController] and manages the visibility of snackbars.
 */
@Composable
fun MainScaffoldWrapper(content: @Composable (PaddingValues) -> Unit) {
    val snackbarController = koinInject<SnackbarController>()
    val snackbarHostState = remember { SnackbarHostState() }

    // Collect snackbar events throughout the app's lifecycle
    LaunchedEffect(Unit) {
        snackbarController.events.collect { event ->
            // Use the suspend version of asString for non-composable context
            val result = snackbarHostState.showSnackbar(
                message = event.message.asStringSuspend(),
                actionLabel = event.action?.name,
                duration = SnackbarDuration.Short
            )
            // Handle action click
            if (result == SnackbarResult.ActionPerformed) {
                event.action?.action?.invoke()
            }
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                // Adjusting bottom padding to ensure it doesn't overlap with bottom bars
                modifier = Modifier.padding(bottom = 75.dp),
            ) },
        content = content
    )
}