package com.madovygabor.maturimate.core.database

import app.cash.sqldelight.db.SqlDriver
import com.madovygabor.maturimate.database.content.ContentDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import maturimate.shared.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.MissingResourceException

/** Where the data layer gets the content database from; null when no content is installed. */
fun interface ContentDatabaseSource {
    suspend fun get(): ContentDatabase?

    /**
     * Runs one read with the content database (null when no content is installed). The provider holds its lock meanwhile,
     * so a content swap waits for the read instead of closing the file under it. The block must not call the source again.
     */
    suspend fun <T> read(block: (ContentDatabase?) -> T): T = block(get())
}

/**
 * Gives access to the read-only content database.
 *
 * On first use it installs the content file bundled with the app (`composeResources/files/content.db`,
 * produced by `./gradlew :tools:content-pipeline:bundleContent`) when it is newer than the installed one,
 * then opens the installed file. Returns null when no content is installed and none is bundled.
 *
 * It also keeps the downloaded packs and swaps them in ([savePack], [applyPending]); the content feature reaches these
 * through its own interfaces (`features/content/data/InstalledContentPacks`).
 */
class ContentDatabaseProvider(private val driverFactory: DatabaseDriverFactory) : ContentDatabaseSource {

    private val installer = ContentInstaller(driverFactory.contentStorage())
    private val mutex = Mutex()
    private var database: ContentDatabase? = null
    private var driver: SqlDriver? = null

    /**
     * False while a lesson, a test or a practice round is open: a downloaded pack then waits, because the swap closes
     * the open file. Set by the navigation ([applyPending] takes the pending packs in as soon as it is true again).
     */
    @Volatile
    var swapAllowed: Boolean = true

    /** Result of the last install attempt of the bundled file, for logging and tests. */
    var lastInstallResult: ContentInstallResult? = null
        private set

    /** The schema version of the content database this app reads. */
    val schemaVersion: Long get() = ContentDatabase.Schema.version

    /**
     * Opens the database. Only for a caller that reads nothing later, or in tests: a read made with the returned database
     * outside [read] can meet a swap that closed it.
     */
    override suspend fun get(): ContentDatabase? = mutex.withLock { openLocked() }

    override suspend fun <T> read(block: (ContentDatabase?) -> T): T = mutex.withLock { block(openLocked()) }

    private suspend fun openLocked(): ContentDatabase? = database ?: withContext(Dispatchers.IO) {
        installBundledContent()
        if (installer.installedManifest() == null) return@withContext null
        val newDriver = driverFactory.createContentDriver()
        ContentDatabase(newDriver).also {
            driver = newDriver
            database = it
        }
    }

    /** Keeps a downloaded pack for the next swap; nothing is closed or replaced here. */
    suspend fun savePack(subjectId: Long, database: ByteArray, manifestJson: String): PackResult = mutex.withLock {
        withContext(Dispatchers.IO) { installer.savePack(subjectId, database, manifestJson) }
    }

    /** True when a downloaded pack is not in the installed file yet. */
    suspend fun hasPending(): Boolean = mutex.withLock { withContext(Dispatchers.IO) { installer.hasPendingParts() } }

    /**
     * Builds the installed file from the packs on disk and reopens it, when [swapAllowed]. The open file is closed first,
     * under the same lock as [read], so a read in progress finishes first and the next one opens the new file. Returns
     * true when a swap happened; a failed build leaves the installed file as it was.
     */
    suspend fun applyPending(): Boolean = mutex.withLock {
        if (!swapAllowed) return@withLock false
        withContext(Dispatchers.IO) {
            if (!installer.hasPendingParts()) return@withContext false
            val openDriver = driver
            database = null
            driver = null
            openDriver?.close()
            try {
                installer.rebuild()
                true
            } catch (e: Exception) {
                println("Content swap failed: ${e.message}")
                false
            }
        }
    }

    /** The kind and version of the installed content of a subject; null when it holds none. */
    fun installedSubject(subjectId: Long): SubjectContent? = installer.installedSubject(subjectId)

    /** The same, counting a downloaded pack that waits for its swap. */
    fun availableSubject(subjectId: Long): SubjectContent? = installer.availableSubject(subjectId)

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
