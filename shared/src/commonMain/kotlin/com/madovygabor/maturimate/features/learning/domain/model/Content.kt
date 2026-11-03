package com.madovygabor.maturimate.features.learning.domain.model

/**
 * Domain models of the read-only content (subjects, topic tree, learning units, tasks).
 * They mirror the content tables of the database design without depending on the database code.
 */

enum class Track { HU, SK, BOTH }

data class Subject(
    val id: Long,
    val code: String,
    val nameHu: String,
    val nameSk: String,
    val track: Track,
    val description: String?,
)

/** A node of the curriculum tree. Leaf topics hold the learning units and tasks. */
data class Topic(
    val id: Long,
    val subjectId: Long?,
    val parentTopicId: Long?,
    val title: String,
    val description: String?,
    val orderIndex: Int,
)

/** The kind of a practice unit: a short test after part of a lesson, the test of a topic, or a test over a parent topic. */
enum class PracticeKind { MINI, TOPIC, FINAL }

/** A block of a lesson page: a paragraph or a box. The label of a box is an app string, not part of the content. */
enum class BlockType { PARAGRAPH, EXAMPLE, TIP, NOTE, WARNING }

/** Text in the app's format (`**bold**`, `*italic*`; paragraphs of a box are separated by a blank line). */
data class LessonBlock(val type: BlockType, val text: String)

/** One page of a lesson, read without scrolling. [title] is the entry in the table of contents (null for an untitled page). */
data class LessonPage(val title: String?, val blocks: List<LessonBlock>)

/** One step of a topic: a lesson to read or a test (a set of practice tasks). */
sealed interface LearningUnit {
    val id: Long
    val topicId: Long
    val title: String
    val estimatedMinutes: Int
    val xpReward: Int
    val sortOrder: Int

    data class Summary(
        override val id: Long,
        override val topicId: Long,
        override val title: String,
        val pages: List<LessonPage>,
        override val estimatedMinutes: Int,
        override val xpReward: Int,
        override val sortOrder: Int,
    ) : LearningUnit

    data class Practice(
        override val id: Long,
        override val topicId: Long,
        override val title: String,
        override val estimatedMinutes: Int,
        val kind: PracticeKind,
        /** How many tasks one round asks. */
        val roundSize: Int,
        override val xpReward: Int,
        override val sortOrder: Int,
    ) : LearningUnit
}

/** A task of a practice unit, in unit order. Reserve tasks (`isCore = false`) fill rounds and extra practice. */
data class UnitTask(
    val taskId: Long,
    val sortOrder: Int,
    val isCore: Boolean,
    val isActive: Boolean,
)

enum class AnswerMode { CHOICE, TEXT }

/** Task formats of the design; every one is graded automatically. */
enum class TaskFormat(val answerMode: AnswerMode) {
    MC_SINGLE(AnswerMode.CHOICE),
    TRUE_FALSE(AnswerMode.CHOICE),
    MATCHING(AnswerMode.CHOICE),
    ORDERING(AnswerMode.CHOICE),
    GAP_CHOICE(AnswerMode.CHOICE),
    GAP_FILL(AnswerMode.TEXT),
    SHORT_ANSWER(AnswerMode.TEXT),
}

enum class OptionRole { ANSWER, PROMPT }

data class TaskOption(
    val label: String,
    val text: String,
    val role: OptionRole,
    /** Why this option is wrong; shown only to a student who picked it. */
    val why: String? = null,
)

/** The correct option of one answer slot; [promptLabel] is the left-hand item of a matching pair. */
data class KeySlot(
    val slot: Int,
    val optionLabel: String,
    val promptLabel: String?,
)

data class AcceptedAnswer(
    val slot: Int,
    val text: String,
    val caseSensitive: Boolean,
)

enum class MediaType { IMAGE, DIAGRAM, AUDIO }

data class Attachment(
    val fileUrl: String,
    val mediaType: MediaType,
    val altText: String?,
)

data class Task(
    val id: Long,
    val topicId: Long,
    val format: TaskFormat,
    /** Text in the app's format; gap formats contain `[[n]]` markers. */
    val questionText: String,
    val explanation: String?,
    val maxPoints: Int,
    val answerWordLimit: Int?,
    val isActive: Boolean,
    val options: List<TaskOption>,
    val key: List<KeySlot>,
    val acceptedAnswers: List<AcceptedAnswer>,
    val attachments: List<Attachment>,
)
