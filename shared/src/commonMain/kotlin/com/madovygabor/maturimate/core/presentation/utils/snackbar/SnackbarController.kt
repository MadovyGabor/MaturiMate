package com.madovygabor.maturimate.core.presentation.utils.snackbar

import com.madovygabor.maturimate.core.presentation.utils.UiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * A central controller to trigger Snackbar events from anywhere in the app,
 * including ViewModels
 */
class SnackbarController {

    // A buffered channel: each event is shown once, and sending never waits for the snackbar on
    // screen to go away (a SharedFlow without a buffer kept the caller's button busy meanwhile)
    private val _events = Channel<SnackbarEvent>(Channel.BUFFERED)
    val events: Flow<SnackbarEvent> = _events.receiveAsFlow()

    /**
     * Emits a new snackbar event.
     * @param message The localized [UiText] to display.
     * @param action Optional [SnackbarAction] for interaction.
     * @param kind [SnackbarKind.SUCCESS] for something that worked, [SnackbarKind.ERROR] for a failure; the default is plain.
     */
    suspend fun showSnackbar(
        message: UiText,
        action: SnackbarAction? = null,
        kind: SnackbarKind = SnackbarKind.INFO,
    ) {
        _events.send(SnackbarEvent(message, action, kind))
    }
}