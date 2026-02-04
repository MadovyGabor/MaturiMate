package com.madovygabor.maturimate.features.subjectsAndExams.data.mapper

import com.madovygabor.maturimate.database.ContentUnitEntity
import com.madovygabor.maturimate.database.ExamEntity
import com.madovygabor.maturimate.database.TaskEntity
import com.madovygabor.maturimate.features.subjectsAndExams.domain.model.ContentUnit
import com.madovygabor.maturimate.features.subjectsAndExams.domain.model.ContentUnitType
import com.madovygabor.maturimate.features.subjectsAndExams.domain.model.Exam
import com.madovygabor.maturimate.features.subjectsAndExams.domain.model.Task
import com.madovygabor.maturimate.features.subjectsAndExams.domain.model.TaskType

// --- Type Conversion Helpers ---

/**
 * Safely converts a raw String representation into a [TaskType] enum.
 * Uses a try-catch block to prevent crashes if the database contains
 * an unsupported or legacy task type string.
 *
 * @return The matching [TaskType] or [TaskType.UNKNOWN] as a fallback.
 */
fun String.toTaskType(): TaskType {
    return try {
        TaskType.valueOf(this)
    } catch (e: Exception) {
        TaskType.UNKNOWN
    }
}

/**
 * Safely converts a raw String representation into a [ContentUnitType] enum.
 * Useful for handling various media types like TEXT, AUDIO, or GAP_FILL.
 *
 * @return The matching [ContentUnitType] or [ContentUnitType.UNKNOWN] as a fallback.
 */
fun String.toContentUnitType(): ContentUnitType {
    return try {
        ContentUnitType.valueOf(this)
    } catch (e: Exception) {
        ContentUnitType.UNKNOWN
    }
}

// --- Main Mapping Functions (Entity -> Domain) ---

/**
 * Maps a database [ExamEntity] to a clean [Exam] domain model.
 * Since SQLDelight entities use Long for numbers, we perform the
 * conversion to Int here for the domain layer.
 *
 * @param tasks Pre-fetched list of domain [Task] models associated with this exam.
 * @param contentUnits Pre-fetched list of domain [ContentUnit] models for this exam.
 */
fun ExamEntity.toDomain(
    tasks: List<Task> = emptyList(),
    contentUnits: List<ContentUnit> = emptyList()
): Exam {
    return Exam(
        id = id,
        subjectId = subjectId,
        year = year.toInt(),
        session = session,
        totalMinutes = totalMinutes.toInt(),
        vpuMinutes = vpuMinutes.toInt(),
        taskCount = taskCount.toInt(),
        tasks = tasks,
        contentUnits = contentUnits
    )
}

/**
 * Maps a [TaskEntity] from the persistence layer to a [Task] domain model.
 * Handles the mapping of internal metadata (JSON string) and type conversion.
 */
fun TaskEntity.toDomain(): Task {
    return Task(
        id = id,
        orderIndex = orderIndex.toInt(),
        type = type.toTaskType(), // Convert raw string to safe Enum
        question = question,
        points = points.toInt(),
        imageUrl = imageUrl,
        metadataJson = metadataJson
    )
}

/**
 * Maps a [ContentUnitEntity] from the persistence layer to a [ContentUnit] domain model.
 * This object represents a text block, audio file, or other study material.
 */
fun ContentUnitEntity.toDomain(): ContentUnit {
    return ContentUnit(
        id = id,
        type = type.toContentUnitType(),
        title = title,
        author = author,
        contentHtml = contentHtml,
        mediaUrl = mediaUrl
    )
}