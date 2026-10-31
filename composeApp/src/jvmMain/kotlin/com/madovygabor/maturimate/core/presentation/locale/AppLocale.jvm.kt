package com.madovygabor.maturimate.core.presentation.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import java.util.Locale

actual object LocalAppLocale {
    private val LocalLocaleTag = staticCompositionLocalOf { Locale.getDefault().toLanguageTag() }

    // the system's locale, kept so "no app language" can go back to it
    private val systemDefault: Locale = Locale.getDefault()

    actual val current: String
        @Composable get() = LocalLocaleTag.current

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val locale = value?.let { Locale.forLanguageTag(it) } ?: systemDefault
        Locale.setDefault(locale)
        return LocalLocaleTag provides locale.toLanguageTag()
    }
}
