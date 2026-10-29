package com.madovygabor.maturimate.features.learning.data

import com.madovygabor.maturimate.core.database.ContentDatabaseSource
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.database.content.ContentDatabase
import com.madovygabor.maturimate.features.learning.data.mapper.toDomain
import com.madovygabor.maturimate.features.learning.domain.ContentRepository
import com.madovygabor.maturimate.features.learning.domain.LearningError
import com.madovygabor.maturimate.features.learning.domain.model.LearningUnit
import com.madovygabor.maturimate.features.learning.domain.model.Subject
import com.madovygabor.maturimate.features.learning.domain.model.Task
import com.madovygabor.maturimate.features.learning.domain.model.Topic
import com.madovygabor.maturimate.features.learning.domain.model.UnitTask
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/** SQLDelight implementation of [ContentRepository] over the read-only content database. */
class ContentRepositoryImpl(
    private val source: ContentDatabaseSource,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ContentRepository {

    override suspend fun subjects(): OperationResult<List<Subject>, LearningError> = read { db ->
        db.subjectQueries.selectAll().executeAsList().map { it.toDomain() }
    }

    override suspend fun rootTopics(subjectId: Long): OperationResult<List<Topic>, LearningError> = read { db ->
        db.topicQueries.selectRootsBySubject(subjectId).executeAsList().map { it.toDomain() }
    }

    override suspend fun childTopics(topicId: Long): OperationResult<List<Topic>, LearningError> = read { db ->
        db.topicQueries.selectChildren(topicId).executeAsList().map { it.toDomain() }
    }

    override suspend fun subtreeTopicIds(topicId: Long): OperationResult<List<Long>, LearningError> = read { db ->
        db.topicQueries.selectSubtreeIds(topicId).executeAsList().ifEmpty { null }
    }

    override suspend fun units(topicId: Long): OperationResult<List<LearningUnit>, LearningError> = read { db ->
        db.learningUnitQueries.selectPublishedByTopic(topicId).executeAsList().map { it.toDomain() }
    }

    override suspend fun unit(learningUnitId: Long): OperationResult<LearningUnit, LearningError> = read { db ->
        db.learningUnitQueries.selectById(learningUnitId).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun unitTasks(learningUnitId: Long): OperationResult<List<UnitTask>, LearningError> = read { db ->
        db.learningUnitQueries.selectUnitTasks(learningUnitId).executeAsList().map { it.toDomain() }
    }

    override suspend fun task(taskId: Long): OperationResult<Task, LearningError> = read { db ->
        val q = db.taskQueries
        q.selectById(taskId).executeAsOneOrNull()?.toDomain(
            options = q.selectOptions(taskId).executeAsList(),
            keys = q.selectKeys(taskId).executeAsList(),
            acceptedAnswers = q.selectAcceptedAnswers(taskId).executeAsList(),
            attachments = q.selectAttachments(taskId).executeAsList(),
        )
    }

    /** Runs a read on the IO dispatcher; a null result means the row does not exist. */
    private suspend fun <T : Any> read(block: (ContentDatabase) -> T?): OperationResult<T, LearningError> =
        withContext(dispatcher) {
            try {
                val db = source.get() ?: return@withContext OperationResult.Error(LearningError.NO_CONTENT)
                block(db)?.let { OperationResult.Success(it) } ?: OperationResult.Error(LearningError.NOT_FOUND)
            } catch (e: Exception) {
                OperationResult.Error(LearningError.DATABASE_ERROR)
            }
        }
}
