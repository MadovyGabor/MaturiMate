package com.madovygabor.maturimate.features.profileDetails.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.features.auth.domain.AccountUser
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.domain.DeletionProof
import com.madovygabor.maturimate.testutil.awaitCondition
import com.madovygabor.maturimate.testutil.awaitFirst
import com.madovygabor.maturimate.testutil.awaitWithin
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalTestApi::class)
class SettingsDeletionTest : SettingsTestBase() {

    @Test
    fun aFailedDeletionShowsTheErrorAndKeepsTheDialog() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val account = FakeAccount().apply { deleteResult = OperationResult.Error(AuthError.NETWORK) }
        setContent { AppTheme { SettingsScreen(viewModel = viewModel(account)) } }

        waitForText("A fiók törlése")
        onNodeWithText("A fiók törlése").performClick()
        onNodeWithText("Törlöd a fiókodat?").assertExists()
        // the typed word and the password guard the button: nothing happens before both are there
        onNodeWithText("Végleges törlés").assertIsNotEnabled()
        onNodeWithText("A megerősítéshez írd be: TÖRLÉS.").performTextInput("töRLés ")
        onNodeWithText("Végleges törlés").assertIsNotEnabled()
        onNodeWithText("A fiókod jelszava").performTextInput("Old12345")
        onNodeWithText("Végleges törlés").assertIsEnabled().performClick()

