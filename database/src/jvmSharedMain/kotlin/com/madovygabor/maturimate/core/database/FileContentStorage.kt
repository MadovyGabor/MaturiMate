package com.madovygabor.maturimate.core.database

import java.io.File
import java.security.MessageDigest

/** [ContentStorage] in a folder: `content.db` and `content-manifest.json`. Used on Android and desktop. */
class FileContentStorage(private val dir: File) : ContentStorage {

    val databaseFile: File get() = File(dir, CONTENT_DATABASE_NAME)
    private val manifestFile: File get() = File(dir, ContentManifest.FILE_NAME)

    override fun readInstalledManifest(): String? =
        manifestFile.takeIf { it.isFile && databaseFile.isFile }?.readText()

    override fun install(database: ByteArray, manifestJson: String) {
        dir.mkdirs()
        replace(databaseFile, database)
        // the manifest goes last: if the app stops in between, the next start installs again
        replace(manifestFile, manifestJson.encodeToByteArray())
    }

    override fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun replace(target: File, bytes: ByteArray) {
        val temp = File(dir, "${target.name}.tmp")
        temp.writeBytes(bytes)
        // rename replaces the target in one step on Android; Windows refuses to rename over an existing file
        // (java.nio.file is not available before Android API 26)
        if (!temp.renameTo(target)) {
            target.delete()
            check(temp.renameTo(target)) { "cannot replace ${target.path}" }
        }
    }
}
