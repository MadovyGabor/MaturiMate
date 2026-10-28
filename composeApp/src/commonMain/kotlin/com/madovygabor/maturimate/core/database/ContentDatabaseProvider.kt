package com.madovygabor.maturimate.core.database

import com.madovygabor.maturimate.database.content.ContentDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import maturimate.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.MissingResourceException

/** Where the data layer gets the content database from; null when no content is installed. */
fun interface ContentDatabaseSource {
    suspend fun get(): ContentDatabase?
}

/**
 * Gives access to the read-only content database.
 *
 * On first use it installs the content file bundled with the app (`composeResources/files/content.db`,
 * produced by `./gradlew :tools:content-pipeline:bundleContent`) when it is newer than the installed one,
 * then opens the installed file. Returns null when no content is installed and none is bundled.
 */
class ContentDatabaseProvider(private val driverFactory: DatabaseDriverFactory) : ContentDatabaseSource {

    private val installer = ContentInstaller(driverFactory.contentStorage())
    private val mutex = Mutex()
    private var database: ContentDatabase? = null

    /** Result of the last install attempt of the bundled file, for logging and tests. */
    var lastInstallResult: ContentInstallResult? = null
        private set

    override suspend fun get(): ContentDatabase? = mutex.withLock {
        database ?: withContext(Dispatchers.IO) {
            installBundledContent()
            if (installer.installedManifest() == null) return@withContext null
            ContentDatabase(driverFactory.createContentDriver()).also { database = it }
        }
    }

    /** Version of the installed content, or null when none is installed. */
    fun installedVersion(): Long? = installer.installedManifest()?.contentVersion

    @OptIn(ExperimentalResourceApi::class)
    private suspend fun installBundledContent() {
        val manifest = try {
            Res.readBytes("files/${ContentManifest.FILE_NAME}").decodeToString()
        } catch (_: MissingResourceException) {
            return // this build has no bundled content
        }
        // the database file is read only when it would replace the installed one
        if (!installer.isNewer(manifest)) return
        val bytes = Res.readBytes("files/$CONTENT_DATABASE_NAME")
        lastInstallResult = installer.installIfNewer(bytes, manifest)
    }
}
