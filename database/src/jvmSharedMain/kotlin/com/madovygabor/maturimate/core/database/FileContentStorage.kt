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

    override fun partSubjectIds(name: String): List<Long> =
        runCatching {
            sqlite.queryStrings(File(partsDir, "$name.db").absolutePath, "SELECT subject_id FROM subject").mapNotNull { it.toLongOrNull() }
        }.getOrDefault(emptyList())

    override fun installMerged(parts: List<MergePart>, manifestFor: (size: Long, sha256: String) -> String, stateJson: String) {
        require(parts.isNotEmpty()) { "nothing to build the content file from" }
        dir.mkdirs()
        val temp = File(dir, "$CONTENT_DATABASE_NAME.tmp")
        val copies = mutableListOf<File>()
        temp.delete()
        try {
            // the first part is the base (its schema and user_version are already right); the others are merged in
            File(partsDir, "${parts.first().name}.db").copyTo(temp, overwrite = true)
            removeSubjects(temp, parts.first().skipSubjects)
            if (parts.size > 1) {
                val merge = parts.drop(1).flatMapIndexed { i, part ->
                    // the rows of the subjects another part covers are removed from a copy: the part on disk stays whole
                    val source = if (part.skipSubjects.isEmpty()) File(partsDir, "${part.name}.db") else {
                        File(dir, "merge-$i.tmp").also { copy ->
                            copies += copy
                            File(partsDir, "${part.name}.db").copyTo(copy, overwrite = true)
                            removeSubjects(copy, part.skipSubjects)
                        }
                    }
                    listOf("ATTACH DATABASE '${sqlPath(source)}' AS p$i") +
                        MERGED_TABLES.map { "INSERT OR IGNORE INTO main.$it SELECT * FROM p$i.$it" } +
                        "DETACH DATABASE p$i"
                }
                sqlite.execute(temp.absolutePath, merge)
            }
            check(sqlite.queryStrings(temp.absolutePath, "PRAGMA integrity_check") == listOf("ok")) {
                "integrity_check failed"
            }
            // only the table of every violation, counted: never a row of the content
            val violations = sqlite.queryStrings(temp.absolutePath, "PRAGMA foreign_key_check")
            check(violations.isEmpty()) {
                "foreign_key_check failed (violations per table: " +
                    violations.groupingBy { it }.eachCount().entries.sortedBy { it.key }.joinToString(", ") { "${it.key}=${it.value}" } + ")"
            }
        } catch (e: Exception) {
            temp.delete()
            throw e
        } finally {
            copies.forEach { it.delete() }
        }
        val bytes = temp.readBytes()
        move(temp, databaseFile)
        // the manifest and the state go last: if the app stops in between, the next start builds again
        replace(manifestFile, manifestFor(bytes.size.toLong(), sha256(bytes)).encodeToByteArray())
        replace(stateFile, stateJson.encodeToByteArray())
    }

    /**
     * Deletes everything of the subjects from a copy of a part (its topics down to the lessons and tasks). Foreign keys are
     * off in the runner, so every table is cleared explicitly; a row of another subject that still points at a deleted
     * one shows up in the foreign key check of the build, which then fails and leaves the installed file as it was.
     */
    private fun removeSubjects(file: File, subjects: List<Long>) {
        if (subjects.isEmpty()) return
        val ids = subjects.joinToString(",")
        val topics = "SELECT id FROM skip_topic"
        val units = "SELECT id FROM skip_unit"
        val tasks = "SELECT id FROM skip_task"
        sqlite.execute(
            file.absolutePath,
            listOf(
                "CREATE TEMP TABLE skip_topic AS WITH RECURSIVE t(id) AS (SELECT topic_id FROM topic WHERE subject_id IN ($ids) " +
                    "UNION SELECT c.topic_id FROM topic c JOIN t ON c.parent_topic_id = t.id) SELECT id FROM t",
                "CREATE TEMP TABLE skip_unit AS SELECT learning_unit_id AS id FROM learning_unit WHERE topic_id IN ($topics)",
                "CREATE TEMP TABLE skip_task AS SELECT task_id AS id FROM task WHERE topic_id IN ($topics)",
                "DELETE FROM page_block WHERE learning_unit_id IN ($units)",
                "DELETE FROM unit_page WHERE learning_unit_id IN ($units)",
                "DELETE FROM unit_tag WHERE learning_unit_id IN ($units)",
                "DELETE FROM unit_task WHERE learning_unit_id IN ($units) OR task_id IN ($tasks)",
                "DELETE FROM task_tag WHERE task_id IN ($tasks)",
                "DELETE FROM task_key WHERE task_id IN ($tasks)",
                "DELETE FROM task_option WHERE task_id IN ($tasks)",
                "DELETE FROM task_accepted_answer WHERE task_id IN ($tasks)",
                "DELETE FROM task_attachment WHERE task_id IN ($tasks)",
                "DELETE FROM learning_unit WHERE learning_unit_id IN ($units)",
                "DELETE FROM task WHERE task_id IN ($tasks)",
                "DELETE FROM topic WHERE topic_id IN ($topics)",
                "DELETE FROM subject WHERE subject_id IN ($ids)",
                "DROP TABLE skip_topic",
                "DROP TABLE skip_unit",
                "DROP TABLE skip_task",
            ),
        )
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
