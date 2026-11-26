package com.madovygabor.maturimate.core.presentation.locale

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The app language sets the platform locale, which Compose resources read their texts in. */
@OptIn(ExperimentalTestApi::class)
class LocalAppLocaleTest {

    private val defaultLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    @Test
    fun theAppLanguageBecomesThePlatformLocale() = runDesktopComposeUiTest {
        var seen = ""
        setContent {
            CompositionLocalProvider(LocalAppLocale provides "hu") { seen = LocalAppLocale.current }
        }
        waitForIdle()

        assertEquals("hu", seen)
        assertEquals("hu", Locale.getDefault().language)
    }

    @Test
    fun noAppLanguageKeepsThePhonesLanguage() = runDesktopComposeUiTest {
        val phone = Locale.getDefault()
        setContent {
            CompositionLocalProvider(LocalAppLocale provides null) {}
        }
        waitForIdle()

        assertEquals(phone, Locale.getDefault())
    }

    @Test
    fun applyAppLocaleImmediatelyUpdatesPlatformLocaleAndDesktopRebuildsTree() {
        assertTrue(LocalAppLocale.rebuildsTreeOnChange)
        applyAppLocaleImmediately("hu")
        assertEquals("hu", Locale.getDefault().language)
    }
}
