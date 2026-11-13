package com.madovygabor.maturimate.core.database

import com.madovygabor.maturimate.database.content.ContentDatabase

/** The installed content database file and the parts it is built from, on this device. */
interface ContentStorage {
    /** The manifest of the installed content, or null when nothing is installed yet. */
    fun readInstalledManifest(): String?

    /** `content-installed.json`: the parts the installed file was built from; null before the first build. */
    fun readInstalledState(): String?

    /** Keeps a part (the bundled file or a downloaded pack) for the next build. A part of the same name is replaced. */
    fun savePart(name: String, database: ByteArray, manifestJson: String)

    fun readPartManifest(name: String): String?

    fun partNames(): List<String>

    /** Keeps the file installed before parts existed as the part [name], so the next build does not lose it. */
    fun adoptInstalledAsPart(name: String)

    /**
     * Builds the installed file from the parts, the first part as the base and the others merged in without overwriting,
     * checks it, and moves it over the installed file in one step. The manifest ([manifestFor] gets the size and hash of
     * the new file) and then the state are written last. On any failure the installed file stays as it was.
     */
    fun installMerged(partNames: List<String>, manifestFor: (size: Long, sha256: String) -> String, stateJson: String)

    fun sha256(bytes: ByteArray): String
}

sealed interface ContentInstallResult {
    data class Installed(val contentVersion: Long) : ContentInstallResult
    data class UpToDate(val installedVersion: Long) : ContentInstallResult
    data class Rejected(val reason: String) : ContentInstallResult
}

sealed interface PackResult {
    data class Saved(val subjectId: Long, val contentVersion: Long) : PackResult
    data class UpToDate(val installedVersion: Long) : PackResult
    data class Rejected(val reason: String) : PackResult
}

/**
 * Installs content: the file bundled with the app (a starter, or a full file in development builds) and the packs
 * downloaded per subject. The installed `content.db` is always built from the parts on disk: the downloaded packs first,
 * then the bundled file, merged without overwriting. So a pack always beats the bundled lesson text of its subject, a
 * bundled starter never replaces a subject that has a full pack, and a full pack of the same version replaces a starter.
 * The user database is never touched.
 */
