package com.madovygabor.maturimate.features.subjectsAndExams.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Root Data Transfer Object for exam import.
 * Maps the high-level structure of the exam JSON files.
 *
 * @property metadata Basic information about the exam (year, subject, etc.).
 * @property contentUnits Optional list of shared resources like texts or audio data.
 * @property tasks The list of actual exam questions.
 */
@Serializable
data class ExamImportDto(
    val metadata: ExamMetadataDto,
    val contentUnits: List<ContentUnitImportDto> = emptyList(),
    val tasks: List<TaskImportDto>
)

/**
 * Metadata information for the exam.
 * Used for identifying and filtering exams in the database.
 */
@Serializable
data class ExamMetadataDto(
    val id: String,
    val subjectId: String,
    val year: Int,
    val session: String,
    val totalMinutes: Int,
    val vpuMinutes: Int,
    val taskCount: Int
)

/**
 * Represents a shared content unit (e.g., a reading passage or listening transcript).
 * Multiple tasks can refer to a single [ContentUnit] via [relatedTaskIds].
 */
@Serializable
data class ContentUnitImportDto(
    val id: String,
    val type: String, // e.g., "TEXT", "AUDIO", "GAP_FILL"
    val title: String,
    val author: String? = null,
    val contentHtml: String? = null,
    val mediaUrl: String? = null,
    val relatedTaskIds: List<String> = emptyList()
)

/**
 * Represents an individual exam task/question.
 *
 * @property metadata A dynamic [JsonElement] that stores type-specific data.
 * This allows the same DTO to handle different structures for Math, English, or Language tasks.
 */
@Serializable
data class TaskImportDto(
    val id: String,
    val orderIndex: Int,
    val type: String, // e.g., "CHOICE", "INPUT"
    val question: String,
    val points: Int = 1,
    val imageUrl: String? = null,
    val metadata: JsonElement
)