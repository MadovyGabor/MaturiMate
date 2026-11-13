package com.madovygabor.maturimate.pipeline

import com.madovygabor.maturimate.core.database.ContentManifest
import java.io.File
import java.nio.file.Files
import java.sql.Connection

/** One pack that was published. */
data class PublishedPack(val subjectId: Long, val schemaVersion: Long, val contentVersion: Long, val objectPath: String, val sizeBytes: Long, val sha256: String)

/**
 * `publish`: builds the full export, puts every pack in the private R2 bucket and writes the `content_release` rows the
 * Worker and the sync read. It refuses to run before `import` has written the same files to the server: a round answered on
 * a phone with a new task would be rejected (`CONTENT_UNKNOWN`) until it did.
 *
 * Objects are never overwritten: the version is in the key, so no cache can serve an old pack under a new name.
 */
class Publisher(private val connection: Connection, private val store: ObjectStore) {

    fun publish(content: ContentSet, contentVersion: Long): List<PublishedPack> {
        checkImported(content)
        val dir = Files.createTempDirectory("maturimate-publish").toFile()
        try {
            SqliteExporter().export(content, dir, contentVersion, kind = ContentKind.FULL)
            val packs = content.subjects.map { subject ->
                val folder = File(dir, "packs/${subject.subjectId}")
                val manifest = ContentManifest.fromJson(File(folder, ContentManifest.FILE_NAME).readText())
                val path = "packs/${manifest.schemaVersion}/${subject.subjectId}/$contentVersion/${manifest.fileName}"
                PublishedPack(subject.subjectId, manifest.schemaVersion, contentVersion, path, manifest.sizeBytes, manifest.sha256) to File(folder, manifest.fileName)
            }
            // the files first: a release row never points to an object that is not there
            packs.forEach { (pack, file) -> store.put(pack.objectPath, file.readBytes()) }
            writeReleases(packs.map { it.first })
            return packs.map { it.first }
        } finally {
            dir.deleteRecursively()
        }
    }

    /** The highest ids of the files must be on the server already. */
    private fun checkImported(content: ContentSet) {
        val tasks = content.tasks.maxOfOrNull { it.entry.taskId } ?: 0
        val units = (content.summaries.map { it.learningUnitId } + content.practices.map { it.file.learningUnitId }).maxOrNull() ?: 0
        val server = { sql: String -> connection.createStatement().use { s -> s.executeQuery(sql).use { it.next(); it.getLong(1) } } }
        if (server("SELECT coalesce(max(task_id), 0) FROM task") < tasks || server("SELECT coalesce(max(learning_unit_id), 0) FROM learning_unit") < units) {
            throw ExportException("the server does not have these files yet: run `import` with the same content first")
        }
    }

    private fun writeReleases(packs: List<PublishedPack>) {
        val autoCommit = connection.autoCommit
        connection.autoCommit = false
        try {
            packs.forEach { p ->
                connection.prepareStatement("UPDATE content_release SET is_current = false WHERE subject_id = ? AND schema_version = ? AND is_current").use {
                    it.setLong(1, p.subjectId); it.setLong(2, p.schemaVersion); it.executeUpdate()
                }
                connection.prepareStatement(
                    "INSERT INTO content_release (subject_id, schema_version, content_version, object_path, size_bytes, sha256) VALUES (?, ?, ?, ?, ?, ?)"
                ).use {
                    it.setLong(1, p.subjectId); it.setLong(2, p.schemaVersion); it.setLong(3, p.contentVersion)
                    it.setString(4, p.objectPath); it.setLong(5, p.sizeBytes); it.setString(6, p.sha256); it.executeUpdate()
                }
            }
            connection.commit()
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = autoCommit
        }
    }
}
