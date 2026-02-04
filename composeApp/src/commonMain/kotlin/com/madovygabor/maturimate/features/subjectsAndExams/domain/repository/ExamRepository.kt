package com.madovygabor.maturimate.features.subjectsAndExams.domain.repository

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.subjectsAndExams.domain.model.Exam
import com.madovygabor.maturimate.features.subjectsAndExams.domain.model.ExamError
import kotlinx.coroutines.flow.Flow

/**
 * Domain-layer repository interface for managing Exam data.
 * Defines the contract for fetching exams and synchronizing data from external sources.
 *
 * This abstraction allows the UI to remain independent of the underlying
 * data source (SQLDelight, JSON, or API).
 */
interface ExamRepository {

    /**
     * Provides an observable stream of all available exams.
     * Since this is a [Flow], it will automatically emit new lists whenever
     * the underlying database changes.
     *
     * Note: [OperationResult] is omitted here because errors in Flows are
     * typically handled at the collection site using Flow operators like .catch().
     *
     * @return A [Flow] emitting the current list of [Exam] domain models.
     */
    fun getExams(): Flow<List<Exam>>

    /**
     * Retrieves a specific exam by its unique identifier.
     * This is a one-shot fetch operation.
     *
     * @param id The unique string identifier of the exam.
     * @return An [OperationResult] containing the [Exam] on success
     * or a domain-specific [ExamError] on failure.
     */
    suspend fun getExamById(id: String): OperationResult<Exam, ExamError>

    /**
     * Synchronizes the local database with provided JSON content.
     * Typically used during onboarding or when importing new test materials.
     *
     * @param jsonContent The raw JSON string containing the exam and its tasks.
     * @return [OperationResult.Success] with [Unit] if the operation completed successfully.
     */
    suspend fun syncExams(jsonContent: String): OperationResult<Unit, ExamError>
}