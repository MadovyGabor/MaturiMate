package com.madovygabor.maturimate.features.learning.domain

import com.madovygabor.maturimate.core.domain.error.Error
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.learning.domain.model.Attempt
import com.madovygabor.maturimate.features.learning.domain.model.AttemptStatus
import com.madovygabor.maturimate.features.learning.domain.model.AttemptType
import com.madovygabor.maturimate.features.learning.domain.model.DailyActivity
import com.madovygabor.maturimate.features.learning.domain.model.LearningUnit
import com.madovygabor.maturimate.features.learning.domain.model.Streak
import com.madovygabor.maturimate.features.learning.domain.model.Subject
import com.madovygabor.maturimate.features.learning.domain.model.Task
import com.madovygabor.maturimate.features.learning.domain.model.TaskProgress
import com.madovygabor.maturimate.features.learning.domain.model.Topic
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgress
import com.madovygabor.maturimate.features.learning.domain.model.UnitTask
import kotlinx.datetime.LocalDate
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Errors of reading content or progress. */
enum class LearningError : Error {
    /** No content is installed and none is bundled with the app. */
    NO_CONTENT,

    /** The requested subject, topic, unit or task does not exist. */
    NOT_FOUND,

    /** The local database could not be read or written. */
    DATABASE_ERROR,
}

/** Read access to the installed content. Only published topics and units are returned. */
interface ContentRepository {
    suspend fun subjects(): OperationResult<List<Subject>, LearningError>
    suspend fun rootTopics(subjectId: Long): OperationResult<List<Topic>, LearningError>
    suspend fun childTopics(topicId: Long): OperationResult<List<Topic>, LearningError>
    /** The topic and every topic below it, for reviews over a subtree. */
    suspend fun subtreeTopicIds(topicId: Long): OperationResult<List<Long>, LearningError>
    suspend fun units(topicId: Long): OperationResult<List<LearningUnit>, LearningError>
    suspend fun unit(learningUnitId: Long): OperationResult<LearningUnit, LearningError>
    /** Tasks of a practice unit: core tasks first, then reserve tasks, each in unit order. */
    suspend fun unitTasks(learningUnitId: Long): OperationResult<List<UnitTask>, LearningError>
    suspend fun task(taskId: Long): OperationResult<Task, LearningError>
}

/** The local user's progress. Holds no rules: grading, unlocking and repetition are decided by use cases. */
@OptIn(ExperimentalTime::class)
interface ProgressRepository {
    suspend fun startAttempt(type: AttemptType, topicId: Long?, learningUnitId: Long?, startedAt: Instant): OperationResult<Long, LearningError>
    suspend fun finishAttempt(
        attemptId: Long,
        status: AttemptStatus,
        finishedAt: Instant,
        earnedPoints: Int,
        maxPoints: Int,
    ): OperationResult<Unit, LearningError>
    suspend fun attempts(): OperationResult<List<Attempt>, LearningError>
    suspend fun attemptsOfUnit(learningUnitId: Long): OperationResult<List<Attempt>, LearningError>

    suspend fun unitProgress(): OperationResult<List<UnitProgress>, LearningError>
    suspend fun saveUnitProgress(progress: UnitProgress): OperationResult<Unit, LearningError>

    suspend fun taskProgress(taskId: Long): OperationResult<TaskProgress?, LearningError>
    suspend fun dueTasks(now: Instant): OperationResult<List<TaskProgress>, LearningError>
    suspend fun saveTaskProgress(progress: TaskProgress): OperationResult<Unit, LearningError>

    suspend fun streak(): OperationResult<Streak?, LearningError>
    suspend fun saveStreak(streak: Streak): OperationResult<Unit, LearningError>
    /** Adds to the day's totals. */
    suspend fun addActivity(activity: DailyActivity): OperationResult<Unit, LearningError>
    suspend fun activity(from: LocalDate, to: LocalDate): OperationResult<List<DailyActivity>, LearningError>
    suspend fun totalXp(): OperationResult<Long, LearningError>
}
