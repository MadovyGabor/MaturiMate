package com.madovygabor.maturimate.core.database

import java.io.File
import java.security.MessageDigest

/**
 * [ContentStorage] in a folder: `content.db`, `content-manifest.json`, `content-installed.json` and the parts it is built
 * from in `parts/` (the bundled file and the downloaded packs). Used on Android and desktop.
 */
class FileContentStorage(private val dir: File, private val sqlite: SqliteRunner) : ContentStorage {

    val databaseFile: File get() = File(dir, CONTENT_DATABASE_NAME)
    private val manifestFile: File get() = File(dir, ContentManifest.FILE_NAME)
    private val stateFile: File get() = File(dir, InstalledContent.FILE_NAME)
    private val partsDir: File get() = File(dir, "parts")

    override fun readInstalledManifest(): String? =
        manifestFile.takeIf { it.isFile && databaseFile.isFile }?.readText()

    override fun readInstalledState(): String? = stateFile.takeIf { it.isFile && databaseFile.isFile }?.readText()

    override fun savePart(name: String, database: ByteArray, manifestJson: String) {
        partsDir.mkdirs()
        replace(File(partsDir, "$name.db"), database)
        // the manifest goes last: a part without its manifest does not exist
        replace(File(partsDir, "$name.json"), manifestJson.encodeToByteArray())
    }

    override fun readPartManifest(name: String): String? =
        File(partsDir, "$name.json").takeIf { it.isFile && File(partsDir, "$name.db").isFile }?.readText()

    override fun partNames(): List<String> =
        partsDir.listFiles { f -> f.name.endsWith(".json") }?.map { it.nameWithoutExtension }
            ?.filter { File(partsDir, "$it.db").isFile }?.sorted().orEmpty()

    override fun adoptInstalledAsPart(name: String) {
        if (!databaseFile.isFile || !manifestFile.isFile) return
        partsDir.mkdirs()
        replace(File(partsDir, "$name.db"), databaseFile.readBytes())
        replace(File(partsDir, "$name.json"), manifestFile.readBytes())
    }

    override fun installMerged(partNames: List<String>, manifestFor: (size: Long, sha256: String) -> String, stateJson: String) {
        require(partNames.isNotEmpty()) { "nothing to build the content file from" }
        dir.mkdirs()
        val temp = File(dir, "$CONTENT_DATABASE_NAME.tmp")
        temp.delete()
        // the first part is the base (its schema and user_version are already right); the others are merged in
        File(partsDir, "${partNames.first()}.db").copyTo(temp, overwrite = true)
        try {
            if (partNames.size > 1) {
                val merge = partNames.drop(1).flatMapIndexed { i, name ->
                    listOf("ATTACH DATABASE '${sqlPath(File(partsDir, "$name.db"))}' AS p$i") +
                        MERGED_TABLES.map { "INSERT OR IGNORE INTO main.$it SELECT * FROM p$i.$it" } +
                        "DETACH DATABASE p$i"
                }
                sqlite.execute(temp.absolutePath, merge)
            }
            check(sqlite.queryStrings(temp.absolutePath, "PRAGMA integrity_check") == listOf("ok")) { "integrity_check failed" }
            check(sqlite.queryStrings(temp.absolutePath, "PRAGMA foreign_key_check").isEmpty()) { "foreign_key_check failed" }
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
        val bytes = temp.readBytes()
        move(temp, databaseFile)
        // the manifest and the state go last: if the app stops in between, the next start builds again
        replace(manifestFile, manifestFor(bytes.size.toLong(), sha256(bytes)).encodeToByteArray())
        replace(stateFile, stateJson.encodeToByteArray())
    }

    override fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun sqlPath(file: File) = file.absolutePath.replace("'", "''")

    private fun replace(target: File, bytes: ByteArray) {
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeBytes(bytes)
        move(temp, target)
    }

    private fun move(temp: File, target: File) {
        // rename replaces the target in one step on Android; Windows refuses to rename over an existing file
        // (java.nio.file is not available before Android API 26)
        if (!temp.renameTo(target)) {
            target.delete()
            check(temp.renameTo(target)) { "cannot replace ${target.path}" }
        }
    }

    private companion object {
        /** Content tables in the order of their foreign keys; the first two are the catalogues every part repeats. */
        val MERGED_TABLES = listOf(
            "task_format", "tag", "subject", "topic", "learning_unit", "unit_page", "page_block", "task",
            "task_option", "task_key", "task_accepted_answer", "task_attachment", "task_tag", "unit_task", "unit_tag",
        )
    }
}
