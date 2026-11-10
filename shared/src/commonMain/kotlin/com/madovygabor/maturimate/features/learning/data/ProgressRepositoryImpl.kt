package com.madovygabor.maturimate.features.learning.data

import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.learning.data.mapper.toDomain
import com.madovygabor.maturimate.features.learning.data.mapper.toRow
import com.madovygabor.maturimate.features.learning.domain.LearningError
import com.madovygabor.maturimate.features.learning.domain.ProgressRepository
import com.madovygabor.maturimate.features.learning.domain.model.Attempt
import com.madovygabor.maturimate.features.learning.domain.model.AttemptAnswer
import com.madovygabor.maturimate.features.learning.domain.model.AttemptStatus
import com.madovygabor.maturimate.features.learning.domain.model.AttemptType
import com.madovygabor.maturimate.features.learning.domain.model.DailyActivity
import com.madovygabor.maturimate.features.learning.domain.model.Streak
import com.madovygabor.maturimate.features.learning.domain.model.TaskProgress
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import com.madovygabor.maturimate.features.learning.domain.model.AnsweredTask

/**
 * SQLDelight implementation of [ProgressRepository] over the user database.
 *
 * The phone has one local user, given by [LocalUserProvider]: a guest id until sign-up,
 * then the Supabase account id.
 */
@OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
class ProgressRepositoryImpl(
    private val database: UserDatabase,
    private val localUser: LocalUserProvider,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val newAttemptId: () -> String = { Uuid.random().toString() },
    private val now: () -> Instant = { Clock.System.now() },
) : ProgressRepository {

    private val progress get() = database.progressQueries
    private val activity get() = database.activityQueries
    private fun userId() = localUser.userId()

    override suspend fun startAttempt(
        type: AttemptType,
        topicId: Long?,
        learningUnitId: Long?,
        startedAt: Instant,
    ): OperationResult<String, LearningError> = run {
        val id = newAttemptId()
        database.transaction {
            progress.abandonOpenAttempts(now().toEpochMilliseconds(), userId())
            progress.insertAttempt(id, userId(), type.name, topicId, learningUnitId, startedAt.toEpochMilliseconds())
        }
        id
    }

    override suspend fun finishAttempt(
        attemptId: String,
        status: AttemptStatus,
        finishedAt: Instant,
        earnedPoints: Int,
        maxPoints: Int,
    ): OperationResult<Unit, LearningError> = run {
        progress.finishAttempt(status.name, finishedAt.toEpochMilliseconds(), earnedPoints.toLong(), maxPoints.toLong(), attemptId)
    }

    override suspend fun recordAnswer(answer: AttemptAnswer): OperationResult<Unit, LearningError> = run {
        progress.upsertAttemptAnswer(answer.toRow(userId()))
    }

    override suspend fun answersOfAttempt(attemptId: String): OperationResult<List<AttemptAnswer>, LearningError> = run {
        progress.selectAnswersOfAttempt(attemptId).executeAsList().map { it.toDomain() }
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

    override suspend fun unitProgressOf(learningUnitId: Long): OperationResult<UnitProgress?, LearningError> = run {
        progress.selectUnitProgressOf(userId(), learningUnitId).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun saveUnitProgress(progress: UnitProgress): OperationResult<Unit, LearningError> = run {
        this.progress.upsertUnitProgress(
            userId(),
            progress.learningUnitId,
            progress.status.name,
            progress.startedAt.toEpochMilliseconds(),
            progress.completedAt?.toEpochMilliseconds(),
        )
    }

    override suspend fun taskProgress(taskId: Long): OperationResult<TaskProgress?, LearningError> = run {
        progress.selectTaskProgress(userId(), taskId).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun lastAnswerTimes(): OperationResult<Map<Long, Instant>, LearningError> = run {
        progress.selectLastAnswerTimes(userId()).executeAsList()
            .mapNotNull { row -> row.last_at?.let { row.task_id to Instant.fromEpochMilliseconds(it) } }
            .toMap()
    }

    override suspend fun answeredTasks(): OperationResult<List<AnsweredTask>, LearningError> = run {
        progress.selectAnsweredTasks(userId()).executeAsList().map { AnsweredTask(it.task_id, it.correct_streak > 0) }
    }

    override suspend fun mistakeTaskIds(): OperationResult<List<Long>, LearningError> = run {
        progress.selectMistakeTaskIds(userId()).executeAsList()
    }

    override suspend fun saveTaskProgress(progress: TaskProgress): OperationResult<Unit, LearningError> = run {
        this.progress.upsertTaskProgress(progress.toRow(userId()))
    }

    override suspend fun recordTaskAnswer(taskId: Long, correct: Boolean, answeredAt: Instant): OperationResult<Unit, LearningError> = run {
        progress.recordTaskAnswer(userId(), taskId, answeredAt.toEpochMilliseconds(), if (correct) 1L else 0L)
    }

    override suspend fun streak(): OperationResult<Streak?, LearningError> = run {
        activity.selectStreak(userId()).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun resetProgress(): OperationResult<Unit, LearningError> = run {
        progress.resetProgress(userId(), now().toEpochMilliseconds())
    }

    override suspend fun updateStreak(change: (Streak?) -> Streak?): OperationResult<Unit, LearningError> = run {
        database.transaction {
            val next = change(activity.selectStreak(userId()).executeAsOneOrNull()?.toDomain())
            if (next != null) saveStreakRow(next)
        }
    }

    private fun saveStreakRow(streak: Streak) =
        activity.upsertStreak(userId(), streak.currentStreak.toLong(), streak.longestStreak.toLong(), streak.lastActivityDate?.toString())

    override suspend fun saveStreak(streak: Streak): OperationResult<Unit, LearningError> = run {
        saveStreakRow(streak)
    }

    override suspend fun addActivity(activity: DailyActivity): OperationResult<Unit, LearningError> = run {
        this.activity.addActivity(
            userId(),
            activity.date.toString(),
            localUser.deviceId(),
            activity.secondsStudied.toLong(),
            activity.tasksAnswered.toLong(),
        )
    }

    override suspend fun activity(from: LocalDate, to: LocalDate): OperationResult<List<DailyActivity>, LearningError> = run {
        activity.selectActivity(userId(), from.toString(), to.toString()).executeAsList().map { it.toDomain() }
    }

    /** Runs a database call on the IO dispatcher and maps failures to [LearningError.DATABASE_ERROR]. */
    private suspend fun <T> run(block: () -> T): OperationResult<T, LearningError> =
        withContext(dispatcher) {
            try {
                OperationResult.Success(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                OperationResult.Error(LearningError.DATABASE_ERROR)
            }
        }
}
