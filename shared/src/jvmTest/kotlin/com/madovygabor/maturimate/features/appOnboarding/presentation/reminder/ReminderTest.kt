package com.madovygabor.maturimate.features.appOnboarding.presentation.reminder

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.userProfile.data.UserProfileRepositoryImpl
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalTime
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.common_cancel
import maturimate.shared.generated.resources.reminder_not_now
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ReminderTest {

    private val defaultLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private fun text(key: StringResource) = runBlocking { getString(key) }

    private fun repository(): UserProfileRepositoryImpl {
        val database = UserDatabase(
            JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) }
        )
        return UserProfileRepositoryImpl(database, LocalUserProvider(database), Dispatchers.Unconfined)
    }

    @Test
    fun turningOnSavesTheChosenTimeAndNotNowTurnsItOff() = runBlocking {
        val repository = repository()
        val viewModel = ReminderViewModel(repository, SnackbarController())
        viewModel.onTimeSelected(LocalTime(20, 15))

        val on = CompletableDeferred<Unit>()
        viewModel.onAnswer(true) { on.complete(Unit) }
        withTimeout(5_000) { on.await() }
        assertTrue(repository.profile.first()!!.reminderEnabled)
        assertEquals(LocalTime(20, 15), repository.profile.first()!!.reminderTime)

        val off = CompletableDeferred<Unit>()
        viewModel.onAnswer(false) { off.complete(Unit) }
        withTimeout(5_000) { off.await() }
        assertFalse(repository.profile.first()!!.reminderEnabled)
    }

    @Test
    fun savedTimeIsOfferedAgain() = runBlocking {
        val repository = repository()
        repository.saveReminder(false, LocalTime(6, 45))

        val viewModel = ReminderViewModel(repository, SnackbarController())

        assertEquals(LocalTime(6, 45), withTimeout(5_000) { viewModel.state.first { it.time != DEFAULT_REMINDER_TIME } }.time)
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