class ContentInstaller(
    private val storage: ContentStorage,
    private val schemaVersion: Long = ContentDatabase.Schema.version,
) {

    fun installedManifest(): ContentManifest? =
        storage.readInstalledManifest()?.let { runCatching { ContentManifest.fromJson(it) }.getOrNull() }

    /** What the installed file was built from; null when it was installed before parts existed or nothing is installed. */
    fun installedState(): InstalledContent? =
        storage.readInstalledState()?.let { runCatching { InstalledContent.fromJson(it) }.getOrNull() }

    /**
     * The kind and version of the installed content of one subject, or null when it holds nothing of it. A file installed
     * before parts existed counts as what its manifest says.
     */
    fun installedSubject(subjectId: Long): SubjectContent? {
        installedState()?.let { return it.subject(subjectId) }
        return installedManifest()?.let { SubjectContent(it.kind, it.contentVersion) }
    }

    /**
     * The better of the installed content of a subject and its downloaded pack that waits for the next build (a full one
     * beats a starter, then the newer version): what the phone holds already, so a waiting pack is not downloaded again.
     */
    fun availableSubject(subjectId: Long): SubjectContent? {
        val waiting = storage.readPartManifest(packName(subjectId))
            ?.let { runCatching { ContentManifest.fromJson(it) }.getOrNull() }
            ?.let { SubjectContent(it.kind, it.contentVersion) }
        return listOfNotNull(installedSubject(subjectId), waiting)
            .maxWithOrNull(compareBy({ it.kind == ContentManifest.KIND_FULL }, { it.contentVersion }))
    }

    /** True when a bundled release with this manifest would replace the bundled part (or the file installed before parts). */
    fun isNewer(manifestJson: String): Boolean {
        val candidate = runCatching { ContentManifest.fromJson(manifestJson) }.getOrNull() ?: return false
        val current = currentBundledVersion() ?: return true
        return candidate.contentVersion > current
    }

    private fun currentBundledVersion(): Long? =
        storage.readPartManifest(BUNDLED)?.let { runCatching { ContentManifest.fromJson(it) }.getOrNull() }?.contentVersion
            ?: installedManifest()?.contentVersion

    /** Installs the bundled file when it is newer than the one installed before. */
    fun installIfNewer(database: ByteArray, manifestJson: String): ContentInstallResult {
        val manifest = runCatching { ContentManifest.fromJson(manifestJson) }.getOrElse {
            return ContentInstallResult.Rejected("manifest cannot be read: ${it.message}")
        }
        check(manifest, database)?.let { return ContentInstallResult.Rejected(it) }
        currentBundledVersion()?.let {
            if (manifest.contentVersion <= it) return ContentInstallResult.UpToDate(it)
        }
        adoptLegacy()
        storage.savePart(BUNDLED, database, manifest.toJson())
        return try {
            rebuild()
            ContentInstallResult.Installed(manifest.contentVersion)
        } catch (e: Exception) {
            ContentInstallResult.Rejected("cannot build the content file: ${e.message}")
        }
    }

    /**
     * Keeps a downloaded pack for the next build; call [rebuild] (the provider does it when no lesson is open) to use it.
     * A pack that is not newer than the saved one of its subject is not kept.
     */
    fun savePack(subjectId: Long, database: ByteArray, manifestJson: String): PackResult {
        val manifest = runCatching { ContentManifest.fromJson(manifestJson) }.getOrElse {
            return PackResult.Rejected("manifest cannot be read: ${it.message}")
        }
        if (manifest.kind != ContentManifest.KIND_FULL) return PackResult.Rejected("a pack must be a full file, not ${manifest.kind}")
        if (manifest.subjectIds != listOf(subjectId)) return PackResult.Rejected("the pack holds ${manifest.subjectIds}, not $subjectId")
        check(manifest, database)?.let { return PackResult.Rejected(it) }
        val saved = storage.readPartManifest(packName(subjectId))?.let { runCatching { ContentManifest.fromJson(it) }.getOrNull() }
        if (saved != null && manifest.contentVersion <= saved.contentVersion) return PackResult.UpToDate(saved.contentVersion)
        adoptLegacy()
        storage.savePart(packName(subjectId), database, manifest.toJson())
        return PackResult.Saved(subjectId, manifest.contentVersion)
    }

    /** True when a part on disk is not in the installed file yet (a pack waits for the swap). */
    fun hasPendingParts(): Boolean {
        val onDisk = storage.partNames().associateWith { name ->
            storage.readPartManifest(name)?.let { runCatching { ContentManifest.fromJson(it).sha256 }.getOrNull() }
        }
        val built = installedState()?.parts?.associate { it.name to it.sha256 }
        // before the first build (or for a file installed before parts existed) only a pack can be pending
        return if (built == null) onDisk.keys.any { it != BUNDLED } else onDisk != built
    }

    /** Builds the installed file from the parts on disk and moves it in place. Throws when the result is not valid. */
    fun rebuild() {
        adoptLegacy()
        // downloaded packs first (they win), the bundled file last
        val names = storage.partNames().sortedWith(compareBy({ it == BUNDLED }, { it }))
        val parts = names.map { name ->
            val manifest = ContentManifest.fromJson(storage.readPartManifest(name)!!)
            BuiltPart(name, manifest.contentVersion, manifest.sha256, manifest.kind, manifest.subjectIds)
        }
        val state = InstalledContent(parts)
        val subjects = parts.flatMap { it.subjectIds }.distinct().sorted()
        // starter while some subject has no full pack (a full file without subject ids holds every subject)
        val allFull = parts.any { it.kind == ContentManifest.KIND_FULL && it.subjectIds.isEmpty() } ||
            subjects.all { state.subject(it)?.kind == ContentManifest.KIND_FULL }
        val kind = if (allFull) ContentManifest.KIND_FULL else ContentManifest.KIND_STARTER
        val version = parts.maxOf { it.contentVersion }
        storage.installMerged(
            names,
            manifestFor = { size, sha ->
                ContentManifest(version, schemaVersion, CONTENT_DATABASE_NAME, size, sha, kind, subjects).toJson()
            },
            stateJson = state.toJson(),
        )
    }

    // a file installed before parts existed is kept as the bundled part, so a build from packs does not lose it
    private fun adoptLegacy() {
        if (BUNDLED !in storage.partNames() && storage.readInstalledManifest() != null) storage.adoptInstalledAsPart(BUNDLED)
    }

    private fun check(manifest: ContentManifest, database: ByteArray): String? {
        // a file of another schema would not match the generated queries of this app version
        if (manifest.schemaVersion != schemaVersion) return "schema version ${manifest.schemaVersion}, this app reads $schemaVersion"
        if (database.size.toLong() != manifest.sizeBytes) return "size ${database.size}, manifest says ${manifest.sizeBytes}"
        if (!storage.sha256(database).equals(manifest.sha256, ignoreCase = true)) return "sha256 does not match the manifest"
        return null
    }

    companion object {
        const val BUNDLED = "bundled"
        fun packName(subjectId: Long) = "pack-$subjectId"
    }
}
