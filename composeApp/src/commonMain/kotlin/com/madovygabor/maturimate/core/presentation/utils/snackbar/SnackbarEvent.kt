package com.madovygabor.maturimate.core.presentation.utils.snackbar

import com.madovygabor.maturimate.core.presentation.utils.UiText

/**
 * Represents an action button within a Snackbar.
 * @param name The clickable text label for the action.
 * @param action The callback to execute when the action is clicked.
 */
data class SnackbarAction(
    val name: String,
    val action: () -> Unit
)
/**
 * Data model for a single Snackbar event.
 * @param message The localized content of the snackbar.
 * @param action Optional action button configuration.
 */
data class SnackbarEvent(
    val message: UiText,
    val action: SnackbarAction? = null
)