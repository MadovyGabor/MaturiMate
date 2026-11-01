package com.madovygabor.maturimate.features.appOnboarding.presentation

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import kotlinx.coroutines.runBlocking
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.welcome_have_account
import maturimate.shared.generated.resources.welcome_start
import maturimate.shared.generated.resources.welcome_tagline
import org.jetbrains.compose.resources.getString
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class WelcomeScreenTest {

    private val defaultLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private fun text(key: org.jetbrains.compose.resources.StringResource) = runBlocking { getString(key) }

    @Test
    fun startButtonStartsTheOnboarding() = runComposeUiTest {
        var started = 0
        setContent { AppTheme(darkTheme = false) { WelcomeScreen(onStart = { started++ }, onHaveAccount = null) } }

        onNodeWithText(text(Res.string.welcome_start)).performClick()

        assertEquals(1, started)
    }

    @Test
    fun signInButtonIsHiddenUntilThereIsASignIn() = runComposeUiTest {
        setContent { AppTheme(darkTheme = false) { WelcomeScreen(onStart = {}, onHaveAccount = null) } }

        onNodeWithText(text(Res.string.welcome_have_account)).assertDoesNotExist()
    }

    @Test
    fun signInButtonOpensTheSignIn() = runComposeUiTest {
        var opened = 0
        setContent { AppTheme(darkTheme = false) { WelcomeScreen(onStart = {}, onHaveAccount = { opened++ }) } }

        onNodeWithText(text(Res.string.welcome_have_account)).performClick()

        assertEquals(1, opened)
    }

    @Test
    fun hungarianPhoneGetsHungarianOtherPhonesGetSlovak() {
        Locale.setDefault(Locale.forLanguageTag("hu-HU"))
        assertEquals("Kezdjük", text(Res.string.welcome_start))
        Locale.setDefault(Locale.forLanguageTag("de-DE"))
        assertEquals("Začnime", text(Res.string.welcome_start))
        Locale.setDefault(Locale.forLanguageTag("sk-SK"))
        assertEquals("Začnime", text(Res.string.welcome_start))
    }

    /**
     * Renders the screen at phone, tablet and desktop size in both themes and in both
     * languages into build/screenshots/welcome, for a look by eye.
     */
    @Test
    fun rendersAtEverySizeAndTheme() {
        val sizes = mapOf("phone" to (412 to 915), "tablet" to (800 to 1100), "desktop" to (1280 to 800))
        val dir = File("build/screenshots/welcome").apply { mkdirs() }
        for (language in listOf("hu", "sk")) {
            Locale.setDefault(Locale.forLanguageTag(language))
            for ((name, size) in sizes) for (dark in listOf(false, true)) {
                runDesktopComposeUiTest(size.first, size.second) {
                    setContent { AppTheme(darkTheme = dark) { WelcomeScreen(onStart = {}, onHaveAccount = {}) } }
                    onNodeWithText(text(Res.string.welcome_tagline)).assertIsDisplayed()
                    val image = onRoot().captureToImage().toAwtImage()
                    val file = File(dir, "welcome-$language-$name-${if (dark) "dark" else "light"}.png")
                    ImageIO.write(image, "png", file)
                    assertTrue(file.length() > 0)
                }
            }
        }
    }
}
