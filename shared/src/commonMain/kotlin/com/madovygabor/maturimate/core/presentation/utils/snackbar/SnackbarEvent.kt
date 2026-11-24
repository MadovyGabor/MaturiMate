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
/** What a message says about what happened; it decides the look (color and icon) of the snackbar. */
enum class SnackbarKind {
    /** Plain information: the quiet dark message. */
    INFO,

    /** Something worked (saved, signed out): green with a check. */
    SUCCESS,

    /** Something failed or is missing: red with an error icon. */
    ERROR,
}

/**
 * Data model for a single Snackbar event.
 * @param message The localized content of the snackbar.
 * @param action Optional action button configuration.
 * @param kind How the snackbar looks.
 */
data class SnackbarEvent(
    val message: UiText,
    val action: SnackbarAction? = null,
    val kind: SnackbarKind = SnackbarKind.INFO,
)