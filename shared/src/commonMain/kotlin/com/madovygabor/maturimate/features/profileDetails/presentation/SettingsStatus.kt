package com.madovygabor.maturimate.features.profileDetails.presentation

import androidx.compose.runtime.Composable
import com.madovygabor.maturimate.core.presentation.uiComponents.SettingsTrailing
import com.madovygabor.maturimate.core.presentation.uiComponents.SettingsValueTone
import com.madovygabor.maturimate.core.presentation.utils.longDate
import com.madovygabor.maturimate.features.content.domain.ContentError
import com.madovygabor.maturimate.features.content.domain.ContentUpdateState
import com.madovygabor.maturimate.features.sync.domain.SyncError
import com.madovygabor.maturimate.features.sync.domain.SyncState
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.settings_content_available
import maturimate.shared.generated.resources.settings_content_download
import maturimate.shared.generated.resources.settings_content_failed
import maturimate.shared.generated.resources.settings_content_failed_last
import maturimate.shared.generated.resources.settings_content_install_error
import maturimate.shared.generated.resources.settings_content_install_failed_last
import maturimate.shared.generated.resources.settings_content_mobile
import maturimate.shared.generated.resources.settings_content_outdated
import maturimate.shared.generated.resources.settings_content_outdated_mobile
import maturimate.shared.generated.resources.settings_content_outdated_version
import maturimate.shared.generated.resources.settings_content_up_to_date_date
import maturimate.shared.generated.resources.settings_content_up_to_date_plain
import maturimate.shared.generated.resources.settings_content_updating
import maturimate.shared.generated.resources.settings_content_waiting_network
import maturimate.shared.generated.resources.settings_content_waiting_network_last
import maturimate.shared.generated.resources.settings_retry
import maturimate.shared.generated.resources.settings_sync_failed
import maturimate.shared.generated.resources.settings_sync_failed_last
import maturimate.shared.generated.resources.settings_sync_never
import maturimate.shared.generated.resources.settings_sync_running
import maturimate.shared.generated.resources.settings_sync_synced
import maturimate.shared.generated.resources.settings_sync_waiting_network
import maturimate.shared.generated.resources.settings_sync_waiting_network_last
import maturimate.shared.generated.resources.settings_sync_waiting_rounds
import maturimate.shared.generated.resources.settings_time_today
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Unified status kind for both Sync and Content update rows. */
enum class RowStatusKind { OK, BUSY, WAITING_NETWORK, FAILED, ACTION_NEEDED, IDLE }

data class SyncRowModel(
    val kind: RowStatusKind,
    val lastSuccessAt: Long?,
    val pendingRounds: Int,
)

data class ContentRowModel(
    val kind: RowStatusKind,
    val lastSuccessAt: Long?,
    val installedVersion: Long?,
    val latestPendingVersion: Long?,
    val pendingMegabytes: Long,
    val needsMobileData: Boolean,
    val installFailed: Boolean,
)

data class StatusRowDisplay(
    val text: String,
    val tone: SettingsValueTone,
    val trailing: SettingsTrailing,
)

fun classifySync(sync: SyncState): SyncRowModel = SyncRowModel(
    kind = when {
        sync.running -> RowStatusKind.BUSY
        sync.lastError == SyncError.NETWORK -> RowStatusKind.WAITING_NETWORK
        sync.lastError != null -> RowStatusKind.FAILED
        sync.pendingRounds > 0 -> RowStatusKind.ACTION_NEEDED
        sync.lastSuccessAt != null -> RowStatusKind.OK
        else -> RowStatusKind.IDLE
    },
    lastSuccessAt = sync.lastSuccessAt,
    pendingRounds = sync.pendingRounds,
)

fun classifyContent(content: ContentUpdateState): ContentRowModel {
    val megabytes = if (content.pendingBytes > 0) (content.pendingBytes + 1024 * 1024 - 1) / (1024 * 1024) else 0L
    return ContentRowModel(
        kind = when {
            content.downloading -> RowStatusKind.BUSY
            content.lastError == ContentError.NETWORK -> RowStatusKind.WAITING_NETWORK
            content.lastError != null -> RowStatusKind.FAILED
            content.needsMobileData || content.pending.isNotEmpty() -> RowStatusKind.ACTION_NEEDED
            content.lastSuccessAt != null || content.installedVersion != null -> RowStatusKind.OK
            else -> RowStatusKind.IDLE
        },
        lastSuccessAt = content.lastSuccessAt,
        installedVersion = content.installedVersion,
        latestPendingVersion = content.pending.maxOfOrNull { it.release.contentVersion },
        pendingMegabytes = megabytes,
        needsMobileData = content.needsMobileData,
        installFailed = content.lastError == ContentError.INSTALL_FAILED,
    )
}

/** Shows a `YYYYMMDDNN` content version as `v` and its digits only, with no dots (for example `v2026112401`). */
fun formatContentVersion(version: Long): String = "v$version"

@OptIn(ExperimentalTime::class)
@Composable
fun formatTimestamp(epochMillis: Long, today: LocalDate?, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val local = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone)
    return if (local.date == today) {
        val time = "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
        stringResource(Res.string.settings_time_today, time)
    } else {
        longDate(local.date)
    }
}

