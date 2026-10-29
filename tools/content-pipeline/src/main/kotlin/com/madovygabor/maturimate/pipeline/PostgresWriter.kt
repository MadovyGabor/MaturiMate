package com.madovygabor.maturimate.pipeline

import java.sql.Connection
import java.sql.PreparedStatement

/** What one import changed, per table. */
data class ImportReport(
    val inserted: Map<String, Int>,
    val updated: Map<String, Int>,
    val deactivated: Map<String, Int>,
) {
    override fun toString(): String = buildString {
        if (inserted.isEmpty() && updated.isEmpty() && deactivated.values.all { it == 0 }) {
            appendLine("  nothing changed")
            return@buildString
        }
        val tables = (inserted.keys + updated.keys).distinct()
        tables.forEach { appendLine("  %-22s %4d new, %4d updated".format(it, inserted[it] ?: 0, updated[it] ?: 0)) }
        deactivated.filterValues { it > 0 }.forEach { (table, n) -> appendLine("  %-22s %4d deactivated".format(table, n)) }
    }
}

/**
 * Writes a validated [ContentSet] into PostgreSQL in one transaction.
 *
 * * Rows are upserted by the ids written in the content files, so running the import twice changes nothing.
 * * Rows owned by a task (options, keys, accepted answers, attachments, tags) and the task list of a
 *   practice unit are replaced. The user database never references them.
 * * Tasks, learning units and topics that disappeared from the files are deactivated, never deleted,
 *   because the user database may still reference them.
 */
class PostgresWriter(private val connection: Connection) {

    private val inserted = mutableMapOf<String, Int>()
    private val updated = mutableMapOf<String, Int>()

    fun write(content: ContentSet): ImportReport {
        connection.autoCommit = false
        try {
            val report = writeAll(content)
            connection.commit()
            return report
        } catch (e: Exception) {
            connection.rollback()
            throw e
        }
    }

    private fun writeAll(content: ContentSet): ImportReport {
        val taskIds = content.tasks.map { it.entry.taskId }
        val unitIds = content.summaries.map { it.learningUnitId } + content.practices.map { it.file.learningUnitId }

        // Owned rows go first, so a task can change its format and a unit its type (both are checked by triggers)
        deleteWhereIn("unit_task", "learning_unit_id", unitIds)
        listOf("task_tag", "task_key", "task_option", "task_accepted_answer", "task_attachment")
            .forEach { deleteWhereIn(it, "task_id", taskIds) }

        content.taskFormats.forEach {
            upsert("task_format", "task_format_id", it.taskFormatId,
                "code" to it.code, "name" to it.name, "answer_mode" to it.answerMode, "description" to it.description)
        }
        content.tags.forEach {
            upsert("tag", "tag_id", it.tagId, "name" to it.name, "tag_type" to it.tagType)
        }
        content.subjects.forEach {
            upsert("subject", "subject_id", it.subjectId, "code" to it.code, "name_hu" to it.nameHu,
                "name_sk" to it.nameSk, "track" to it.track, "description" to it.description)
        }
        // parents come before their children (the loader reads the tree top-down)
        content.topics.forEach {
            upsert("topic", "topic_id", it.file.topicId, "subject_id" to it.subjectId,
                "parent_topic_id" to it.parentTopicId, "title" to it.file.title, "description" to it.file.description,
                "order_index" to it.file.orderIndex, "is_published" to it.file.isPublished)
        }
        content.summaries.forEach {
            upsert("learning_unit", "learning_unit_id", it.learningUnitId, "topic_id" to it.topicId,
                "unit_type" to "SUMMARY", "title" to it.title, "body_text" to it.bodyText,
                "estimated_minutes" to it.estimatedMinutes, "round_size" to 5, "xp_reward" to it.xpReward,
                "sort_order" to it.sortOrder, "is_published" to it.isPublished)
        }
        content.practices.forEach { p ->
            val u = p.file
            upsert("learning_unit", "learning_unit_id", u.learningUnitId, "topic_id" to p.topicId,
                "unit_type" to "PRACTICE", "title" to u.title, "body_text" to null,
                "estimated_minutes" to u.estimatedMinutes, "round_size" to u.roundSize, "xp_reward" to u.xpReward,
                "sort_order" to u.sortOrder, "is_published" to u.isPublished)
        }

        val formatIds = content.taskFormats.associate { it.code to it.taskFormatId }
        val tagIds = content.tags.associate { it.name to it.tagId }
        content.tasks.forEach { writeTask(it, formatIds.getValue(it.entry.format), tagIds) }

        content.practices.forEach { p ->
            p.file.tasks.forEachIndexed { index, entry ->
                insert("unit_task", "learning_unit_id" to p.file.learningUnitId, "task_id" to entry.taskId,
                    "sort_order" to index + 1, "is_core" to entry.isCore)
            }
        }

        val deactivated = mapOf(
            "task" to deactivateMissing("task", "task_id", "is_active", taskIds),
            "learning_unit" to deactivateMissing("learning_unit", "learning_unit_id", "is_published", unitIds),
            "topic" to deactivateMissing("topic", "topic_id", "is_published", content.topics.map { it.file.topicId }),
        )

        resetIdentitySequences()
        return ImportReport(inserted.toMap(), updated.toMap(), deactivated)
    }

