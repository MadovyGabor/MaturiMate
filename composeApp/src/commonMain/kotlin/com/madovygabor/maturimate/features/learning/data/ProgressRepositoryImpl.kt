package com.madovygabor.maturimate.features.learning.data

import com.madovygabor.maturimate.core.database.LocalUserProvider
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
 * The phone has one local user, given by [LocalUserProvider]: a guest id until sign-up,
 * then the Supabase account id.
 */
@OptIn(ExperimentalTime::class)
class ProgressRepositoryImpl(
    private val database: UserDatabase,
    private val localUser: LocalUserProvider,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ProgressRepository {

    private val progress get() = database.progressQueries
    private val activity get() = database.activityQueries
    private fun userId() = localUser.userId()

    override suspend fun startAttempt(
        type: AttemptType,
        topicId: Long?,
        learningUnitId: Long?,
        startedAt: Instant,
    ): OperationResult<Long, LearningError> = run {
        database.transactionWithResult {
            progress.insertAttempt(userId(), type.name, topicId, learningUnitId, startedAt.toEpochMilliseconds())
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
        progress.selectAttempts(userId()).executeAsList().map { it.toDomain() }
    }

    override suspend fun attemptsOfUnit(learningUnitId: Long): OperationResult<List<Attempt>, LearningError> = run {
        progress.selectAttemptsByUnit(userId(), learningUnitId).executeAsList().map { it.toDomain() }
    }

    override suspend fun unitProgress(): OperationResult<List<UnitProgress>, LearningError> = run {
        progress.selectUnitProgress(userId()).executeAsList().map { it.toDomain() }
    }

    override suspend fun saveUnitProgress(progress: UnitProgress): OperationResult<Unit, LearningError> = run {
        this.progress.upsertUnitProgress(progress.toRow(userId()))
    }

    override suspend fun taskProgress(taskId: Long): OperationResult<TaskProgress?, LearningError> = run {
        progress.selectTaskProgress(userId(), taskId).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun dueTasks(now: Instant): OperationResult<List<TaskProgress>, LearningError> = run {
        progress.selectDueTasks(userId(), now.toEpochMilliseconds()).executeAsList().map { it.toDomain() }
    }

    override suspend fun saveTaskProgress(progress: TaskProgress): OperationResult<Unit, LearningError> = run {
        this.progress.upsertTaskProgress(progress.toRow(userId()))
    }

    override suspend fun streak(): OperationResult<Streak?, LearningError> = run {
        activity.selectStreak(userId()).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun saveStreak(streak: Streak): OperationResult<Unit, LearningError> = run {
        activity.upsertStreak(streak.toRow(userId()))
    }

    override suspend fun addActivity(activity: DailyActivity): OperationResult<Unit, LearningError> = run {
        this.activity.addActivity(
            userId(),
            activity.date.toString(),
            activity.xpEarned.toLong(),
            activity.minutesStudied.toLong(),
            activity.tasksAnswered.toLong(),
        )
    }

    override suspend fun activity(from: LocalDate, to: LocalDate): OperationResult<List<DailyActivity>, LearningError> = run {
        activity.selectActivity(userId(), from.toString(), to.toString()).executeAsList().map { it.toDomain() }
    }

    override suspend fun totalXp(): OperationResult<Long, LearningError> = run {
        activity.selectTotalXp(userId()).executeAsOne()
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
}