@Composable
fun syncRowDisplay(sync: SyncState, today: LocalDate?, onRetry: () -> Unit): StatusRowDisplay {
    val model = classifySync(sync)
    val last = model.lastSuccessAt?.let { formatTimestamp(it, today) }
    val retry = stringResource(Res.string.settings_retry)
    return when (model.kind) {
        RowStatusKind.BUSY -> StatusRowDisplay(
            text = stringResource(Res.string.settings_sync_running),
            tone = SettingsValueTone.DEFAULT,
            trailing = SettingsTrailing.Busy,
        )
        RowStatusKind.WAITING_NETWORK -> StatusRowDisplay(
            text = if (last != null) stringResource(Res.string.settings_sync_waiting_network_last, last)
            else stringResource(Res.string.settings_sync_waiting_network),
            tone = SettingsValueTone.DEFAULT,
            trailing = SettingsTrailing.Action(retry, onRetry),
        )
        RowStatusKind.FAILED -> StatusRowDisplay(
            text = if (last != null) stringResource(Res.string.settings_sync_failed_last, last)
            else stringResource(Res.string.settings_sync_failed),
            tone = SettingsValueTone.ERROR,
            trailing = SettingsTrailing.Action(retry, onRetry),
        )
        RowStatusKind.ACTION_NEEDED -> StatusRowDisplay(
            text = pluralStringResource(Res.plurals.settings_sync_waiting_rounds, model.pendingRounds, model.pendingRounds),
            tone = SettingsValueTone.DEFAULT,
            trailing = SettingsTrailing.Action(retry, onRetry),
        )
        RowStatusKind.OK -> StatusRowDisplay(
            text = stringResource(Res.string.settings_sync_synced, last.orEmpty()),
            tone = SettingsValueTone.SUCCESS,
            trailing = SettingsTrailing.None,
        )
        RowStatusKind.IDLE -> StatusRowDisplay(
            text = stringResource(Res.string.settings_sync_never),
            tone = SettingsValueTone.DEFAULT,
            trailing = SettingsTrailing.None,
        )
    }
}

@Composable
fun contentRowDisplay(content: ContentUpdateState, today: LocalDate?, onRetryOrDownload: () -> Unit): StatusRowDisplay {
    val model = classifyContent(content)
    val version = model.installedVersion?.let(::formatContentVersion)
    val retry = stringResource(Res.string.settings_retry)
    val download = stringResource(Res.string.settings_content_download)
    return when (model.kind) {
        RowStatusKind.BUSY -> StatusRowDisplay(
            text = stringResource(Res.string.settings_content_updating),
            tone = SettingsValueTone.DEFAULT,
            trailing = SettingsTrailing.Busy,
        )
        RowStatusKind.WAITING_NETWORK -> StatusRowDisplay(
            text = if (version != null) stringResource(Res.string.settings_content_waiting_network_last, version)
            else stringResource(Res.string.settings_content_waiting_network),
            tone = SettingsValueTone.DEFAULT,
            trailing = SettingsTrailing.Action(retry, onRetryOrDownload),
        )
        RowStatusKind.FAILED -> StatusRowDisplay(
            text = when {
                model.installFailed && version != null -> stringResource(Res.string.settings_content_install_failed_last, version)
                model.installFailed -> stringResource(Res.string.settings_content_install_error)
                version != null -> stringResource(Res.string.settings_content_failed_last, version)
                else -> stringResource(Res.string.settings_content_failed)
            },
            tone = SettingsValueTone.ERROR,
            trailing = SettingsTrailing.Action(retry, onRetryOrDownload),
        )
        RowStatusKind.ACTION_NEEDED -> {
            val newVer = model.latestPendingVersion?.let(::formatContentVersion)
            val text = when {
                model.needsMobileData && version != null -> stringResource(
                    Res.string.settings_content_outdated_mobile,
                    version,
                    model.pendingMegabytes.toString(),
                )
                version != null && newVer != null -> stringResource(
                    Res.string.settings_content_outdated_version,
                    version,
                    newVer,
                )
                version != null -> stringResource(Res.string.settings_content_outdated, version)
                model.needsMobileData -> stringResource(Res.string.settings_content_mobile, model.pendingMegabytes.toString())
                else -> stringResource(Res.string.settings_content_available, model.pendingMegabytes.toString())
            }
            StatusRowDisplay(
                text = text,
                tone = SettingsValueTone.ERROR,
                trailing = SettingsTrailing.Action(download, onRetryOrDownload),
            )
        }
        RowStatusKind.OK -> StatusRowDisplay(
            text = if (version != null) stringResource(Res.string.settings_content_up_to_date_date, version)
            else stringResource(Res.string.settings_content_up_to_date_plain),
            tone = SettingsValueTone.SUCCESS,
            trailing = SettingsTrailing.None,
        )
        RowStatusKind.IDLE -> StatusRowDisplay(
            text = stringResource(Res.string.settings_content_up_to_date_plain),
            tone = SettingsValueTone.DEFAULT,
            trailing = SettingsTrailing.None,
        )
    }
}
