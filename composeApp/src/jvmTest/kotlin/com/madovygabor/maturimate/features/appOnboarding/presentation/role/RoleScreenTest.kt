package com.madovygabor.maturimate.features.appOnboarding.presentation.role

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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.features.appOnboarding.presentation.QuestionUiState
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import kotlinx.coroutines.runBlocking
import maturimate.composeapp.generated.resources.Res
import maturimate.composeapp.generated.resources.common_next
import maturimate.composeapp.generated.resources.onboarding_step
import maturimate.composeapp.generated.resources.role_learner
import maturimate.composeapp.generated.resources.role_student
import maturimate.composeapp.generated.resources.role_teacher
import maturimate.composeapp.generated.resources.role_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class RoleScreenTest {

    private val defaultLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private fun text(key: StringResource) = runBlocking { getString(key) }
    private fun stepLabel(number: Int, total: Int) = runBlocking { getString(Res.string.onboarding_step, number, total) }.uppercase()

    @Test
    fun nextIsDisabledUntilARoleIsPicked() = runComposeUiTest {
        var state by mutableStateOf(QuestionUiState<UserRole>())
        var next = 0
        setContent {
            AppTheme(darkTheme = false) {
                RoleContent(state, onSelected = { state = state.copy(selected = it) }, onBack = {}, onNext = { next++ })
            }
        }

        onNodeWithText(text(Res.string.common_next)).assertIsNotEnabled()
        onNodeWithText(text(Res.string.role_learner)).performClick()
        onNodeWithText(text(Res.string.common_next)).assertIsEnabled().performClick()

        assertEquals(UserRole.LEARNER, state.selected)
        assertEquals(1, next)
    }

    @Test
    fun savedRoleIsSelected() = runComposeUiTest {
        setContent {
            AppTheme(darkTheme = false) {
                RoleContent(QuestionUiState(selected = UserRole.TEACHER), onSelected = {}, onBack = {}, onNext = {})
            }
        }

        onNodeWithText(text(Res.string.role_teacher)).assertIsSelected()
        onNodeWithText(text(Res.string.role_student)).assertIsNotSelected()
    }

    @Test
    fun stepCountFollowsThePickedRole() = runComposeUiTest {
        var state by mutableStateOf(QuestionUiState<UserRole>())
        setContent {
            AppTheme(darkTheme = false) {
                RoleContent(state, onSelected = { state = state.copy(selected = it) }, onBack = {}, onNext = {})
            }
        }

        onNodeWithText(stepLabel(2, 8)).assertExists()
        onNodeWithText(text(Res.string.role_learner)).performClick()
        onNodeWithText(stepLabel(2, 7)).assertExists()
        onNodeWithText(text(Res.string.role_teacher)).performClick()
        onNodeWithText(stepLabel(2, 6)).assertExists()
    }

    /** Pictures at phone, tablet and desktop size, both themes and languages, into build/screenshots/role. */
    @Test
    fun rendersAtEverySizeAndTheme() {
        val sizes = mapOf("phone" to (412 to 915), "tablet" to (800 to 1100), "desktop" to (1280 to 800))
        val dir = File("build/screenshots/role").apply { mkdirs() }
        for (language in listOf("hu", "sk")) {
            Locale.setDefault(Locale.forLanguageTag(language))
            for ((name, size) in sizes) for (dark in listOf(false, true)) {
                runDesktopComposeUiTest(size.first, size.second) {
                    setContent {
                        AppTheme(darkTheme = dark) {
                            RoleContent(QuestionUiState(selected = UserRole.STUDENT), onSelected = {}, onBack = {}, onNext = {})
                        }
                    }
                    onNodeWithText(text(Res.string.role_title)).assertExists()
                    val file = File(dir, "role-$language-$name-${if (dark) "dark" else "light"}.png")
                    ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
                }
            }
        }
    }
}
