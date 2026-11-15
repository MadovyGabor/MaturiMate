package com.madovygabor.maturimate.core.presentation.utils.snackbar

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject

/**
 * The height of the bottom navigation bar that the snackbar has to clear. The main frame sets it while its bar is on
 * screen; it is zero everywhere else (the onboarding, a lesson, the wide layout with a side rail).
 */
@Stable
class BottomBarHeight {
    var value: Dp by mutableStateOf(0.dp)
}

/** The [BottomBarHeight] of the app: provided by [MainScaffoldWrapper], set by the main frame. */
val LocalBottomBarHeight = compositionLocalOf { BottomBarHeight() }

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

    val bottomBar = remember { BottomBarHeight() }
    CompositionLocalProvider(LocalBottomBarHeight provides bottomBar) {
        Scaffold(
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    // above the bottom bar of the main frame, if it is on screen
                    modifier = Modifier.padding(bottom = bottomBar.value),
                )
            },
            content = content
        )
    }
}