package com.madovygabor.maturimate.features.learning.domain

import com.madovygabor.maturimate.core.domain.error.Error
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.learning.domain.model.Attempt
import com.madovygabor.maturimate.features.learning.domain.model.AttemptAnswer
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
import com.madovygabor.maturimate.features.learning.domain.model.AnsweredTask

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
    suspend fun topic(topicId: Long): OperationResult<Topic, LearningError>
    suspend fun childTopics(topicId: Long): OperationResult<List<Topic>, LearningError>
    /** The topic and every topic below it, for reviews over a subtree. */
    suspend fun subtreeTopicIds(topicId: Long): OperationResult<List<Long>, LearningError>
    /** The units of a topic in order. Lessons come without their pages (a listing does not need the text); [unit] has them. */
    suspend fun units(topicId: Long): OperationResult<List<LearningUnit>, LearningError>
    suspend fun unit(learningUnitId: Long): OperationResult<LearningUnit, LearningError>
    /** Tasks of a practice unit: core tasks first, then reserve tasks, each in unit order. */
    suspend fun unitTasks(learningUnitId: Long): OperationResult<List<UnitTask>, LearningError>
    suspend fun task(taskId: Long): OperationResult<Task, LearningError>
}

/** The local user's progress. Holds no rules: grading, unlocking and repetition are decided by use cases. */
@OptIn(ExperimentalTime::class)
interface ProgressRepository {
    /**
     * Starts a round and returns its id, made by the device. A round of this user that is still open (the app was
     * killed in the middle of it) is closed as `ABANDONED` first.
     */
    suspend fun startAttempt(type: AttemptType, topicId: Long?, learningUnitId: Long?, startedAt: Instant): OperationResult<String, LearningError>
    suspend fun finishAttempt(
        attemptId: String,
        status: AttemptStatus,
        finishedAt: Instant,
        earnedPoints: Int,
        maxPoints: Int,
    ): OperationResult<Unit, LearningError>
    /** Records the answer to one task of a round; a second answer to the same task replaces the first. */
    suspend fun recordAnswer(answer: AttemptAnswer): OperationResult<Unit, LearningError>
    suspend fun answersOfAttempt(attemptId: String): OperationResult<List<AttemptAnswer>, LearningError>
    suspend fun attempts(): OperationResult<List<Attempt>, LearningError>
    suspend fun attemptsOfUnit(learningUnitId: Long): OperationResult<List<Attempt>, LearningError>

    suspend fun unitProgress(): OperationResult<List<UnitProgress>, LearningError>
    /** The progress of one unit; null when it was never opened. */
    suspend fun unitProgressOf(learningUnitId: Long): OperationResult<UnitProgress?, LearningError>
    suspend fun saveUnitProgress(progress: UnitProgress): OperationResult<Unit, LearningError>

    suspend fun taskProgress(taskId: Long): OperationResult<TaskProgress?, LearningError>
    /** The time of the newest answer of every task answered, in any round (the review of old material). */
    suspend fun lastAnswerTimes(): OperationResult<Map<Long, Instant>, LearningError>
    /** Every task answered at least once, with whether its latest answer is right (the topic scores). */
    suspend fun answeredTasks(): OperationResult<List<AnsweredTask>, LearningError>
    /** The tasks whose latest answer is wrong: the mistakes of the mistake practice. */
    suspend fun mistakeTaskIds(): OperationResult<List<Long>, LearningError>
    suspend fun saveTaskProgress(progress: TaskProgress): OperationResult<Unit, LearningError>
    /**
     * Counts one answer in the progress of the task: right adds to the answers and the right answers in a row, wrong
     * sets the row to 0. Done in the database, not from a value read earlier, so no count is lost.
     */
    suspend fun recordTaskAnswer(taskId: Long, correct: Boolean, answeredAt: Instant): OperationResult<Unit, LearningError>

    /** "Haladás újrakezdése": the rounds, answers, finished units and task repetition go; streak and study time stay. */
    suspend fun resetProgress(): OperationResult<Unit, LearningError>

    suspend fun streak(): OperationResult<Streak?, LearningError>
    suspend fun saveStreak(streak: Streak): OperationResult<Unit, LearningError>
    /**
     * Reads the streak, lets [change] decide the new one and saves it, in one transaction. [change] returns null to
     * leave the streak as it is. The rule stays with the caller; the database only keeps the read and the write together.
     */
    suspend fun updateStreak(change: (Streak?) -> Streak?): OperationResult<Unit, LearningError>
    /** Adds to the day's totals of this device. */
    suspend fun addActivity(activity: DailyActivity): OperationResult<Unit, LearningError>
    suspend fun activity(from: LocalDate, to: LocalDate): OperationResult<List<DailyActivity>, LearningError>
}
