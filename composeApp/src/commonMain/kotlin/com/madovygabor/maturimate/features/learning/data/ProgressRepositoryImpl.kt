package com.madovygabor.maturimate.features.learning.data

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.learning.data.mapper.toDomain
import com.madovygabor.maturimate.features.learning.data.mapper.toRow
import com.madovygabor.maturimate.features.learning.domain.LearningError
import com.madovygabor.maturimate.features.learning.domain.ProgressRepository
import com.madovygabor.maturimate.features.learning.domain.model.Attempt
import com.madovygabor.maturimate.features.learning.domain.model.AttemptStatus
import com.madovygabor.maturimate.features.learning.domain.model.AttemptType
import com.madovygabor.maturimate.features.learning.domain.model.DailyActivity
import com.madovygabor.maturimate.features.learning.domain.model.Streak
import com.madovygabor.maturimate.features.learning.domain.model.TaskProgress
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgress
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * SQLDelight implementation of [ProgressRepository] over the user database.
 *
 * The phone has one local user. Its rows use [LOCAL_USER_ID] until accounts exist;
 * the backend assigns the real `app_user.user_id` at the first sync (Milestone 7).
 */
@OptIn(ExperimentalTime::class)
class ProgressRepositoryImpl(
    private val database: UserDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ProgressRepository {

    private val progress get() = database.progressQueries
    private val activity get() = database.activityQueries

    override suspend fun startAttempt(
        type: AttemptType,
        topicId: Long?,
        learningUnitId: Long?,
        startedAt: Instant,
    ): OperationResult<Long, LearningError> = run {
        database.transactionWithResult {
            progress.insertAttempt(LOCAL_USER_ID, type.name, topicId, learningUnitId, startedAt.toEpochMilliseconds())
            progress.lastInsertedId().executeAsOne()
        }
    }

    override suspend fun finishAttempt(
        attemptId: Long,
        status: AttemptStatus,
        finishedAt: Instant,
        earnedPoints: Int,
        maxPoints: Int,
    ): OperationResult<Unit, LearningError> = run {
        progress.finishAttempt(status.name, finishedAt.toEpochMilliseconds(), earnedPoints.toLong(), maxPoints.toLong(), attemptId)
    }

    override suspend fun attempts(): OperationResult<List<Attempt>, LearningError> = run {
        progress.selectAttempts(LOCAL_USER_ID).executeAsList().map { it.toDomain() }
    }

    override suspend fun attemptsOfUnit(learningUnitId: Long): OperationResult<List<Attempt>, LearningError> = run {
        progress.selectAttemptsByUnit(LOCAL_USER_ID, learningUnitId).executeAsList().map { it.toDomain() }
    }

    override suspend fun unitProgress(): OperationResult<List<UnitProgress>, LearningError> = run {
        progress.selectUnitProgress(LOCAL_USER_ID).executeAsList().map { it.toDomain() }
    }

    override suspend fun saveUnitProgress(progress: UnitProgress): OperationResult<Unit, LearningError> = run {
        this.progress.upsertUnitProgress(progress.toRow(LOCAL_USER_ID))
    }

    override suspend fun taskProgress(taskId: Long): OperationResult<TaskProgress?, LearningError> = run {
        progress.selectTaskProgress(LOCAL_USER_ID, taskId).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun dueTasks(now: Instant): OperationResult<List<TaskProgress>, LearningError> = run {
        progress.selectDueTasks(LOCAL_USER_ID, now.toEpochMilliseconds()).executeAsList().map { it.toDomain() }
    }

    override suspend fun saveTaskProgress(progress: TaskProgress): OperationResult<Unit, LearningError> = run {
        this.progress.upsertTaskProgress(progress.toRow(LOCAL_USER_ID))
    }

    override suspend fun streak(): OperationResult<Streak?, LearningError> = run {
        activity.selectStreak(LOCAL_USER_ID).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun saveStreak(streak: Streak): OperationResult<Unit, LearningError> = run {
        activity.upsertStreak(streak.toRow(LOCAL_USER_ID))
    }

    override suspend fun addActivity(activity: DailyActivity): OperationResult<Unit, LearningError> = run {
        this.activity.addActivity(
            LOCAL_USER_ID,
            activity.date.toString(),
            activity.xpEarned.toLong(),
            activity.minutesStudied.toLong(),
            activity.tasksAnswered.toLong(),
        )
    }

    override suspend fun activity(from: LocalDate, to: LocalDate): OperationResult<List<DailyActivity>, LearningError> = run {
        activity.selectActivity(LOCAL_USER_ID, from.toString(), to.toString()).executeAsList().map { it.toDomain() }
    }

    override suspend fun totalXp(): OperationResult<Long, LearningError> = run {
        activity.selectTotalXp(LOCAL_USER_ID).executeAsOne()
    }

    /** Runs a database call on the IO dispatcher and maps failures to [LearningError.DATABASE_ERROR]. */
    private suspend fun <T> run(block: () -> T): OperationResult<T, LearningError> =
        withContext(dispatcher) {
            try {
                OperationResult.Success(block())
            } catch (e: Exception) {
                OperationResult.Error(LearningError.DATABASE_ERROR)
            }
        }

    companion object {
        const val LOCAL_USER_ID = 1L
    }
}
