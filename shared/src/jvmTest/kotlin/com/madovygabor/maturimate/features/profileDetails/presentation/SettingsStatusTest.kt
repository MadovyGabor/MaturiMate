package com.madovygabor.maturimate.features.profileDetails.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.uiComponents.SettingsValueTone
import com.madovygabor.maturimate.features.content.domain.ContentError
import com.madovygabor.maturimate.features.content.domain.ContentUpdateState
import com.madovygabor.maturimate.features.content.domain.PendingPack
import com.madovygabor.maturimate.features.sync.domain.ContentRelease
import com.madovygabor.maturimate.features.sync.domain.SyncError
import com.madovygabor.maturimate.features.sync.domain.SyncState
import com.madovygabor.maturimate.testutil.awaitFirst
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

@OptIn(ExperimentalTestApi::class)
class SettingsStatusTest : SettingsTestBase() {

    @Test
    fun classifySyncMapsEveryStateCorrectly() {
        assertEquals(RowStatusKind.IDLE, classifySync(SyncState()).kind)
        assertEquals(RowStatusKind.BUSY, classifySync(SyncState(running = true)).kind)
        assertEquals(RowStatusKind.WAITING_NETWORK, classifySync(SyncState(lastError = SyncError.NETWORK, lastSuccessAt = 100L)).kind)
        assertEquals(RowStatusKind.FAILED, classifySync(SyncState(lastError = SyncError.SERVER, lastSuccessAt = 100L)).kind)
        assertEquals(RowStatusKind.ACTION_NEEDED, classifySync(SyncState(pendingRounds = 2)).kind)
        assertEquals(RowStatusKind.OK, classifySync(SyncState(lastSuccessAt = 100L)).kind)
    }

    @Test
    fun classifyContentMapsEveryStateCorrectly() {
        assertEquals(RowStatusKind.IDLE, classifyContent(ContentUpdateState()).kind)
        assertEquals(RowStatusKind.BUSY, classifyContent(ContentUpdateState(downloading = true)).kind)
        assertEquals(RowStatusKind.WAITING_NETWORK, classifyContent(ContentUpdateState(lastError = ContentError.NETWORK, lastSuccessAt = 100L)).kind)
        assertEquals(RowStatusKind.FAILED, classifyContent(ContentUpdateState(lastError = ContentError.INSTALL_FAILED, installedVersion = 2026112401L)).kind)
        assertEquals(
            RowStatusKind.ACTION_NEEDED,
            classifyContent(ContentUpdateState(pending = listOf(PendingPack(ContentRelease(1, 7, 2026112501L, 1_500_000L, "a".repeat(64)))))).kind,
        )
        assertEquals(RowStatusKind.OK, classifyContent(ContentUpdateState(installedVersion = 2026112401L)).kind)
    }

    @Test
    fun contentRowShowsUpToDateAndFailedWithRetryButton() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val loaded = runBlocking { settings(student(repository())).loaded() }
        val account = com.madovygabor.maturimate.features.auth.domain.AccountUser("id", "anna@example.com", "Anna", "Kovács")
        val noon = today.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds() + 12 * 3_600_000L
        var retried = 0
        setContent {
            AppTheme {
                SettingsContent(
                    loaded.copy(
                        account = account,
                        guestPhone = false,
                        content = ContentUpdateState(lastError = ContentError.UNKNOWN, lastSuccessAt = noon, installedVersion = 2026112401L),
                    ),
                    SettingsActions(onUpdateContent = { retried++ }),
                )
            }
        }

        onNodeWithText("Nem sikerült frissíteni · v2026112401").assertExists()
        onNodeWithText("Újra").performClick()
        assertEquals(1, retried)
    }

    @Test
    fun contentRowNeverShowsTimestampWhenVersionIsMissing() = runDesktopComposeUiTest(412, 1200) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val noon = today.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds() + 12 * 3_600_000L
        lateinit var display: StatusRowDisplay
        setContent {
            AppTheme {
                display = contentRowDisplay(
                    ContentUpdateState(lastSuccessAt = noon, installedVersion = null),
                    today = today,
                    onRetryOrDownload = {},
                )
            }
        }
        assertEquals("Naprakész", display.text)
    }

    @Test
    fun contentRowShowsInstalledVersionAndRedOutdatedState() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        assertEquals("v2026112401", formatContentVersion(2026112401L))
        assertEquals("v2026112402", formatContentVersion(2026112402L))
        val loaded = runBlocking { settings(student(repository())).loaded() }
        val account = com.madovygabor.maturimate.features.auth.domain.AccountUser("id", "anna@example.com", "Anna", "Kovács")
        setContent {
            AppTheme {
                SettingsContent(
                    loaded.copy(
                        account = account,
                        guestPhone = false,
                        content = ContentUpdateState(installedVersion = 2026112401L),
                    ),
                    SettingsActions(),
                )
            }
        }
        onNodeWithText("Naprakész · v2026112401").assertExists()
    }

    @Test
    fun contentRowOutdatedUsesErrorToneAndShowsNewVersion() = runDesktopComposeUiTest(412, 1200) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        lateinit var display: StatusRowDisplay
        setContent {
            AppTheme {
                display = contentRowDisplay(
                    ContentUpdateState(
                        installedVersion = 2026112401L,
                        pending = listOf(PendingPack(ContentRelease(1, 7, 2026112801L, 1_500_000L, "a".repeat(64)))),
                    ),
                    today = today,
                    onRetryOrDownload = {},
                )
            }
        }
        assertEquals(SettingsValueTone.ERROR, display.tone)
        assertEquals("Régi verzió (v2026112401) · Új: v2026112801", display.text)
    }

    @Test
    fun manualSyncAndContentCheckShowMinimumFeedback() = runBlocking<Unit> {
        val repo = student(repository())
        val vm = settings(repo, manualFeedbackMs = 80L)
        vm.loaded()

        vm.syncNow()
        vm.state.awaitFirst { it?.sync?.running == true }
        vm.state.awaitFirst { it?.sync?.running == false }

        vm.updateContent()
        vm.state.awaitFirst { it?.content?.downloading == true }
        vm.state.awaitFirst { it?.content?.downloading == false }
    }
}
