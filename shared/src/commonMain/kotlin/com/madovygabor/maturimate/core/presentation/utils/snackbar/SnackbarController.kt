package com.madovygabor.maturimate.core.presentation.utils.snackbar

import com.madovygabor.maturimate.core.presentation.utils.UiText
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * A central controller to trigger Snackbar events from anywhere in the app,
 * including ViewModels
 */
class SnackbarController {

    // Using SharedFlow instead of StateFlow because snackbars are "one-time events"
    private val _events = MutableSharedFlow<SnackbarEvent>()
    val events = _events.asSharedFlow()

    /**
     * Emits a new snackbar event.
     * @param message The localized [UiText] to display.
     * @param action Optional [SnackbarAction] for interaction.
     */
    suspend fun showSnackbar(
        message: UiText,
        action: SnackbarAction? = null
    ) {
        _events.emit(SnackbarEvent(message, action))
    }
}