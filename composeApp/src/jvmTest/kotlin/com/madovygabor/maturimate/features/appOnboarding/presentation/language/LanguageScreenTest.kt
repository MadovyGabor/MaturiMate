package com.madovygabor.maturimate.features.appOnboarding.presentation.language

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.features.appOnboarding.presentation.QuestionUiState
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import kotlinx.coroutines.runBlocking
import maturimate.composeapp.generated.resources.Res
import maturimate.composeapp.generated.resources.common_back
import maturimate.composeapp.generated.resources.common_next
import maturimate.composeapp.generated.resources.language_hu_desc
import maturimate.composeapp.generated.resources.language_sk_desc
import maturimate.composeapp.generated.resources.language_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class LanguageScreenTest {

    private val defaultLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private fun text(key: StringResource) = runBlocking { getString(key) }

    @Test
    fun nextIsDisabledUntilALanguageIsPicked() = runComposeUiTest {
        var state by mutableStateOf(QuestionUiState<TeachingLanguage>())
        var next = 0
        setContent {
            AppTheme(darkTheme = false) {
                LanguageContent(state, role = null, onSelected = { state = state.copy(selected = it) }, onBack = {}, onNext = { next++ })
            }
        }

        onNodeWithText(text(Res.string.common_next)).assertIsNotEnabled()
        onNodeWithText(text(Res.string.language_sk_desc)).performClick()
        onNodeWithText(text(Res.string.common_next)).assertIsEnabled().performClick()

        assertEquals(TeachingLanguage.SK, state.selected)
        assertEquals(1, next)
    }

    @Test
    fun savedLanguageIsSelected() = runComposeUiTest {
        setContent {
            AppTheme(darkTheme = false) {
                LanguageContent(QuestionUiState(selected = TeachingLanguage.HU), role = null, onSelected = {}, onBack = {}, onNext = {})
            }
        }

        onNodeWithText(text(Res.string.language_hu_desc)).assertIsSelected()
        onNodeWithText(text(Res.string.language_sk_desc)).assertIsNotSelected()
        onNodeWithText(text(Res.string.common_next)).assertIsEnabled()
    }

    @Test
    fun backGoesBack() = runComposeUiTest {
        var back = 0
        setContent {
            AppTheme(darkTheme = false) {
                LanguageContent(QuestionUiState(), role = null, onSelected = {}, onBack = { back++ }, onNext = {})
            }
        }

        onNodeWithContentDescription(text(Res.string.common_back)).performClick()

        assertEquals(1, back)
    }

    @Test
    fun nextIsDisabledWhileSaving() = runComposeUiTest {
        setContent {
            AppTheme(darkTheme = false) {
                LanguageContent(QuestionUiState(TeachingLanguage.SK, saving = true), role = null, onSelected = {}, onBack = {}, onNext = {})
            }
        }

        onNodeWithText(text(Res.string.common_next)).assertIsNotEnabled()
    }

    /** Pictures at phone, tablet and desktop size, both themes and languages, into build/screenshots/language. */
    @Test
    fun rendersAtEverySizeAndTheme() {
        val sizes = mapOf("phone" to (412 to 915), "tablet" to (800 to 1100), "desktop" to (1280 to 800))
        val dir = File("build/screenshots/language").apply { mkdirs() }
        for (language in listOf("hu", "sk")) {
            Locale.setDefault(Locale.forLanguageTag(language))
            for ((name, size) in sizes) for (dark in listOf(false, true)) {
                runDesktopComposeUiTest(size.first, size.second) {
                    setContent {
                        AppTheme(darkTheme = dark) {
                            LanguageContent(QuestionUiState(selected = TeachingLanguage.HU), role = null, onSelected = {}, onBack = {}, onNext = {})
                        }
                    }
                    onNodeWithText(text(Res.string.language_title)).assertExists()
                    val file = File(dir, "language-$language-$name-${if (dark) "dark" else "light"}.png")
                    ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
                }
            }
        }
    }
}
