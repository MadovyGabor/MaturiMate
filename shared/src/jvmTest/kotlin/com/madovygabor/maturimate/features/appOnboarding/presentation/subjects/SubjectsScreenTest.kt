package com.madovygabor.maturimate.features.appOnboarding.presentation.subjects

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.features.userProfile.domain.CatalogSubject
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.common_next
import maturimate.shared.generated.resources.level_unknown
import maturimate.shared.generated.resources.subject_anj
import maturimate.shared.generated.resources.subject_mat
import maturimate.shared.generated.resources.subjects_level_unknown_hint
import maturimate.shared.generated.resources.subjects_title_student
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class SubjectsScreenTest {

    private val defaultLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private fun text(key: StringResource) = runBlocking { getString(key) }

    @Test
    fun levelChoiceAppearsWithEnglishAndUnlocksNext() = runComposeUiTest {
        var state by mutableStateOf(
            SubjectsUiState(
                role = UserRole.LEARNER,
                items = (CatalogSubject.forLanguage(TeachingLanguage.HU) - CatalogSubject.ENGLISH_B2).map {
                    SubjectItem(it, false, false, asksLevel = it.level != null)
                },
            )
        )
        setContent {
            AppTheme(darkTheme = false) {
                SubjectsContent(
                    state = state,
                    onToggled = { subject ->
                        state = state.copy(items = state.items.map { if (it.subject == subject) it.copy(selected = !it.selected) else it })
                    },
                    onLevelSelected = { state = state.copy(level = it) },
                    onBack = {}, onNext = {},
                )
            }
        }

        onNodeWithText(text(Res.string.common_next)).assertIsNotEnabled()
        onNodeWithText(text(Res.string.subject_anj)).performClick()
        onNodeWithText(text(Res.string.common_next)).assertIsNotEnabled()
        onNodeWithText(text(Res.string.level_unknown)).performClick()
        onNodeWithText(text(Res.string.subjects_level_unknown_hint)).assertExists()
        onNodeWithText(text(Res.string.common_next)).assertIsEnabled()
        onNodeWithText(text(Res.string.subject_mat)).performClick()
        onNodeWithText(text(Res.string.common_next)).assertIsEnabled()
    }

    /** Pictures at phone, tablet and desktop size, both themes and languages, into build/screenshots/subjects. */
    @Test
    fun rendersAtEverySizeAndTheme() {
        val sizes = mapOf("phone" to (412 to 915), "tablet" to (800 to 1100), "desktop" to (1280 to 800))
        val dir = File("build/screenshots/subjects").apply { mkdirs() }
        val state = SubjectsUiState(
            role = UserRole.STUDENT,
            items = (CatalogSubject.forLanguage(TeachingLanguage.HU) - CatalogSubject.ENGLISH_B2).map {
                SubjectItem(it, it.compulsory, it.compulsory, asksLevel = it.level != null)
            },
            level = LevelChoice.UNKNOWN,
        )
        for (language in listOf("hu", "sk")) {
            Locale.setDefault(Locale.forLanguageTag(language))
            for ((name, size) in sizes) for (dark in listOf(false, true)) {
                runDesktopComposeUiTest(size.first, size.second) {
                    setContent {
                        AppTheme(darkTheme = dark) { SubjectsContent(state, {}, {}, {}, {}) }
                    }
                    onNodeWithText(text(Res.string.subjects_title_student)).assertExists()
                    val file = File(dir, "subjects-$language-$name-${if (dark) "dark" else "light"}.png")
                    ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
                }
            }
        }
    }
}
