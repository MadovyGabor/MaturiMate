package com.madovygabor.maturimate.core.presentation.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue

/**
 * The language the app's texts are shown in, set from inside the app (the teaching language).
 *
 * Compose resources pick their language from the platform locale, so changing it means
 * setting the platform locale and, on platforms where Compose does not recompose resources
 * on a locale change (`rebuildsTreeOnChange`), building the UI again (`key`). Null keeps
 * the phone's own language.
 */
expect object LocalAppLocale {
    /** Whether changing the app language requires rebuilding the navigation tree via `key(...)`. */
    val rebuildsTreeOnChange: Boolean

    val current: String @Composable get

    @Composable
    infix fun provides(value: String?): ProvidedValue<*>
}

/**
 * Sets the process default locale immediately before navigating to a new screen, so the
 * destination already composes in [languageTag] on its very first frame.
 */
expect fun applyAppLocaleImmediately(languageTag: String)

/**
 * Keeps the locale seen by [content] frozen while [hold] is true, so an exiting screen does
 * not recompose its texts into the newly chosen language mid-transition.
 */
@Composable
expect fun KeepLocaleWhile(hold: Boolean, content: @Composable () -> Unit)
