package com.madovygabor.maturimate.features.content.data

import com.madovygabor.maturimate.core.database.ContentDatabaseProvider
import com.madovygabor.maturimate.core.database.ContentManifest
import com.madovygabor.maturimate.core.database.PackResult
import com.madovygabor.maturimate.core.database.SubjectContent
import com.madovygabor.maturimate.features.content.domain.ContentPacks
import com.madovygabor.maturimate.features.content.domain.ContentSwapGate
import com.madovygabor.maturimate.features.content.domain.PackSaveResult
import com.madovygabor.maturimate.features.content.domain.SubjectContentInfo
import com.madovygabor.maturimate.features.sync.domain.ContentRelease

/**
 * The content feature's view of the installed content ([ContentPacks], [ContentSwapGate]) over the content database
 * provider: it turns a release of the server into the manifest the installer checks, and the installer's answers into
 * the feature's own types.
 */
class InstalledContentPacks(private val provider: ContentDatabaseProvider) : ContentPacks, ContentSwapGate {

    override val schemaVersion: Long get() = provider.schemaVersion

    override fun installedSubject(subjectId: Long): SubjectContentInfo? = provider.installedSubject(subjectId)?.toInfo()

    override fun availableSubject(subjectId: Long): SubjectContentInfo? = provider.availableSubject(subjectId)?.toInfo()

    override suspend fun savePack(release: ContentRelease, bytes: ByteArray): PackSaveResult =
        when (provider.savePack(release.subjectId, bytes, packManifest(release).toJson())) {
            is PackResult.Saved -> PackSaveResult.SAVED
            is PackResult.UpToDate -> PackSaveResult.UP_TO_DATE
            is PackResult.Rejected -> PackSaveResult.REJECTED
        }

    override suspend fun applyPending(): Boolean = provider.applyPending()

    override suspend fun setReading(reading: Boolean) {
        provider.swapAllowed = !reading
        if (!reading) provider.applyPending()
    }

    private fun SubjectContent.toInfo() = SubjectContentInfo(isStarter = kind == ContentManifest.KIND_STARTER, contentVersion = contentVersion)
}

/**
 * The manifest a downloaded pack is checked against: the size and the hash of the release the server announced (not of
 * the link, which cannot change the expected hash), one subject, full.
 */
internal fun packManifest(release: ContentRelease) = ContentManifest(
    contentVersion = release.contentVersion, schemaVersion = release.schemaVersion,
    fileName = "content.db", sizeBytes = release.sizeBytes, sha256 = release.sha256,
    kind = ContentManifest.KIND_FULL, subjectIds = listOf(release.subjectId),
)
