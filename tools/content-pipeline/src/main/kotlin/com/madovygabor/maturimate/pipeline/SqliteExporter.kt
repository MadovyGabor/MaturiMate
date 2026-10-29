package com.madovygabor.maturimate.pipeline

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.CONTENT_DATABASE_NAME
import com.madovygabor.maturimate.core.database.ContentManifest
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.database.content.ContentDatabase
import com.madovygabor.maturimate.database.content.Learning_unit
import com.madovygabor.maturimate.database.content.Subject
import com.madovygabor.maturimate.database.content.Tag
import com.madovygabor.maturimate.database.content.Task
import com.madovygabor.maturimate.database.content.Task_accepted_answer
import com.madovygabor.maturimate.database.content.Task_attachment
import com.madovygabor.maturimate.database.content.Task_format
import com.madovygabor.maturimate.database.content.Task_key
import com.madovygabor.maturimate.database.content.Task_option
import com.madovygabor.maturimate.database.content.Task_tag
import com.madovygabor.maturimate.database.content.Topic
import com.madovygabor.maturimate.database.content.Unit_task
import java.io.File
import java.security.MessageDigest
import java.sql.DriverManager
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class ExportException(message: String) : Exception(message)

/**
 * Builds the read-only content database for the app from a validated [ContentSet].
 *
 * The schema comes from the generated [ContentDatabase] of the shared `:database` module, so the file
 * always matches what the app reads. The same files and version give a byte-identical file.
 */
class SqliteExporter {

