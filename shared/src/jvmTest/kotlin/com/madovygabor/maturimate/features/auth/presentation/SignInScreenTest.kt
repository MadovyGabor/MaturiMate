package com.madovygabor.maturimate.features.auth.presentation

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.reset_save
import maturimate.shared.generated.resources.signin_why
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTestApi::class, ExperimentalTime::class)
class SignInScreenTest {

    private val defaultLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private fun text(key: StringResource) = runBlocking { getString(key) }

    /** Pictures of the sign-in and the reset, both themes and languages, into build/screenshots/signin. */
    @Test
    fun rendersAtEverySizeAndTheme() {
        val sizes = mapOf("phone" to (412 to 915), "desktop" to (1280 to 800))
        val dir = File("build/screenshots/signin").apply { mkdirs() }
        val form = SignInUiState(email = "anna@example.com", password = "secret")
        val reset = SignInUiState(phase = SignInPhase.RESET_CODE, email = "anna@example.com", code = "123")
        for (language in listOf("hu", "sk")) {
            Locale.setDefault(Locale.forLanguageTag(language))
            for ((name, size) in sizes) for (dark in listOf(false, true)) for ((step, state) in listOf("form" to form, "reset" to reset)) {
                runDesktopComposeUiTest(size.first, size.second) {
                    setContent { AppTheme(darkTheme = dark) { SignInContent(state, SignInActions()) } }
                    onNodeWithText(text(if (step == "form") Res.string.signin_why else Res.string.reset_save)).assertExists()
                    ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(dir, "signin-$step-$language-$name-${if (dark) "dark" else "light"}.png"))
                }
            }
        }
    }
}
