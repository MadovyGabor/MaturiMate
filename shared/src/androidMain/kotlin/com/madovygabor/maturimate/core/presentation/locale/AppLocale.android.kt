package com.madovygabor.maturimate.core.presentation.locale

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

actual object LocalAppLocale {
    // the phone's locale, kept so "no app language" can go back to it
    private val systemDefault: Locale = Locale.getDefault()

    actual val current: String
        @Composable get() = Locale.getDefault().toLanguageTag()

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val locale = value?.let { Locale.forLanguageTag(it) } ?: systemDefault
        Locale.setDefault(locale)
        val configuration = Configuration(LocalConfiguration.current).apply { setLocale(locale) }
        return LocalConfiguration provides configuration
    }
}
