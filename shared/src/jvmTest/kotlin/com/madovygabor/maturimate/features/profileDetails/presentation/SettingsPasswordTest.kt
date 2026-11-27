package com.madovygabor.maturimate.features.profileDetails.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.testutil.TEST_TIMEOUT
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.settings_password_done

@OptIn(ExperimentalTestApi::class)
class SettingsPasswordTest : SettingsTestBase() {

    @Test
    fun freshSessionChangesPasswordInOnePageAndEndsWithSnackbar() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val account = FakeAccount()
        val snackbar = SnackbarController()
        setContent { AppTheme { SettingsScreen(viewModel = viewModel(account, snackbar = snackbar)) } }

        waitForText("Jelszó megváltoztatása")
        onNodeWithText("Jelszó megváltoztatása").performClick()
        waitForText("Jelenlegi jelszó")
        onNodeWithText("Mentés").assertIsNotEnabled()
        onNodeWithText("Jelenlegi jelszó").performTextInput("Old12345")
        onNodeWithText("Új jelszó").performTextInput("New12345")
        onNodeWithText("Új jelszó újra").performTextInput("New12399")
        onNodeWithText("A két új jelszó nem egyezik.").assertExists()
        onNodeWithText("Mentés").assertIsNotEnabled()
        onNodeWithText("New12399").performTextClearance()
        onNodeWithText("Új jelszó újra").performTextInput("New12345")
        onNodeWithText("Mentés").assertIsEnabled().performClick()

        waitUntil(timeoutMillis = TEST_TIMEOUT.inWholeMilliseconds) { account.changed != null }
        assertEquals(Triple("Old12345", null, "New12345"), account.changed)
        assertEquals(0, account.codesSent)
        assertEquals(said(Res.string.settings_password_done), runBlocking { snackbar.next() })
        waitUntil(timeoutMillis = TEST_TIMEOUT.inWholeMilliseconds) { onAllNodesWithText("Új jelszó").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun reauthenticationCodeStepAppearsOnlyWhenServerRequiresIt() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val account = FakeAccount().apply { changeResult = OperationResult.Error(AuthError.REAUTHENTICATION_REQUIRED) }
        val snackbar = SnackbarController()
        setContent { AppTheme { SettingsScreen(viewModel = viewModel(account, snackbar = snackbar)) } }

        fillThePasswordForm()
        waitForContentDescription("6 jegyű kód")
        assertEquals(1, account.codesSent)

        account.changeResult = OperationResult.Success(Unit)
        onNodeWithContentDescription("6 jegyű kód").performTextInput("123456")
        onNodeWithText("Megerősítés").assertIsEnabled().performClick()

        waitUntil(timeoutMillis = TEST_TIMEOUT.inWholeMilliseconds) { account.changed?.second == "123456" }
        assertEquals(said(Res.string.settings_password_done), runBlocking { snackbar.next() })
    }

    @Test
    fun aWrongCodeIsSaidAtTheCode() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val account = FakeAccount().apply { changeResult = OperationResult.Error(AuthError.REAUTHENTICATION_REQUIRED) }
        setContent { AppTheme { SettingsScreen(viewModel = viewModel(account)) } }

        fillThePasswordForm()
        waitForContentDescription("6 jegyű kód")
        account.changeResult = OperationResult.Error(AuthError.INVALID_CODE)
        onNodeWithContentDescription("6 jegyű kód").performTextInput("000000")
        onNodeWithText("Megerősítés").performClick()

