package com.madovygabor.maturimate.features.profileDetails.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.features.auth.domain.AccountUser
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.domain.ExportSaver
import com.madovygabor.maturimate.features.auth.presentation.toUiText
import com.madovygabor.maturimate.testutil.awaitFirst
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.settings_export_done
import maturimate.shared.generated.resources.settings_export_shared

@OptIn(ExperimentalTestApi::class)
class SettingsExportTest : SettingsTestBase() {

    @Test
    fun theExportSaysSavedWithThePlaceNothingOnCancelAndAnErrorOnFailure() = runBlocking {
        val account = FakeAccount()
        val snackbar = SnackbarController()
        val viewModel = viewModel(account, snackbar = snackbar)
        viewModel.loaded()

        account.exportResult = OperationResult.Success("C:\\Users\\anna\\maturimate.json")
        viewModel.exportData()
        assertEquals(said(Res.string.settings_export_done, "C:\\Users\\anna\\maturimate.json"), snackbar.next())
        assertEquals(false, account.exportedAsShare)

        // the picker was closed: nothing to show
        account.exportResult = OperationResult.Success(ExportSaver.CANCELLED)
        viewModel.exportData()
        viewModel.busy.awaitFirst { !it }
        assertTrue(snackbar.isQuiet())

        account.exportResult = OperationResult.Error(AuthError.UNKNOWN)
        viewModel.exportData()
        assertEquals(AuthError.UNKNOWN.toUiText(), snackbar.next())
    }

    @Test
    fun sharingSaysSharedNotSaved() = runBlocking {
        val account = FakeAccount().apply { canShare = true }
        val snackbar = SnackbarController()
        val viewModel = viewModel(account, snackbar = snackbar)
        assertTrue(viewModel.loaded().canShareExport)

        viewModel.exportData(share = true)
        assertEquals(said(Res.string.settings_export_shared), snackbar.next())
        assertEquals(true, account.exportedAsShare)
    }

    @Test
    fun theExportOffersSharingOnlyWhereItWorks() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val loaded = runBlocking { settings(student(repository())).loaded() }
        val account = AccountUser("id", "anna@example.com", "Anna", "Kovács")
        var shared = 0
        var exports = mutableListOf<Boolean>()
        var dialogs by mutableStateOf(SettingsDialogState())
        var canShare by mutableStateOf(true)
        setContent {
            AppTheme {
                SettingsContent(
                    loaded.copy(account = account, guestPhone = false, canShareExport = canShare),
                    SettingsActions(onOpenDialog = { dialogs = SettingsDialogState(it) }, onExport = { exports += it; if (it) shared++ }),
                    dialogs,
                )
            }
        }

        onNodeWithText("Adataim letöltése").performClick()
        onNodeWithText("Mentés fájlba").assertExists()
        onNodeWithText("Megosztás").performClick()
        assertEquals(listOf(true), exports)
        assertEquals(1, shared)

        // a desktop cannot share: the row goes straight to the save dialog
        dialogs = SettingsDialogState()
        canShare = false
        onNodeWithText("Adataim letöltése").performClick()
        onAllNodesWithText("Megosztás").assertCountEquals(0)
        assertEquals(listOf(true, false), exports)
    }
}