    private fun writeTask(task: LoadedTask, formatId: Long, tagIds: Map<String, Long>) {
        val t = task.entry
        upsert("task", "task_id", t.taskId, "topic_id" to task.topicId, "task_format_id" to formatId,
            "question_text" to t.questionText, "explanation" to t.explanation, "max_points" to t.maxPoints,
            "answer_word_limit" to t.answerWordLimit, "is_active" to t.isActive)

        // child ids are derived from the task id and the position in the file
        val optionIds = mutableMapOf<Pair<String, String>, Long>()
        t.options.forEachIndexed { index, o ->
            val id = childId(t.taskId, index)
            optionIds[o.role to o.label] = id
            insert("task_option", "option_id" to id, "task_id" to t.taskId, "option_role" to o.role,
                "option_label" to o.label, "option_text" to o.text)
        }
        t.key.forEach { k ->
            insert("task_key", "task_id" to t.taskId, "answer_slot" to k.slot,
                "prompt_option_id" to k.prompt?.let { optionIds.getValue("PROMPT" to it) },
                "option_id" to optionIds.getValue("ANSWER" to k.option))
        }
        t.acceptedAnswers.forEachIndexed { index, a ->
            insert("task_accepted_answer", "accepted_answer_id" to childId(t.taskId, index), "task_id" to t.taskId,
                "answer_slot" to a.slot, "answer_text" to a.text, "case_sensitive" to a.caseSensitive)
        }
        t.attachments.forEachIndexed { index, a ->
            insert("task_attachment", "attachment_id" to childId(t.taskId, index), "task_id" to t.taskId,
                "file_url" to a.fileUrl, "media_type" to a.mediaType, "alt_text" to a.altText)
        }
        t.tags.forEach { insert("task_tag", "task_id" to t.taskId, "tag_id" to tagIds.getValue(it)) }
    }

    private fun upsert(table: String, idColumn: String, id: Long, vararg columns: Pair<String, Any?>) {
        val names = listOf(idColumn) + columns.map { it.first }
        val sql = """
            INSERT INTO $table (${names.joinToString()}) VALUES (${names.joinToString { "?" }})
            ON CONFLICT ($idColumn) DO UPDATE SET ${columns.joinToString { "${it.first} = EXCLUDED.${it.first}" }}
            WHERE (${columns.joinToString { "$table.${it.first}" }}) IS DISTINCT FROM (${columns.joinToString { "EXCLUDED.${it.first}" }})
            RETURNING (xmax = 0) AS inserted
        """.trimIndent()
        connection.prepareStatement(sql).use { statement ->
            statement.bind(listOf(id) + columns.map { it.second })
            statement.executeQuery().use { rows ->
                // no row: the existing row was already equal
                if (rows.next()) {
                    val counter = if (rows.getBoolean("inserted")) inserted else updated
                    counter.merge(table, 1, Int::plus)
                }
            }
        }
    }

    private fun insert(table: String, vararg columns: Pair<String, Any?>) {
        val sql = "INSERT INTO $table (${columns.joinToString { it.first }}) VALUES (${columns.joinToString { "?" }})"
        connection.prepareStatement(sql).use {
            it.bind(columns.map { c -> c.second })
            it.executeUpdate()
        }
    }

    private fun deleteWhereIn(table: String, column: String, ids: List<Long>) {
        connection.prepareStatement("DELETE FROM $table WHERE $column = ANY (?)").use {
            it.setArray(1, connection.createArrayOf("bigint", ids.toTypedArray()))
            it.executeUpdate()
        }
    }

    private fun deactivateMissing(table: String, idColumn: String, flag: String, keep: List<Long>): Int =
        connection.prepareStatement("UPDATE $table SET $flag = false WHERE $flag AND $idColumn <> ALL (?)").use {
            it.setArray(1, connection.createArrayOf("bigint", keep.toTypedArray()))
            it.executeUpdate()
        }

    /** Ids were inserted explicitly, so the identity sequences must continue after the highest id. */
    private fun resetIdentitySequences() {
        IDENTITY_COLUMNS.forEach { (table, column) ->
            connection.createStatement().use {
                it.execute(
                    "SELECT setval(pg_get_serial_sequence('$table', '$column'), " +
                        "COALESCE((SELECT MAX($column) FROM $table), 0) + 1, false)"
                )
            }
        }
    }

    private fun PreparedStatement.bind(values: List<Any?>) {
        values.forEachIndexed { index, value ->
            when (value) {
                null -> setObject(index + 1, null)
                is Boolean -> setBoolean(index + 1, value)
                is Int -> setInt(index + 1, value)
                is Long -> setLong(index + 1, value)
                else -> setString(index + 1, value.toString())
            }
        }
    }

    private companion object {
        val IDENTITY_COLUMNS = listOf(
            "task_format" to "task_format_id", "tag" to "tag_id", "subject" to "subject_id", "topic" to "topic_id",
            "task" to "task_id", "task_option" to "option_id", "task_accepted_answer" to "accepted_answer_id",
            "task_attachment" to "attachment_id", "learning_unit" to "learning_unit_id",
        )
    }
}
