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
        vm.syncState.awaitFirst { it.running }
        vm.syncState.awaitFirst { !it.running }

        vm.updateContent()
        vm.contentState.awaitFirst { it.downloading }
        vm.contentState.awaitFirst { !it.downloading }
    }

    @Test
    fun syncRowActionNeededUsesUploadLabelAndCheckContentUsesSyncEngine() = runDesktopComposeUiTest(412, 1200) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        lateinit var display: StatusRowDisplay
        setContent {
            AppTheme {
                display = syncRowDisplay(
                    SyncState(signedIn = true, pendingRounds = 2),
                    today = today,
                    onRetry = {},
                )
            }
        }
        assertEquals(
            "Feltöltés",
            (display.trailing as com.madovygabor.maturimate.core.presentation.uiComponents.SettingsTrailing.Action).text,
        )

        val repo = runBlocking { student(repository()) }
        var syncCalls = 0
        val engine = com.madovygabor.maturimate.features.sync.domain.SyncEngine(
            repository = object : com.madovygabor.maturimate.features.sync.domain.SyncRepository {
                override suspend fun sync(
                    cursor: String?,
                    deviceId: String,
                    guestMerge: Boolean,
                    changes: com.madovygabor.maturimate.features.sync.domain.SyncChanges,
                ): com.madovygabor.maturimate.core.domain.error.OperationResult<com.madovygabor.maturimate.features.sync.domain.SyncResponse, SyncError> {
                    syncCalls++
                    return com.madovygabor.maturimate.core.domain.error.OperationResult.Error(SyncError.NETWORK)
                }
            },
            store = java.lang.reflect.Proxy.newProxyInstance(
                com.madovygabor.maturimate.features.sync.domain.SyncStore::class.java.classLoader,
                arrayOf(com.madovygabor.maturimate.features.sync.domain.SyncStore::class.java),
            ) { _, method, _ ->
                when (method.name) {
                    "state" -> com.madovygabor.maturimate.features.sync.domain.StoredSyncState(
                        userId = "u",
                        cursor = null,
                        lastPushAt = null,
                        lastSuccessAt = null,
                        fullPushPending = false,
                        guestMergePending = false,
                        rejectedCount = 0,
                    )
                    "pendingSummary" -> Pair(0, null as Long?)
                    "pending" -> com.madovygabor.maturimate.features.sync.domain.PendingChanges(
                        com.madovygabor.maturimate.features.sync.domain.SyncChanges(),
                        null,
                        moreRounds = false,
                    )
                    "pendingCount" -> 0
                    "cursor" -> null
                    "needsGuestMerge" -> false
                    "outgoing" -> com.madovygabor.maturimate.features.sync.domain.SyncChanges()
                    else -> Unit
                }
            } as com.madovygabor.maturimate.features.sync.domain.SyncStore,
            session = object : com.madovygabor.maturimate.features.sync.domain.SyncSession {
                override suspend fun isSignedIn() = true
                override suspend fun deviceId() = "dev"
            },
            now = { 10_000L },
        )
        val packs = java.lang.reflect.Proxy.newProxyInstance(
            com.madovygabor.maturimate.features.content.domain.ContentPacks::class.java.classLoader,
            arrayOf(com.madovygabor.maturimate.features.content.domain.ContentPacks::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "getSchemaVersion" -> 7L
                "installedSubjects" -> mapOf(1L to com.madovygabor.maturimate.features.content.domain.SubjectContentInfo(false, 2026112401L))
                "installFailed" -> false
                else -> null
            }
        } as com.madovygabor.maturimate.features.content.domain.ContentPacks
        val updater = com.madovygabor.maturimate.features.content.domain.ContentUpdater(
            packs = packs,
            source = com.madovygabor.maturimate.features.content.domain.PackSource {
                com.madovygabor.maturimate.core.domain.error.OperationResult.Error(ContentError.NETWORK)
            },
            network = com.madovygabor.maturimate.features.content.domain.NetworkState { false },
            chosenSubjects = { listOf(1L) },
        )

        val vm = settings(repo, syncEngine = engine, contentUpdater = updater, manualFeedbackMs = 0L)
        runBlocking {
            vm.loaded()
            vm.updateContent()
            vm.contentState.awaitFirst { it.lastError == ContentError.NETWORK }
        }
        assertEquals(1, syncCalls)
        assertEquals(ContentError.NETWORK, vm.contentState.value.lastError)
    }
}
