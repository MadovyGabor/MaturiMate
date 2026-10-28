package com.madovygabor.maturimate.core.database

import kotlinx.coroutines.runBlocking
import maturimate.composeapp.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.MissingResourceException
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Installs and opens the content file bundled in the app resources.
 * Skipped when the build has no bundled content (run `:tools:content-pipeline:bundleContent` first).
 */
class ContentDatabaseProviderTest {

    private val appDir: File = Files.createTempDirectory("app").toFile()

    @OptIn(ExperimentalResourceApi::class)
    @BeforeTest
    fun requireBundledContent() {
        val bundled = runBlocking {
            try {
                Res.readBytes("files/content-manifest.json")
                true
            } catch (_: MissingResourceException) {
                false
            }
        }
        assumeTrue("no bundled content in this build", bundled)
    }

    @AfterTest
    fun cleanUp() {
        appDir.deleteRecursively()
    }

    @Test
    fun firstStartInstallsAndOpensTheBundledContent() = runBlocking {
        val provider = ContentDatabaseProvider(DatabaseDriverFactory(appDir))

        val db = assertNotNull(provider.get())

        assertIs<ContentInstallResult.Installed>(provider.lastInstallResult)
        assertNotNull(provider.installedVersion())
        assertEquals(true, db.subjectQueries.selectAll().executeAsList().isNotEmpty())
    }

    @Test
    fun secondStartDoesNotInstallAgain() = runBlocking {
        ContentDatabaseProvider(DatabaseDriverFactory(appDir)).get()
        val modified = File(appDir, CONTENT_DATABASE_NAME).lastModified()

        val provider = ContentDatabaseProvider(DatabaseDriverFactory(appDir))
        assertNotNull(provider.get())

        assertNull(provider.lastInstallResult)
        assertEquals(modified, File(appDir, CONTENT_DATABASE_NAME).lastModified())
    }
}
