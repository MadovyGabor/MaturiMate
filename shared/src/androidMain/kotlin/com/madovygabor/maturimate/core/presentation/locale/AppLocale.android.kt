package com.madovygabor.maturimate.core.presentation.locale

import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import java.util.Locale

@Volatile
private var activeAppLocale: String? = null

/**
 * The app language is applied in-process via [LocaleList.setDefault] + [LocalConfiguration]
 * override, without Activity recreation or `AppCompatDelegate.setApplicationLocales`.
 * [MainActivity] calls [reapplyAppLocale] on configuration changes and resume so the system
 * cannot reset the process default locale behind our back.
 */
actual object LocalAppLocale {
    actual val rebuildsTreeOnChange: Boolean = false

    actual val current: String
        @Composable get() = LocalConfiguration.current.locales[0]?.toLanguageTag() ?: Locale.getDefault().toLanguageTag()

    @Composable
    actual infix fun provides(value: String?): ProvidedValue<*> {
        val base = LocalConfiguration.current
        if (value == null) {
            if (activeAppLocale != null) {
                activeAppLocale = null
                if (!base.locales.isEmpty) {
                    LocaleList.setDefault(base.locales)
                    Locale.setDefault(base.locales[0])
                }
            }
            return LocalConfiguration provides base
        }
        activeAppLocale = value
        val locale = Locale.forLanguageTag(value)
        applyProcessLocale(locale)
        val overridden = remember(base, value) {
            Configuration(base).apply {
                setLocales(LocaleList(locale))
                setLayoutDirection(locale)
            }
        }
        return LocalConfiguration provides overridden
    }
}

actual fun applyAppLocaleImmediately(languageTag: String) {
    activeAppLocale = languageTag
    applyProcessLocale(Locale.forLanguageTag(languageTag))
}

@Composable
actual fun KeepLocaleWhile(hold: Boolean, content: @Composable () -> Unit) {
    val current = LocalConfiguration.current
    var held by remember { mutableStateOf(current) }
    if (!hold) held = current
    CompositionLocalProvider(LocalConfiguration provides held, content = content)
}

/** Reapplies the active in-app locale after system configuration changes or returning to foreground. */
fun reapplyAppLocale() {
    activeAppLocale?.let { applyProcessLocale(Locale.forLanguageTag(it)) }
}

private fun applyProcessLocale(locale: Locale) {
    if (Locale.getDefault().language != locale.language) {
        LocaleList.setDefault(LocaleList(locale))
        Locale.setDefault(locale)
    }
}
