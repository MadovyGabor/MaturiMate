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
    fun aFailedDeletionShowsTheErrorAndKeepsThePage() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val account = FakeAccount().apply { deleteResult = OperationResult.Error(AuthError.NETWORK) }
        setContent { AppTheme { SettingsScreen(viewModel = viewModel(account)) } }

        waitForText("A fiók törlése")
        onNodeWithText("A fiók törlése").performClick()
        waitForText("A megerősítéshez írd be: TÖRLÉS.")
        onNodeWithText("Végleges törlés").assertIsNotEnabled()
        onNodeWithText("A megerősítéshez írd be: TÖRLÉS.").performTextInput("töRLés ")
        onNodeWithText("Végleges törlés").assertIsNotEnabled()
        onNodeWithText("A fiókod jelszava").performTextInput("Old12345")
        onNodeWithText("Végleges törlés").assertIsEnabled().performClick()

        waitForText("Nincs internetkapcsolat. Próbáld újra, ha újra van net.")
        onNodeWithText("A fiók törlése").assertExists()
        assertEquals(1, account.deletes)
        assertEquals("Old12345", (account.proofs.single() as DeletionProof.Password).password)
    }

    @Test
    fun aWrongPasswordIsSaidAtTheDeletionsPasswordField() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val account = FakeAccount().apply { deleteResult = OperationResult.Error(AuthError.INVALID_CREDENTIALS) }
        setContent { AppTheme { SettingsScreen(viewModel = viewModel(account)) } }

        waitForText("A fiók törlése")
        onNodeWithText("A fiók törlése").performClick()
        waitForText("A megerősítéshez írd be: TÖRLÉS.")
        onNodeWithText("A megerősítéshez írd be: TÖRLÉS.").performTextInput("TÖRLÉS")
        onNodeWithText("A fiókod jelszava").performTextInput("Wrong123")
        onNodeWithText("Végleges törlés").performClick()

        waitForText("A jelszó nem jó.")
        onNodeWithText("A fiók törlése").assertExists()
    }

    @Test
    fun aGoogleAccountsDeletionAsksForAFreshGoogleSignIn() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        var google = 0
        setContent {
            AppTheme {
                SettingsDeletePage(
                    withPassword = false,
                    busy = false,
                    error = null,
                    onClearError = {},
                    onDeleteWithPassword = {},
                    onDeleteWithGoogle = { google++ },
                    onBack = {},
                )
            }
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
        val viewModel = viewModel(account, user = com.madovygabor.maturimate.features.auth.domain.AccountUser("account-y", "bea@example.com", "Bea", "Nagy", hasPassword = false))
        viewModel.loaded()

        val opened = CompletableDeferred<Unit>()
        viewModel.startGoogleDeletion { opened.complete(Unit) }
        opened.awaitWithin()
        assertEquals(1, account.proofsStarted)
        assertTrue(viewModel.busy.value)
        viewModel.onGoogleDeletionSignedIn()

        awaitCondition { account.deletes == 1 }
        assertEquals(listOf<DeletionProof>(DeletionProof.FreshSignIn), account.proofs)
        viewModel.busy.awaitFirst { !it }
    }

    @Test
    fun aGoogleSignInOfAnotherAccountDeletesNothing() = runBlocking<Unit> {
        val account = FakeAccount().apply { freshSignIn = OperationResult.Error(AuthError.WRONG_ACCOUNT) }
        val viewModel = viewModel(account, user = com.madovygabor.maturimate.features.auth.domain.AccountUser("account-y", "bea@example.com", "Bea", "Nagy", hasPassword = false))
        viewModel.loaded()

        viewModel.startGoogleDeletion {}
        awaitCondition { account.proofsStarted > 0 }
        viewModel.onGoogleDeletionSignedIn()

        assertEquals(AuthError.WRONG_ACCOUNT, viewModel.pageError.awaitFirst { it != null })
        assertEquals(0, account.deletes)
        viewModel.busy.awaitFirst { !it }

        viewModel.startGoogleDeletion {}
        viewModel.onGoogleDeletionClosed()
        assertFalse(viewModel.busy.value)
        viewModel.startGoogleDeletion {}
        viewModel.onGoogleDeletionFailed(AuthError.NETWORK)
        assertEquals(AuthError.NETWORK, viewModel.pageError.value)
        assertFalse(viewModel.busy.value)
        awaitCondition { account.cancels >= 2 }
        assertEquals(0, account.deletes)
    }

    @Test
    fun aProofThatCannotStartNeverOpensGoogle() = runBlocking<Unit> {
        val account = FakeAccount().apply { startResult = OperationResult.Error(AuthError.NETWORK) }
        val viewModel = viewModel(account, user = com.madovygabor.maturimate.features.auth.domain.AccountUser("account-y", "bea@example.com", "Bea", "Nagy", hasPassword = false))
        viewModel.loaded()
        var opened = false

        viewModel.startGoogleDeletion { opened = true }

        assertEquals(AuthError.NETWORK, viewModel.pageError.awaitFirst { it != null })
        viewModel.busy.awaitFirst { !it }
        assertFalse(opened)
    }

    @Test
    fun aForeignSessionIsEndedBeforeTheSettingsShowTheAccount() = runBlocking<Unit> {
        val account = FakeAccount()
        viewModel(account).loaded()
        assertTrue(account.foreignChecks > 0)
    }

    @Test
    fun theDialogClosesWhenTheSignOutWentThrough() = runBlocking<Unit> {
        val account = FakeAccount()
        val viewModel = viewModel(account)
        viewModel.loaded()

        viewModel.openDialog(SettingsDialog.SIGN_OUT)
        viewModel.signOut()
        viewModel.dialogs.awaitFirst { it.dialog == null }

        viewModel.openDialog(SettingsDialog.SIGN_OUT_UNSYNCED)
        viewModel.signOut(force = true)
        viewModel.dialogs.awaitFirst { it.dialog == null }
    }

    @Test
    fun theTypedWordMustMatchBeforeTheDeleteButtonWorks() = runDesktopComposeUiTest(412, 1200) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        var deleted = 0
        setContent {
            AppTheme {
                SettingsDeletePage(
                    withPassword = true,
                    busy = false,
                    error = null,
                    onClearError = {},
                    onDeleteWithPassword = { deleted++ },
                    onDeleteWithGoogle = {},
                    onBack = {},
                )
            }
        }

        onNodeWithText("Végleges törlés").assertIsNotEnabled()
        onNodeWithText("A megerősítéshez írd be: TÖRLÉS.").performTextInput("TÖRL")
        onNodeWithText("Végleges törlés").assertIsNotEnabled()
        onNodeWithText("TÖRL").performTextClearance()
        onNodeWithText("A megerősítéshez írd be: TÖRLÉS.").performTextInput("  törlés ")
        onNodeWithText("A fiókod jelszava").performTextInput("Old12345")
        onNodeWithText("Végleges törlés").assertIsEnabled().performClick()
        assertEquals(1, deleted)
    }

    @Test
    fun aBusyConfirmDialogWaitsAndDoesNotConfirmTwice() = runDesktopComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        var confirmed = 0
        setContent { AppTheme { ConfirmDialog("Cím", "Szöveg", "Töröl", busy = true, onConfirm = { confirmed++ }, onDismiss = {}) } }

        onNodeWithText("Töröl").assertIsNotEnabled().performClick()
        assertEquals(0, confirmed)
    }
}