        waitForText("Hibás vagy lejárt kód. Ellenőrizd, vagy kérj újat.")
        onAllNodesWithContentDescription("6 jegyű kód").assertCountEquals(1)
    }

    @Test
    fun aWrongCurrentPasswordIsSaidAtItsField() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val account = FakeAccount().apply { changeResult = OperationResult.Error(AuthError.INVALID_CREDENTIALS) }
        setContent { AppTheme { SettingsScreen(viewModel = viewModel(account)) } }

        fillThePasswordForm()

        waitForText("A jelszó nem jó.")
        onAllNodesWithText("Hibás e-mail cím vagy jelszó.").assertCountEquals(0)
        onNodeWithText("Jelenlegi jelszó").assertExists()
    }

    @Test
    fun tooManyTriesAreSaidInTheirOwnWords() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val account = FakeAccount().apply { changeResult = OperationResult.Error(AuthError.TOO_MANY_REQUESTS) }
        setContent { AppTheme { SettingsScreen(viewModel = viewModel(account)) } }

        fillThePasswordForm()

        waitForText("Túl sok próbálkozás. Várj egy kicsit, és próbáld újra.")
    }

    private fun ComposeUiTest.fillThePasswordForm() {
        waitForText("Jelszó megváltoztatása")
        onNodeWithText("Jelszó megváltoztatása").performClick()
        waitForText("Jelenlegi jelszó")
        onNodeWithText("Jelenlegi jelszó").performTextInput("Old12345")
        onNodeWithText("Új jelszó").performTextInput("New12345")
        onNodeWithText("Új jelszó újra").performTextInput("New12345")
        onNodeWithText("Mentés").performClick()
    }

    @Test
    fun theKeyboardActionKeySubmitsThePasswordForm() = runDesktopComposeUiTest(412, 1200) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        var saved: Triple<String, String, String?>? = null
        setContent {
            AppTheme {
                SettingsPasswordPage(
                    email = "a@b.hu",
                    pageState = PasswordPageState(),
                    busy = false,
                    onClearError = {},
                    onSendCode = {},
                    onBack = {},
                    onSave = { old, new, code -> saved = Triple(old, new, code) },
                )
            }
        }

        onNodeWithText("Jelenlegi jelszó").performTextInput("Old12345")
        onNodeWithText("Új jelszó").performTextInput("New12345")
        onNodeWithText("Új jelszó újra").performTextInput("New12345")
        onNodeWithText("Új jelszó újra").performImeAction()
        assertEquals(Triple("Old12345", "New12345", null), saved)
    }

    @Test
    fun rendersReauthCodeScreen() {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val dir = File("build/screenshots/settings").apply { mkdirs() }
        for (dark in listOf(false, true)) {
            runDesktopComposeUiTest(412, 915) {
                setContent {
                    AppTheme(darkTheme = dark) {
                        Surface(color = MaterialTheme.colorScheme.background) {
                            SettingsPasswordPage(
                                email = "pelda@email.hu",
                                pageState = PasswordPageState(step = PasswordStep.CODE, codeSent = true),
                                busy = false,
                                onClearError = {},
                                onSendCode = {},
                                onBack = {},
                                onSave = { _, _, _ -> },
                            )
                        }
                    }
                }
                onNodeWithText("Biztonsági megerősítés").assertExists()
                ImageIO.write(
                    onRoot().captureToImage().toAwtImage(),
                    "png",
                    File(dir, "password-reauth-hu-${if (dark) "dark" else "light"}.png"),
                )
            }
        }
    }

    @Test
    fun backFromCodeStepReturnsToFormAndResendClearsCode() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val account = FakeAccount().apply { changeResult = OperationResult.Error(AuthError.REAUTHENTICATION_REQUIRED) }
        val snackbar = SnackbarController()
        setContent { AppTheme { SettingsScreen(viewModel = viewModel(account, snackbar = snackbar)) } }

        fillThePasswordForm()
        waitForContentDescription("6 jegyű kód")
        onNodeWithContentDescription("6 jegyű kód").performTextInput("1234")
        onNodeWithText("Új kódot kérek").performClick()
        waitUntil(timeoutMillis = TEST_TIMEOUT.inWholeMilliseconds) { account.codesSent == 2 }
        onNodeWithText("Megerősítés").assertIsNotEnabled()

        onNodeWithContentDescription("Vissza").performClick()
        waitForText("Jelenlegi jelszó")
        onNodeWithText("Új jelszó").assertExists()
    }

    @Test
    fun codeFieldImeDoneSubmitsAndWrongCurrentPasswordKeepsSnackbarQuiet() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val account = FakeAccount().apply { changeResult = OperationResult.Error(AuthError.INVALID_CREDENTIALS) }
        val snackbar = SnackbarController()
        setContent { AppTheme { SettingsScreen(viewModel = viewModel(account, snackbar = snackbar)) } }

        fillThePasswordForm()
        waitForText("A jelszó nem jó.")
        kotlin.test.assertTrue(runBlocking { snackbar.isQuiet() })

        account.changeResult = OperationResult.Error(AuthError.REAUTHENTICATION_REQUIRED)
        onNodeWithText("Mentés").performClick()
        waitForContentDescription("6 jegyű kód")
        account.changeResult = OperationResult.Success(Unit)
        onNodeWithContentDescription("6 jegyű kód").performTextInput("123456")
        onNodeWithContentDescription("6 jegyű kód").performImeAction()
        assertEquals(said(Res.string.settings_password_done), runBlocking { snackbar.next() })
    }
}
