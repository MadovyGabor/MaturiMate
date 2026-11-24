package com.madovygabor.maturimate.features.profileDetails.presentation

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
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
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.settings_password_done

@OptIn(ExperimentalTestApi::class)
class SettingsPasswordTest : SettingsTestBase() {

    @Test
    fun thePasswordChangeGoesInStepsAndEndsWithASnackbar() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val account = FakeAccount()
        val snackbar = SnackbarController()
        setContent { AppTheme { SettingsScreen(viewModel = viewModel(account, snackbar = snackbar)) } }

        waitForText("Jelszó megváltoztatása")
        onNodeWithText("Jelszó megváltoztatása").performClick()
        // step 1: the code goes to the e-mail address
        onNodeWithText("Kód küldése").performClick()
        waitForText("6 jegyű kód")
        assertEquals(1, account.codesSent)
        // step 2: the code, the current password and the new one twice; Save waits for all of them
        onNodeWithText("Mentés").assertIsNotEnabled()
        onNodeWithText("6 jegyű kód").performTextInput("123456")
        onNodeWithText("Jelenlegi jelszó").performTextInput("Old12345")
        onNodeWithText("Új jelszó").performTextInput("New12345")
        onNodeWithText("Új jelszó újra").performTextInput("New12399")
        onNodeWithText("A két új jelszó nem egyezik.").assertExists()
        onNodeWithText("Mentés").assertIsNotEnabled()
        onNodeWithText("New12399").performTextClearance()
        onNodeWithText("Új jelszó újra").performTextInput("New12345")
        onNodeWithText("Mentés").assertIsEnabled().performClick()

        waitUntil(timeoutMillis = TEST_TIMEOUT.inWholeMilliseconds) { account.changed != null }
        assertEquals(Triple("Old12345", "123456", "New12345"), account.changed)
        assertEquals(said(Res.string.settings_password_done), runBlocking { snackbar.next() })
        waitUntil(timeoutMillis = TEST_TIMEOUT.inWholeMilliseconds) { onAllNodesWithText("Új jelszó").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun aWrongCodeIsSaidAtTheCode() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val account = FakeAccount().apply { changeResult = OperationResult.Error(AuthError.INVALID_CODE) }
        setContent { AppTheme { SettingsScreen(viewModel = viewModel(account)) } }

        fillThePasswordDialog()

        waitForText("Hibás vagy lejárt kód. Ellenőrizd, vagy kérj újat.")
        onAllNodesWithText("6 jegyű kód").assertCountEquals(1)
    }

    // Card 11: a wrong current password is said at its own field, in words of the settings (not "wrong e-mail or password").
    @Test
    fun aWrongCurrentPasswordIsSaidAtItsField() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val account = FakeAccount().apply { changeResult = OperationResult.Error(AuthError.INVALID_CREDENTIALS) }
        setContent { AppTheme { SettingsScreen(viewModel = viewModel(account)) } }

        fillThePasswordDialog()

        waitForText("A jelszó nem jó.")
        onAllNodesWithText("Hibás e-mail cím vagy jelszó.").assertCountEquals(0)
        // the dialog stays, with what was typed
        onNodeWithText("Jelenlegi jelszó").assertExists()
    }

    @Test
    fun tooManyTriesAreSaidInTheirOwnWords() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val account = FakeAccount().apply { changeResult = OperationResult.Error(AuthError.TOO_MANY_REQUESTS) }
        setContent { AppTheme { SettingsScreen(viewModel = viewModel(account)) } }

        fillThePasswordDialog()

        waitForText("Túl sok próbálkozás. Várj egy kicsit, és próbáld újra.")
    }

    private fun ComposeUiTest.fillThePasswordDialog() {
        waitForText("Jelszó megváltoztatása")
        onNodeWithText("Jelszó megváltoztatása").performClick()
        onNodeWithText("Kód küldése").performClick()
        waitForText("6 jegyű kód")
        onNodeWithText("6 jegyű kód").performTextInput("000000")
        onNodeWithText("Jelenlegi jelszó").performTextInput("Old12345")
        onNodeWithText("Új jelszó").performTextInput("New12345")
        onNodeWithText("Új jelszó újra").performTextInput("New12345")
        onNodeWithText("Mentés").performClick()
    }

    @Test
    fun theKeyboardActionKeyMovesOnAndSubmitsThePasswordSteps() = runDesktopComposeUiTest(412, 1200) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        var saved: Triple<String, String, String>? = null
        setContent {
            AppTheme {
                PasswordDialog("a@b.hu", PasswordStep.FORM, busy = false, error = null, onSendCode = {},
                    onClearError = {}, onSave = { old, c, n -> saved = Triple(old, c, n) }, onDismiss = {})
            }
        }

        onNodeWithText("6 jegyű kód").performTextInput("123456")
        onNodeWithText("Jelenlegi jelszó").performTextInput("Old12345")
        onNodeWithText("Új jelszó").performTextInput("New12345")
        onNodeWithText("Új jelszó újra").performTextInput("New12345")
        onNodeWithText("Új jelszó újra").performImeAction()
        assertEquals(Triple("Old12345", "123456", "New12345"), saved)
    }
}