    /**
     * Writes `content.db` and `content-manifest.json` into [outDir].
     *
     * @param contentVersion `yyyyMMddNN`, higher than every earlier release.
     * @param previous the `content.db` of the previous release; no id of it may be missing.
     */
    fun export(content: ContentSet, outDir: File, contentVersion: Long, previous: File? = null): ContentManifest {
        val createdAt = releaseTime(contentVersion)
        previous?.let { checkNothingRemoved(content, it, contentVersion) }

        outDir.mkdirs()
        val dbFile = File(outDir, CONTENT_FILE_NAME)
        dbFile.delete()

        JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}", foreignKeysOn()).use { driver ->
            ContentDatabase.Schema.create(driver)
            val db = ContentDatabase(driver)
            db.transaction { insertAll(db, content, createdAt) }
            check(driver)
            // the Android driver reads the schema version from here; 0 would mean "empty, create it"
            driver.execute(null, "PRAGMA user_version = ${ContentDatabase.Schema.version}", 0)
            driver.execute(null, "VACUUM", 0)
        }

        val bytes = dbFile.readBytes()
        val manifest = ContentManifest(
            contentVersion = contentVersion,
            schemaVersion = ContentDatabase.Schema.version,
            fileName = CONTENT_FILE_NAME,
            sizeBytes = bytes.size.toLong(),
            sha256 = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) },
        )
        File(outDir, ContentManifest.FILE_NAME).writeText(manifest.toJson())
        return manifest
    }

    // Rows are inserted in the order of the files (sorted by name), parents before children
    private fun insertAll(db: ContentDatabase, content: ContentSet, createdAt: Long) {
        content.taskFormats.forEach {
            db.taskFormatQueries.insert(Task_format(it.taskFormatId, it.code, it.name, it.answerMode, it.description))
        }
        content.tags.forEach { db.tagQueries.insert(Tag(it.tagId, it.name, it.tagType, createdAt)) }
        content.subjects.forEach {
            db.subjectQueries.insert(Subject(it.subjectId, it.code, it.nameHu, it.nameSk, it.track, it.description))
        }
        content.topics.forEach {
            db.topicQueries.insert(
                Topic(it.file.topicId, it.subjectId, it.parentTopicId, it.file.title, it.file.description,
                    it.file.orderIndex.toLong(), it.file.isPublished)
            )
        }
        content.summaries.forEach {
            db.learningUnitQueries.insert(
                Learning_unit(it.learningUnitId, it.topicId, "SUMMARY", it.title, it.bodyText,
                    it.estimatedMinutes.toLong(), 5, it.xpReward.toLong(), it.sortOrder.toLong(), it.isPublished, createdAt)
            )
        }
        content.practices.forEach { p ->
            val u = p.file
            db.learningUnitQueries.insert(
                Learning_unit(u.learningUnitId, p.topicId, "PRACTICE", u.title, null, u.estimatedMinutes.toLong(),
                    u.roundSize.toLong(), u.xpReward.toLong(), u.sortOrder.toLong(), u.isPublished, createdAt)
            )
        }

        val formatIds = content.taskFormats.associate { it.code to it.taskFormatId }
        val tagIds = content.tags.associate { it.name to it.tagId }
        content.tasks.forEach { task -> insertTask(db, task, formatIds.getValue(task.entry.format), tagIds, createdAt) }

        content.practices.forEach { p ->
            p.file.tasks.forEachIndexed { index, entry ->
                db.learningUnitQueries.insertUnitTask(
                    Unit_task(p.file.learningUnitId, entry.taskId, index + 1L, entry.isCore)
                )
            }
        }
    }

    private fun insertTask(db: ContentDatabase, task: LoadedTask, formatId: Long, tagIds: Map<String, Long>, createdAt: Long) {
        val t = task.entry
        val q = db.taskQueries
        q.insert(
            Task(t.taskId, task.topicId, formatId, t.questionText, t.explanation, t.maxPoints.toLong(),
                t.answerWordLimit?.toLong(), t.isActive, createdAt)
        )

        val optionIds = mutableMapOf<Pair<String, String>, Long>()
        t.options.forEachIndexed { index, o ->
            val id = childId(t.taskId, index)
            optionIds[o.role to o.label] = id
            q.insertOption(Task_option(id, t.taskId, o.role, o.label, o.text))
        }
        t.key.forEach { k ->
            q.insertKey(
                Task_key(t.taskId, k.slot.toLong(), k.prompt?.let { optionIds.getValue("PROMPT" to it) },
                    optionIds.getValue("ANSWER" to k.option))
            )
        }
        t.acceptedAnswers.forEachIndexed { index, a ->
            q.insertAcceptedAnswer(Task_accepted_answer(childId(t.taskId, index), t.taskId, a.slot.toLong(), a.text, a.caseSensitive))
        }
        t.attachments.forEachIndexed { index, a ->
            q.insertAttachment(Task_attachment(childId(t.taskId, index), t.taskId, a.fileUrl, a.mediaType, a.altText))
        }
        t.tags.forEach { db.tagQueries.insertTaskTag(Task_tag(t.taskId, tagIds.getValue(it), createdAt)) }
    }

    private fun check(driver: SqlDriver) {
        val brokenKeys = driver.executeQuery(null, "PRAGMA foreign_key_check", { QueryResult.Value(it.next().value) }, 0).value
        if (brokenKeys) throw ExportException("foreign_key_check found broken references")
        val integrity = driver.executeQuery(null, "PRAGMA integrity_check", { c ->
            c.next()
            QueryResult.Value(c.getString(0))
        }, 0).value
        if (integrity != "ok") throw ExportException("integrity_check: $integrity")
    }

    /** Content rows are switched off, never removed, because the user database refers to their ids. */
    private fun checkNothingRemoved(content: ContentSet, previous: File, contentVersion: Long) {
        if (!previous.isFile) throw ExportException("previous release not found: ${previous.path}")
        // the app only installs a file with a higher version
        File(previous.parentFile, ContentManifest.FILE_NAME).takeIf { it.isFile }?.let { file ->
            val old = ContentManifest.fromJson(file.readText())
            if (contentVersion <= old.contentVersion) {
                throw ExportException("content version $contentVersion must be higher than the previous ${old.contentVersion}")
            }
        }
        val current = mapOf(
            "subject" to content.subjects.map { it.subjectId }.toSet(),
            "topic" to content.topics.map { it.file.topicId }.toSet(),
            "learning_unit" to (content.summaries.map { it.learningUnitId } + content.practices.map { it.file.learningUnitId }).toSet(),
            "task" to content.tasks.map { it.entry.taskId }.toSet(),
        )
        val missing = DriverManager.getConnection("jdbc:sqlite:${previous.absolutePath}").use { connection ->
            current.flatMap { (table, ids) ->
                connection.createStatement().use { s ->
                    s.executeQuery("SELECT ${table}_id FROM $table").use { rows ->
                        generateSequence { if (rows.next()) rows.getLong(1) else null }.toList()
                    }
                }.filter { it !in ids }.map { "$table $it" }
            }
        }
        if (missing.isNotEmpty()) {
            throw ExportException(
                "rows of the previous release are missing from the files (switch them off instead of removing them): " +
                    missing.joinToString()
            )
        }
    }

    /** `created_at` of every row: midnight UTC of the release day in [contentVersion], so exports are repeatable. */
    private fun releaseTime(contentVersion: Long): Long {
        val text = contentVersion.toString()
        val day = runCatching { LocalDate.parse(text.take(8), DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull()
        if (text.length != 10 || day == null) throw ExportException("content version must be yyyyMMddNN, got $contentVersion")
        return day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }

    companion object {
        const val CONTENT_FILE_NAME = CONTENT_DATABASE_NAME
    }
}
