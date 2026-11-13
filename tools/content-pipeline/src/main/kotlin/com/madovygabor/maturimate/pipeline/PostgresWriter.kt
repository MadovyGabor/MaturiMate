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
 * * Rows owned by a task (options, keys, accepted answers, attachments, tags), the task list of a
 *   practice unit and the pages of a lesson are replaced. The user database never references them.
 * * Tasks, learning units and topics that disappeared from the files are deactivated, never deleted,
 *   because the user database may still reference them.
 * * The rows are collected first and sent in chunks of [CHUNK] rows, one statement per chunk. A row per
 *   statement made the import wait for one network round trip per row (about 25 000 of them).
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
        deleteWhereIn("page_block", "learning_unit_id", unitIds)
        deleteWhereIn("unit_page", "learning_unit_id", unitIds)
        listOf("task_tag", "task_key", "task_option", "task_accepted_answer", "task_attachment")
            .forEach { deleteWhereIn(it, "task_id", taskIds) }

        val t = Tables()
        content.taskFormats.forEach { t.taskFormat.add(it.taskFormatId, it.code, it.name, it.answerMode, it.description) }
        content.tags.forEach { t.tag.add(it.tagId, it.name, it.tagType) }
        content.subjects.forEach {
            t.subject.add(it.subjectId, it.code, it.nameHu, it.nameSk, it.track, it.description)
        }
        // parents come before their children (the loader reads the tree top-down)
        content.topics.forEach {
            t.topic.add(it.file.topicId, it.subjectId, it.parentTopicId, it.file.title, it.file.description,
                it.file.orderIndex, it.file.isPublished)
        }
        content.summaries.forEach {
            t.learningUnit.add(it.learningUnitId, it.topicId, "SUMMARY", it.title, null,
                it.estimatedMinutes, 5, it.xpReward, it.sortOrder, it.isPublished)
        }
        content.practices.forEach { p ->
            val u = p.file
            t.learningUnit.add(u.learningUnitId, p.topicId, "PRACTICE", u.title, u.practiceKind,
                u.estimatedMinutes, u.roundSize, u.xpReward, u.sortOrder, u.isPublished)
        }

        content.summaries.forEach { unit ->
            unit.pages.forEachIndexed { pageIndex, page ->
                t.unitPage.add(unit.learningUnitId, pageIndex + 1, page.title)
                page.blocks.forEachIndexed { blockIndex, block ->
                    t.pageBlock.add(unit.learningUnitId, pageIndex + 1, blockIndex + 1, block.type, block.text)
                }
            }
        }

        val formatIds = content.taskFormats.associate { it.code to it.taskFormatId }
        val tagIds = content.tags.associate { it.name to it.tagId }
        content.tasks.forEach { bufferTask(it, formatIds.getValue(it.entry.format), tagIds, t) }

        content.practices.forEach { p ->
            p.file.tasks.forEachIndexed { index, entry ->
                t.unitTask.add(p.file.learningUnitId, entry.taskId, index + 1, entry.isCore)
            }
        }

        t.inOrder.forEach { flush(it) }

        val deactivated = mapOf(
            "task" to deactivateMissing("task", "task_id", "is_active", taskIds),
            "learning_unit" to deactivateMissing("learning_unit", "learning_unit_id", "is_published", unitIds),
            "topic" to deactivateMissing("topic", "topic_id", "is_published", content.topics.map { it.file.topicId }),
        )

        resetIdentitySequences()
        return ImportReport(inserted.toMap(), updated.toMap(), deactivated)
    }

    private fun bufferTask(task: LoadedTask, formatId: Long, tagIds: Map<String, Long>, t: Tables) {
        val e = task.entry
        t.task.add(e.taskId, task.topicId, formatId, e.questionText, e.explanation, e.maxPoints,
            e.answerWordLimit, e.isActive)

        // child ids are derived from the task id and the position in the file
        val optionIds = mutableMapOf<Pair<String, String>, Long>()
        e.options.forEachIndexed { index, o ->
            val id = childId(e.taskId, index)
            optionIds[o.role to o.label] = id
            t.taskOption.add(id, e.taskId, o.role, o.label, o.text, o.why)
        }
        e.key.forEach { k ->
            t.taskKey.add(e.taskId, k.slot,
                k.prompt?.let { optionIds.getValue("PROMPT" to it) },
                optionIds.getValue("ANSWER" to k.option))
        }
        e.acceptedAnswers.forEachIndexed { index, a ->
            t.acceptedAnswer.add(childId(e.taskId, index), e.taskId, a.slot, a.text, a.caseSensitive)
        }
        e.attachments.forEachIndexed { index, a ->
            t.attachment.add(childId(e.taskId, index), e.taskId, a.fileUrl, a.mediaType, a.altText)
        }
        e.tags.forEach { t.taskTag.add(e.taskId, tagIds.getValue(it)) }
    }

    /** Sends the rows of one table in chunks: an upsert when it has an id column, a plain insert when not. */
    private fun flush(table: BufferedTable) {
        table.rows.chunked(CHUNK).forEach { chunk ->
            val values = chunk.flatten()
            if (table.idColumn == null) {
                connection.prepareStatement(insertSql(table, chunk.size)).use { statement ->
                    statement.bind(values)
                    statement.executeUpdate()
                }
            } else {
                connection.prepareStatement(upsertSql(table, chunk.size)).use { statement ->
                    statement.bind(values)
                    // only the rows that changed come back, so the counts are the same as one statement per row
                    statement.executeQuery().use { resultRows ->
                        while (resultRows.next()) {
                            val counter = if (resultRows.getBoolean("inserted")) inserted else updated
                            counter.merge(table.name, 1, Int::plus)
                        }
                    }
                }
            }
        }
    }

    private fun insertSql(table: BufferedTable, rowCount: Int): String =
        "INSERT INTO ${table.name} (${table.columns.joinToString()}) VALUES ${placeholders(rowCount, table.columns.size)}"

    private fun upsertSql(table: BufferedTable, rowCount: Int): String {
        val id = table.idColumn ?: error("${table.name} has no id column")
        val others = table.columns.filter { it != id }
        return "INSERT INTO ${table.name} (${table.columns.joinToString()}) " +
            "VALUES ${placeholders(rowCount, table.columns.size)} " +
            "ON CONFLICT ($id) DO UPDATE SET ${others.joinToString { "$it = EXCLUDED.$it" }} " +
            "WHERE (${others.joinToString { "${table.name}.$it" }}) IS DISTINCT FROM (${others.joinToString { "EXCLUDED.$it" }}) " +
            "RETURNING (xmax = 0) AS inserted"
    }

    private fun placeholders(rowCount: Int, columnCount: Int): String =
        (1..rowCount).joinToString(", ") { "(" + (1..columnCount).joinToString { "?" } + ")" }

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

    /** The rows of one table, collected before they are sent. [idColumn] is null for a table that is only inserted. */
    private class BufferedTable(val name: String, val idColumn: String?, vararg names: String) {
        val columns: List<String> = names.toList()
        val rows = mutableListOf<List<Any?>>()

        fun add(vararg values: Any?) {
            require(values.size == columns.size) { "$name: ${values.size} values for ${columns.size} columns" }
            rows += values.toList()
        }
    }

    /** All tables of one import. The order of [inOrder] is the order of the foreign keys. */
    private class Tables {
        val taskFormat = BufferedTable("task_format", "task_format_id", "task_format_id", "code", "name", "answer_mode", "description")
        val tag = BufferedTable("tag", "tag_id", "tag_id", "name", "tag_type")
        val subject = BufferedTable("subject", "subject_id", "subject_id", "code", "name_hu", "name_sk", "track", "description")
        val topic = BufferedTable("topic", "topic_id", "topic_id", "subject_id", "parent_topic_id", "title", "description",
            "order_index", "is_published")
        val learningUnit = BufferedTable("learning_unit", "learning_unit_id", "learning_unit_id", "topic_id", "unit_type",
            "title", "practice_kind", "estimated_minutes", "round_size", "xp_reward", "sort_order", "is_published")
        val task = BufferedTable("task", "task_id", "task_id", "topic_id", "task_format_id", "question_text", "explanation",
            "max_points", "answer_word_limit", "is_active")
        val taskOption = BufferedTable("task_option", null, "option_id", "task_id", "option_role", "option_label", "option_text", "why")
        val taskKey = BufferedTable("task_key", null, "task_id", "answer_slot", "prompt_option_id", "option_id")
        val acceptedAnswer = BufferedTable("task_accepted_answer", null, "accepted_answer_id", "task_id", "answer_slot",
            "answer_text", "case_sensitive")
        val attachment = BufferedTable("task_attachment", null, "attachment_id", "task_id", "file_url", "media_type", "alt_text")
        val taskTag = BufferedTable("task_tag", null, "task_id", "tag_id")
        val unitPage = BufferedTable("unit_page", null, "learning_unit_id", "page_no", "title")
        val pageBlock = BufferedTable("page_block", null, "learning_unit_id", "page_no", "block_no", "block_type", "body_text")
        val unitTask = BufferedTable("unit_task", null, "learning_unit_id", "task_id", "sort_order", "is_core")

        val inOrder: List<BufferedTable>
            get() = listOf(
                taskFormat, tag, subject, topic, learningUnit, task, taskOption, taskKey,
                acceptedAnswer, attachment, taskTag, unitPage, pageBlock, unitTask,
            )
    }

    private companion object {
        /** Rows per statement. Far below the 65 535 bind parameters of PostgreSQL. */
        const val CHUNK = 500

        val IDENTITY_COLUMNS = listOf(
            "task_format" to "task_format_id", "tag" to "tag_id", "subject" to "subject_id", "topic" to "topic_id",
            "task" to "task_id", "task_option" to "option_id", "task_accepted_answer" to "accepted_answer_id",
            "task_attachment" to "attachment_id", "learning_unit" to "learning_unit_id",
        )
    }
}
