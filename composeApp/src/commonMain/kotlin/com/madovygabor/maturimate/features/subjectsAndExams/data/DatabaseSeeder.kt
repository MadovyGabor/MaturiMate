package com.madovygabor.maturimate.features.subjectsAndExams.data

import com.madovygabor.maturimate.database.Database
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Service class responsible for populating the database with exam data from JSON resources.
 * It ensures data integrity by using database transactions during the seeding process.
 */
class DatabaseSeeder(
    private val database: Database
) {

    /**
     * JSON parser configuration for [kotlinx.serialization].
     * - [ignoreUnknownKeys]: Ensures the app doesn't crash if the JSON contains extra fields.
     * - [coerceInputValues]: Attempts to fix type mismatches in the input JSON.
     */
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /**
     * Parses a raw JSON string and seeds the database with Exam, ContentUnit, and Task entities.
     * The entire process is wrapped in a transaction to ensure atomicity (all or nothing).
     *
     * @param jsonContent The raw string content of the exam JSON file.
     * @throws Exception if JSON parsing or database insertion fails.
     */
    fun seedExam(jsonContent: String) {
        // 1. Parse the JSON string into Data Transfer Objects (DTOs)
        val dto = json.decodeFromString<ExamImportDto>(jsonContent)
        val meta = dto.metadata

        // 2. Start a database transaction to ensure atomicity
        database.transaction {

            // A) Insert Exam metadata
            database.examEntityQueries.insertExam(
                id = meta.id,
                subjectId = meta.subjectId,
                year = meta.year.toLong(),
                session = meta.session,
                totalMinutes = meta.totalMinutes.toLong(),
                vpuMinutes = meta.vpuMinutes.toLong(),
                taskCount = meta.taskCount.toLong()
            )

            // B) Insert Content Units (texts, audio info, etc.)
            dto.contentUnits.forEach { unitDto ->
                database.contentUnitEntityQueries.insertContentUnit(
                    id = unitDto.id,
                    examId = meta.id,
                    type = unitDto.type,
                    title = unitDto.title,
                    author = unitDto.author,
                    contentHtml = unitDto.contentHtml,
                    mediaUrl = unitDto.mediaUrl,
                    description = null,
                    sharedOptionsJson = null // Future-proofing for shared selection options
                )
            }

            // C) Insert Tasks and link them to their parent units
            dto.tasks.forEach { taskDto ->
                // Determine the parent unit ID by checking relatedTaskIds lists in the JSON
                val parentUnitId = dto.contentUnits.find {
                    it.relatedTaskIds.contains(taskDto.id)
                }?.id

                // Serialize task-specific metadata (options, answers) back to a JSON string for DB storage
                val metadataString = json.encodeToString(JsonElement.serializer(), taskDto.metadata)

                database.taskEntityQueries.insertTask(
                    id = taskDto.id,
                    examId = meta.id,
                    contentUnitId = parentUnitId, // Linkage happens here via foreign key logic
                    orderIndex = taskDto.orderIndex.toLong(),
                    type = taskDto.type,
                    question = taskDto.question,
                    points = taskDto.points.toLong(),
                    imageUrl = taskDto.imageUrl,
                    metadataJson = metadataString
                )
            }
        }
    }
}