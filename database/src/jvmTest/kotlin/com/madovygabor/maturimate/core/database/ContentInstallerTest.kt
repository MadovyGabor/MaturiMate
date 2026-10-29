package com.madovygabor.maturimate.core.database

import com.madovygabor.maturimate.database.content.ContentDatabase
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContentInstallerTest {

    private val dir = Files.createTempDirectory("content").toFile()
    private val storage = FileContentStorage(dir)
    private val installer = ContentInstaller(storage)

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun release(version: Long, bytes: ByteArray = "db $version".encodeToByteArray(), schema: Long = ContentDatabase.Schema.version) =
        bytes to ContentManifest(version, schema, CONTENT_DATABASE_NAME, bytes.size.toLong(), storage.sha256(bytes)).toJson()

    @Test
    fun firstStartInstallsTheBundledFile() {
        assertNull(installer.installedManifest())
        val (db, manifest) = release(2026102801)

        assertEquals(ContentInstallResult.Installed(2026102801), installer.installIfNewer(db, manifest))
        assertTrue(storage.databaseFile.readBytes().contentEquals(db))
        assertEquals(2026102801, installer.installedManifest()?.contentVersion)
    }

    @Test
    fun sameOrOlderVersionIsNotInstalled() {
        val (db, manifest) = release(2026102801)
        installer.installIfNewer(db, manifest)
        val (olderDb, olderManifest) = release(2026102701)

        assertFalse(installer.isNewer(manifest))
        assertEquals(ContentInstallResult.UpToDate(2026102801), installer.installIfNewer(db, manifest))
        assertEquals(ContentInstallResult.UpToDate(2026102801), installer.installIfNewer(olderDb, olderManifest))
        assertTrue(storage.databaseFile.readBytes().contentEquals(db))
    }

    @Test
    fun newerVersionReplacesTheInstalledFile() {
        installer.installIfNewer(release(2026102801).first, release(2026102801).second)
        val (newDb, newManifest) = release(2026110101)

        assertTrue(installer.isNewer(newManifest))
        assertEquals(ContentInstallResult.Installed(2026110101), installer.installIfNewer(newDb, newManifest))
        assertTrue(storage.databaseFile.readBytes().contentEquals(newDb))
        assertFalse(dir.listFiles()!!.any { it.name.endsWith(".tmp") })
    }

    @Test
    fun wrongHashIsRejected() {
        val (_, manifest) = release(2026102801, "original".encodeToByteArray())
        val result = installer.installIfNewer("tampered".encodeToByteArray(), manifest)

        assertIs<ContentInstallResult.Rejected>(result)
        assertNull(installer.installedManifest())
    }

    @Test
    fun otherSchemaVersionIsRejected() {
        val (db, manifest) = release(2026102801, schema = ContentDatabase.Schema.version + 1)
        assertIs<ContentInstallResult.Rejected>(installer.installIfNewer(db, manifest))
    }

    @Test
    fun brokenManifestIsRejected() {
        assertIs<ContentInstallResult.Rejected>(installer.installIfNewer(ByteArray(0), "{"))
        assertFalse(installer.isNewer("{"))
    }
}
