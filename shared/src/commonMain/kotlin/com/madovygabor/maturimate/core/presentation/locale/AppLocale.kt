package com.madovygabor.maturimate.core.presentation.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue

/**
 * The language the app's texts are shown in, set from inside the app (the teaching language).
 *
 * Compose resources pick their language from the platform locale, so changing it means
 * setting the platform locale and building the UI again (`key`), as Compose Multiplatform
 * recommends. Null keeps the phone's own language.
 */
expect object LocalAppLocale {
    val current: String @Composable get

    @Composable
    infix fun provides(value: String?): ProvidedValue<*>
}