        waitForText("Nincs internetkapcsolat. Próbáld újra, ha újra van net.")
        onNodeWithText("Törlöd a fiókodat?").assertExists()
        assertEquals(1, account.deletes)
        assertEquals("Old12345", (account.proofs.single() as DeletionProof.Password).password)
    }

    // Card 11: the deletion needs the proof that the user is the owner: a wrong password is said at its field.
    @Test
    fun aWrongPasswordIsSaidAtTheDeletionsPasswordField() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val account = FakeAccount().apply { deleteResult = OperationResult.Error(AuthError.INVALID_CREDENTIALS) }
        setContent { AppTheme { SettingsScreen(viewModel = viewModel(account)) } }

        waitForText("A fiók törlése")
        onNodeWithText("A fiók törlése").performClick()
        onNodeWithText("A megerősítéshez írd be: TÖRLÉS.").performTextInput("TÖRLÉS")
        onNodeWithText("A fiókod jelszava").performTextInput("Wrong123")
        onNodeWithText("Végleges törlés").performClick()

        waitForText("A jelszó nem jó.")
        onNodeWithText("Törlöd a fiókodat?").assertExists()
    }

    // Card 11: an account made with Google has no password; its proof is a fresh Google sign-in of the same account.
    @Test
    fun aGoogleAccountsDeletionAsksForAFreshGoogleSignIn() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val loaded = runBlocking { settings(student(repository())).loaded() }
        var google = 0
        val state = loaded.copy(account = AccountUser("id", "anna@example.com", "Anna", "Kovács", hasPassword = false), guestPhone = false)
        setContent {
            AppTheme { SettingsContent(state, SettingsActions(onDeleteWithGoogle = { google++ }), SettingsDialogState(SettingsDialog.DELETE)) }
        }

        onNodeWithText("A Google-lel létrehozott fiók törléséhez jelentkezz be újra Google-lel, ugyanazzal a fiókkal.").assertExists()
        onAllNodesWithText("A fiókod jelszava").assertCountEquals(0)
        onNodeWithText("Bejelentkezés és törlés").assertIsNotEnabled()
        onNodeWithText("A megerősítéshez írd be: TÖRLÉS.").performTextInput("TÖRLÉS")
        onNodeWithText("Bejelentkezés és törlés").assertIsEnabled().performClick()
        assertEquals(1, google)
    }

    @Test
    fun aFreshGoogleSignInOfTheSameAccountDeletesIt() = runBlocking<Unit> {
        val account = FakeAccount()
        val viewModel = viewModel(account, user = AccountUser("account-y", "bea@example.com", "Bea", "Nagy", hasPassword = false))
        viewModel.loaded()
        viewModel.openDialog(SettingsDialog.DELETE)

        val opened = CompletableDeferred<Unit>()
        viewModel.startGoogleDeletion { opened.complete(Unit) }
        // Google opens only after the session is remembered (and the sync held)
        opened.awaitWithin()
        assertEquals(1, account.proofsStarted)
        // the Google flow is open: the dialog waits
        assertTrue(viewModel.busy.value)
        viewModel.onGoogleDeletionSignedIn()

        viewModel.dialogs.awaitFirst { it.dialog == null }
        assertEquals(listOf<DeletionProof>(DeletionProof.FreshSignIn), account.proofs)
        viewModel.busy.awaitFirst { !it }
    }

    @Test
    fun aGoogleSignInOfAnotherAccountDeletesNothing() = runBlocking<Unit> {
        val account = FakeAccount().apply { freshSignIn = OperationResult.Error(AuthError.WRONG_ACCOUNT) }
        val viewModel = viewModel(account, user = AccountUser("account-y", "bea@example.com", "Bea", "Nagy", hasPassword = false))
        viewModel.loaded()
        viewModel.openDialog(SettingsDialog.DELETE)

        viewModel.startGoogleDeletion {}
        awaitCondition { account.proofsStarted > 0 }
        viewModel.onGoogleDeletionSignedIn()

        val dialog = viewModel.dialogs.awaitFirst { it.error != null }
        assertEquals(SettingsDialogState(SettingsDialog.DELETE, AuthError.WRONG_ACCOUNT), dialog)
        assertEquals(0, account.deletes)
        viewModel.busy.awaitFirst { !it }

        // S2-1: a closed or failed Google flow frees the dialog again and ends the proof (the sync goes on)
        viewModel.startGoogleDeletion {}
        viewModel.onGoogleDeletionClosed()
        assertFalse(viewModel.busy.value)
        viewModel.startGoogleDeletion {}
        viewModel.onGoogleDeletionFailed(AuthError.NETWORK)
        assertEquals(AuthError.NETWORK, viewModel.dialogs.value.error)
        assertFalse(viewModel.busy.value)
        awaitCondition { account.cancels >= 2 }
        assertEquals(0, account.deletes)
    }

    @Test
    fun aProofThatCannotStartNeverOpensGoogle() = runBlocking<Unit> {
        val account = FakeAccount().apply { startResult = OperationResult.Error(AuthError.NETWORK) }
        val viewModel = viewModel(account, user = AccountUser("account-y", "bea@example.com", "Bea", "Nagy", hasPassword = false))
        viewModel.loaded()
        viewModel.openDialog(SettingsDialog.DELETE)
        var opened = false

        viewModel.startGoogleDeletion { opened = true }

        assertEquals(AuthError.NETWORK, viewModel.dialogs.awaitFirst { it.error != null }.error)
        viewModel.busy.awaitFirst { !it }
        assertFalse(opened)
    }

    // S2-1: the settings never show another account's session on this account's phone
    @Test
    fun aForeignSessionIsEndedBeforeTheSettingsShowTheAccount() = runBlocking<Unit> {
        val account = FakeAccount()
        viewModel(account).loaded()
        assertTrue(account.foreignChecks > 0)
    }

    // The sign-out and the deletion left their dialog open (and its button busy) until the app was rebuilt.
    @Test
    fun theDialogClosesWhenTheSignOutOrTheDeletionWentThrough() = runBlocking {
        val account = FakeAccount()
        val viewModel = viewModel(account)
        viewModel.loaded()

        viewModel.openDialog(SettingsDialog.SIGN_OUT)
        viewModel.signOut()
        viewModel.dialogs.awaitFirst { it.dialog == null }

        viewModel.openDialog(SettingsDialog.SIGN_OUT_UNSYNCED)
        viewModel.signOut(force = true)
        viewModel.dialogs.awaitFirst { it.dialog == null }

        viewModel.openDialog(SettingsDialog.DELETE)
        viewModel.deleteAccount("Old12345")
        viewModel.dialogs.awaitFirst { it.dialog == null }
        assertEquals(1, account.deletes)
    }

    @Test
    fun theTypedWordMustMatchBeforeTheDeleteButtonWorks() = runDesktopComposeUiTest(412, 1000) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        var deleted = 0
        setContent {
            AppTheme { ConfirmDialog("Cím", "Szöveg", "Töröl", busy = false, onConfirm = { deleted++ }, onDismiss = {}, word = "TÖRLÉS", wordLabel = "Írd be") }
        }

        onNodeWithText("Töröl").assertIsNotEnabled()
        onNodeWithText("Írd be").performTextInput("TÖRL")
        onNodeWithText("Töröl").assertIsNotEnabled()
        onNodeWithText("TÖRL").performTextClearance()
        onNodeWithText("Írd be").performTextInput("  törlés ")
        onNodeWithText("Töröl").assertIsEnabled().performClick()
        assertEquals(1, deleted)
    }

    @Test
    fun aBusyConfirmDialogSaysSoAndWaits() = runDesktopComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        setContent { AppTheme { ConfirmDialog("Cím", "Szöveg", "Töröl", busy = true, onConfirm = {}, onDismiss = {}) } }

        onNodeWithText("Betöltés…").assertIsNotEnabled()
        onAllNodesWithText("Töröl").assertCountEquals(0)
    }
}
