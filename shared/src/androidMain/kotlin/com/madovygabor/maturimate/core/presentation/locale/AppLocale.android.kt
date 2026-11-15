package com.madovygabor.maturimate.core.presentation.locale

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * The app language is the system's per-app language ([AppCompatDelegate.setApplicationLocales]): the platform keeps it
 * across configuration changes and restarts, and the activity is rebuilt when it changes. Nothing sets the JVM default
 * locale while composing, so a configuration change cannot bring back the phone's language.
 */
actual object LocalAppLocale {
    private val LocalLocaleTag = staticCompositionLocalOf { Locale.getDefault().toLanguageTag() }

    actual val current: String
        @Composable get() = LocalLocaleTag.current

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        // after the composition, not in it; null keeps the language the app already has
        SideEffect { if (value != null) applyAppLanguage(value) }
        return LocalLocaleTag provides (value ?: Locale.getDefault().toLanguageTag())
    }
}

/** Sets the per-app language; does nothing when it is already that one (a change rebuilds the activity). */
internal fun applyAppLanguage(languageTag: String) {
    val wanted = LocaleListCompat.forLanguageTags(languageTag)
    if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != wanted.toLanguageTags()) {
        AppCompatDelegate.setApplicationLocales(wanted)
    }
}
