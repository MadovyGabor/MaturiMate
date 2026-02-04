package com.madovygabor.maturimate.features.subjectsAndExams.domain.model

/**
 * Core domain model representing a complete Exam.
 * This class serves as the single source of truth for the UI layer,
 * containing metadata, tasks, and shared content units.
 *
 * @property tasks The list of questions associated with this exam.
 * @property contentUnits Shared resources (texts, audio) that tasks may refer to.
 */
data class Exam(
    val id: String,
    val subjectId: String,
    val year: Int,
    val session: String,
    val totalMinutes: Int,
    val vpuMinutes: Int,
    val taskCount: Int,
    val tasks: List<Task> = emptyList(),
    val contentUnits: List<ContentUnit> = emptyList()
)

/**
 * Represents a piece of shared material used by one or more tasks.
 * Examples include a reading passage, a listening transcript, or an audio file.
 */
data class ContentUnit(
    val id: String,
    val type: ContentUnitType, // Enum for TEXT, AUDIO, etc.
    val title: String,
    val author: String?,
    val contentHtml: String?,
    val mediaUrl: String?
)

/**
 * Represents a single question or exercise within an exam.
 *
 * @property metadataJson A raw JSON string containing type-specific data
 * (e.g., choice options or correct answers). Parsing happens in the UI layer
 * to keep the Domain layer clean of serialization logic.
 */
data class Task(
    val id: String,
    val orderIndex: Int,
    val type: TaskType, // Enum for CHOICE, INPUT, etc.
    val question: String,
    val points: Int,
    val imageUrl: String?,
    val metadataJson: String
)