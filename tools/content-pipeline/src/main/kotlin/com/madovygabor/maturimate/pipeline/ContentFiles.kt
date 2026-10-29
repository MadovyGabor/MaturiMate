package com.madovygabor.maturimate.pipeline

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Id of a row owned by a task (option, accepted answer, attachment): derived from the task id and the
 * position in the file, so the importer and the exporter give the same ids. At most 99 per task.
 */
fun childId(taskId: Long, index: Int): Long = taskId * 100 + index + 1

// Shapes of the content source files. Field names follow the columns of dbServer/postgres/01_ddl.sql.

/** `task_formats.json` at the content root. */
@Serializable
data class TaskFormatFile(
    @SerialName("task_format_id") val taskFormatId: Long,
    val code: String,
    val name: String,
    @SerialName("answer_mode") val answerMode: String,
    val description: String? = null,
)

/** `tags.json` at the content root. */
@Serializable
data class TagFile(
    @SerialName("tag_id") val tagId: Long,
    val name: String,
    @SerialName("tag_type") val tagType: String = "CONCEPT",
)

/** `subject.json` in a subject folder. */
@Serializable
data class SubjectFile(
    @SerialName("subject_id") val subjectId: Long,
    val code: String,
    @SerialName("name_hu") val nameHu: String,
    @SerialName("name_sk") val nameSk: String,
    val track: String,
    val description: String? = null,
)

/** `topic.json` in a topic folder. The parent and the subject come from the folder nesting. */
@Serializable
data class TopicFile(
    @SerialName("topic_id") val topicId: Long,
    val title: String,
    val description: String? = null,
    @SerialName("order_index") val orderIndex: Int,
    @SerialName("is_published") val isPublished: Boolean = false,
)

/** Where a task or a learning unit comes from. Checked by the importer, not imported. */
@Serializable
data class Provenance(
    @SerialName("source_type") val sourceType: String? = null,
    @SerialName("source_note") val sourceNote: String? = null,
    @SerialName("reviewed_at") val reviewedAt: String? = null,
)

/** A `PRACTICE` learning unit: any `*.json` in a leaf topic folder other than `topic.json` and `tasks.json`. */
@Serializable
data class PracticeUnitFile(
    @SerialName("learning_unit_id") val learningUnitId: Long,
    val title: String,
    @SerialName("estimated_minutes") val estimatedMinutes: Int,
    @SerialName("round_size") val roundSize: Int = 5,
    @SerialName("xp_reward") val xpReward: Int = 10,
    @SerialName("sort_order") val sortOrder: Int,
    @SerialName("is_published") val isPublished: Boolean = false,
    /** Tasks in unit order. */
    val tasks: List<UnitTaskEntry>,
    @SerialName("source_type") val sourceType: String? = null,
    @SerialName("source_note") val sourceNote: String? = null,
    @SerialName("reviewed_at") val reviewedAt: String? = null,
)

@Serializable
data class UnitTaskEntry(
    @SerialName("task_id") val taskId: Long,
    @SerialName("is_core") val isCore: Boolean = true,
)

/** One entry of `tasks.json` in a leaf topic folder. */
@Serializable
data class TaskEntry(
    @SerialName("task_id") val taskId: Long,
    /** `task_format.code`, for example `MC_SINGLE`. */
    val format: String,
    @SerialName("question_text") val questionText: String,
    val explanation: String? = null,
    @SerialName("max_points") val maxPoints: Int = 1,
    @SerialName("answer_word_limit") val answerWordLimit: Int? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    val options: List<OptionEntry> = emptyList(),
    /** Correct option per answer slot (choice formats). */
    val key: List<KeyEntry> = emptyList(),
    /** Accepted texts per answer slot (typed formats). */
    @SerialName("accepted_answers") val acceptedAnswers: List<AcceptedAnswerEntry> = emptyList(),
    val attachments: List<AttachmentEntry> = emptyList(),
    /** Names from `tags.json`. */
    val tags: List<String> = emptyList(),
    @SerialName("source_type") val sourceType: String? = null,
    @SerialName("source_note") val sourceNote: String? = null,
    @SerialName("reviewed_at") val reviewedAt: String? = null,
)

@Serializable
data class OptionEntry(
    val label: String,
    val text: String,
    val role: String = "ANSWER",
)

/** Options are referenced by their label, prompts (MATCHING only) by the label of a `PROMPT` option. */
@Serializable
data class KeyEntry(
    val slot: Int = 1,
    val option: String,
    val prompt: String? = null,
)

@Serializable
data class AcceptedAnswerEntry(
    val slot: Int = 1,
    val text: String,
    @SerialName("case_sensitive") val caseSensitive: Boolean = false,
)

@Serializable
data class AttachmentEntry(
    @SerialName("file_url") val fileUrl: String,
    @SerialName("media_type") val mediaType: String,
    @SerialName("alt_text") val altText: String? = null,
)
