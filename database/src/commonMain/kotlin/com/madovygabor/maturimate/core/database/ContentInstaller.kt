package com.madovygabor.maturimate.core.database

import com.madovygabor.maturimate.database.content.ContentDatabase

/** The installed content database file and its manifest on this device. */
interface ContentStorage {
    /** The manifest of the installed content, or null when nothing is installed yet. */
    fun readInstalledManifest(): String?

    /** Replaces the installed content. The new file is written beside the old one and then moved over it. */
    fun install(database: ByteArray, manifestJson: String)

    fun sha256(bytes: ByteArray): String
}

sealed interface ContentInstallResult {
    data class Installed(val contentVersion: Long) : ContentInstallResult
    data class UpToDate(val installedVersion: Long) : ContentInstallResult
    data class Rejected(val reason: String) : ContentInstallResult
}

/**
 * Installs a content database (the one bundled with the app now; downloaded updates later)
 * when it is newer than the installed one and passes the checks. The user database is never touched.
 */
class ContentInstaller(
    private val storage: ContentStorage,
    private val schemaVersion: Long = ContentDatabase.Schema.version,
) {

    fun installedManifest(): ContentManifest? =
        storage.readInstalledManifest()?.let { runCatching { ContentManifest.fromJson(it) }.getOrNull() }

    /** True when a release with this manifest would replace the installed content. */
    fun isNewer(manifestJson: String): Boolean {
        val candidate = runCatching { ContentManifest.fromJson(manifestJson) }.getOrNull() ?: return false
        val installed = installedManifest() ?: return true
        return candidate.contentVersion > installed.contentVersion
    }

    fun installIfNewer(database: ByteArray, manifestJson: String): ContentInstallResult {
        val manifest = runCatching { ContentManifest.fromJson(manifestJson) }.getOrElse {
            return ContentInstallResult.Rejected("manifest cannot be read: ${it.message}")
        }
        // a file of another schema would not match the generated queries of this app version
        if (manifest.schemaVersion != schemaVersion) {
            return ContentInstallResult.Rejected("schema version ${manifest.schemaVersion}, this app reads $schemaVersion")
        }
        val installed = installedManifest()
        if (installed != null && manifest.contentVersion <= installed.contentVersion) {
            return ContentInstallResult.UpToDate(installed.contentVersion)
        }
        if (database.size.toLong() != manifest.sizeBytes) {
            return ContentInstallResult.Rejected("size ${database.size}, manifest says ${manifest.sizeBytes}")
        }
        if (!storage.sha256(database).equals(manifest.sha256, ignoreCase = true)) {
            return ContentInstallResult.Rejected("sha256 does not match the manifest")
        }

        storage.install(database, manifest.toJson())
        return ContentInstallResult.Installed(manifest.contentVersion)
    }
}
