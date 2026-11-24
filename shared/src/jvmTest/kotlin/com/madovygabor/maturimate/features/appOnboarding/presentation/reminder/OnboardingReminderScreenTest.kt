package com.madovygabor.maturimate.features.appOnboarding.presentation.reminder

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toAwtImage
import com.madovygabor.maturimate.core.presentation.NotificationPermissionResult
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.common_cancel
import maturimate.shared.generated.resources.reminder_not_now
import maturimate.shared.generated.resources.reminder_open_settings
import maturimate.shared.generated.resources.reminder_permission_blocked
import maturimate.shared.generated.resources.reminder_permission_denied
import maturimate.shared.generated.resources.reminder_permission_hint
import maturimate.shared.generated.resources.reminder_title
import maturimate.shared.generated.resources.reminder_turn_on
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class OnboardingReminderScreenTest {

    private val defaultLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private fun text(key: StringResource) = runBlocking { getString(key) }

    @Test
    fun theHintTellsDeniedFromBlockedAndOnlyBlockedOffersTheSettings() = runComposeUiTest {
        var state by mutableStateOf(ReminderUiState(permission = NotificationPermissionResult.DENIED))
        var opened = 0
        setContent {
            AppTheme(darkTheme = false) {
                ReminderContent(state, UserRole.STUDENT, showPermissionHint = true, {}, {}, {}, {}, onOpenSettings = { opened++ })
            }
        }

        onNodeWithText(text(Res.string.reminder_permission_denied)).assertExists()
        onNodeWithText(text(Res.string.reminder_permission_hint)).assertDoesNotExist()
        onNodeWithText(text(Res.string.reminder_open_settings)).assertDoesNotExist()

        state = ReminderUiState(permission = NotificationPermissionResult.BLOCKED)
        onNodeWithText(text(Res.string.reminder_permission_blocked)).assertExists()
        onNodeWithText(text(Res.string.reminder_permission_denied)).assertDoesNotExist()
        onNodeWithText(text(Res.string.reminder_open_settings)).performClick()
        assertEquals(1, opened)
    }

    @Test
    fun buttonsAnswerAndTheTimeOpensThePicker() = runComposeUiTest {
        var turnedOn = 0
        var notNow = 0
        setContent {
            AppTheme(darkTheme = false) {
                ReminderContent(ReminderUiState(), UserRole.STUDENT, showPermissionHint = true, {}, { turnedOn++ }, { notNow++ }, {})
            }
        }

        onNodeWithText(text(Res.string.reminder_permission_hint)).assertExists()
        onNodeWithText(text(Res.string.reminder_turn_on)).performClick()
        onNodeWithText(text(Res.string.reminder_not_now)).performClick()
        assertEquals(1, turnedOn)
        assertEquals(1, notNow)

        onNodeWithText("18:00").performClick()
        onNodeWithText(text(Res.string.common_cancel)).assertExists()
    }

    /** Pictures at phone, tablet and desktop size, both themes and languages, into build/screenshots/reminder. */
    @Test
    fun rendersAtEverySizeAndTheme() {
        val sizes = mapOf("phone" to (412 to 915), "tablet" to (800 to 1100), "desktop" to (1280 to 800))
        val dir = File("build/screenshots/reminder").apply { mkdirs() }
        for (language in listOf("hu", "sk")) {
            Locale.setDefault(Locale.forLanguageTag(language))
            for ((name, size) in sizes) for (dark in listOf(false, true)) {
                runDesktopComposeUiTest(size.first, size.second) {
                    setContent { AppTheme(darkTheme = dark) { ReminderContent(ReminderUiState(), UserRole.STUDENT, true, {}, {}, {}, {}) } }
                    onNodeWithText(text(Res.string.reminder_title)).assertExists()
                    ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(dir, "reminder-$language-$name-${if (dark) "dark" else "light"}.png"))
                }
            }
        }
    }
}
