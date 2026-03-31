package com.madovygabor.maturimate.features.subjectsAndExams.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.database.Database
import com.madovygabor.maturimate.features.subjectsAndExams.data.DatabaseSeeder
import com.madovygabor.maturimate.features.subjectsAndExams.data.mapper.toDomain
import com.madovygabor.maturimate.features.subjectsAndExams.domain.model.Exam
import com.madovygabor.maturimate.features.subjectsAndExams.domain.model.ExamError
import com.madovygabor.maturimate.features.subjectsAndExams.domain.repository.ExamRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Implementation of [ExamRepository] that orchestrates data operations
 * between the SQLDelight database and the Domain layer.
 *
 * @property database The SQLDelight [Database] instance for persistence.
 * @property seeder Helper class to populate the database from JSON resources.
 */
class ExamRepositoryImpl(
    private val database: Database,
    private val seeder: DatabaseSeeder
) : ExamRepository {

    /**
     * Retrieves a reactive stream of all available exams from the database.
     * The list is automatically updated whenever the underlying database changes.
     *
     * @return A [Flow] containing a list of [Exam] domain models.
     */
    override fun getExams(): Flow<List<Exam>> {
        // Fetch raw entities as a reactive Flow from SQLDelight
        return database.examEntityQueries
            .selectAllExams()
            .asFlow()
            .mapToList(Dispatchers.IO)
            // Transform the list of entities into domain models
            .map { entities ->
                entities.map { it.toDomain() }
            }
    }

    override fun getExamsBySubject(subjectId: String): Flow<List<Exam>> {
        return database.examEntityQueries
            .getExamsBySubject(subjectId)
            .asFlow()
            .mapToList(Dispatchers.IO)
            .map { entities ->
                entities.map { it.toDomain() }
            }
    }

    /**
     * Fetches a detailed [Exam] object by its unique identifier,
     * including all associated tasks and content units.
     *
     * @param id The unique ID of the exam.
     * @return [OperationResult.Success] with the full exam or [OperationResult.Error] if not found or DB fails.
     */
    override suspend fun getExamById(id: String): OperationResult<Exam, ExamError> {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Fetch the main exam record (header data)
                val examEntity = database.examEntityQueries.selectExamById(id).executeAsOneOrNull()

                if (examEntity == null) {
                    return@withContext OperationResult.Error(ExamError.NOT_FOUND)
                }

                // 2. Fetch all related tasks for this specific exam
                val tasks = database.taskEntityQueries.getTasksForExam(id)
                    .executeAsList()
                    .map { it.toDomain() }

                // 3. Fetch all related content units (texts, audio info, etc.)
                val units = database.contentUnitEntityQueries.getContentUnitsForExam(id)
                    .executeAsList()
                    .map { it.toDomain() }

                // Combine the header data with the nested lists into the final Domain model
                OperationResult.Success(examEntity.toDomain(tasks, units))

            } catch (e: Exception) {
                // Wrap any low-level SQL errors into a domain-specific error
                OperationResult.Error(ExamError.DATABASE_ERROR)
            }
        }
    }

    /**
     * Synchronizes local database storage with a provided JSON content string.
     * This usually happens during onboarding or a manual update.
     *
     * @param jsonContent The raw JSON string containing exam, units, and tasks data.
     * @return [OperationResult.Success] if the seeding was successful.
     */
    override suspend fun syncExams(jsonContent: String): OperationResult<Unit, ExamError> {
        return withContext(Dispatchers.IO) {
            try {
                // Delegate the parsing and database insertion logic to the Seeder
                seeder.seedExam(jsonContent)
                OperationResult.Success(Unit)
            } catch (e: Exception) {
                // Typically indicates a mismatch between JSON structure and DTO models
                OperationResult.Error(ExamError.PARSE_ERROR)
            }
        }
    }
}