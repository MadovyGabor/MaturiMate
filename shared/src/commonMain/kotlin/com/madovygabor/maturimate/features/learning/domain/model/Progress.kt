package com.madovygabor.maturimate.features.learning.domain.model

import kotlinx.datetime.LocalDate
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Domain models of the user's progress (user database). */

enum class AttemptType {
    UNIT_ROUND, EXTRA_PRACTICE, REVIEW,

    /** A free practice round over the topics the student picked; belongs to no unit and no topic. */
    FREE_PRACTICE,

    /** A round over the tasks whose latest answer is wrong; belongs to no unit and no topic. */
    MISTAKE_PRACTICE,
}

enum class AttemptStatus { IN_PROGRESS, FINISHED, ABANDONED }

/** One practice round. Unit rounds have [learningUnitId], a review has [topicId]. */
@OptIn(ExperimentalTime::class)
data class Attempt(
    val id: Long,
    val type: AttemptType,
    val topicId: Long?,
    val learningUnitId: Long?,
    val status: AttemptStatus,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val earnedPoints: Int?,
    val maxPoints: Int?,
)

/**
 * The answer to one task in a round: right or wrong, the points, and what the student answered
 * ([givenAnswer], short text such as `1=B;2=A`, for review).
 */
@OptIn(ExperimentalTime::class)
data class AttemptAnswer(
    val attemptId: Long,
    val taskId: Long,
    val isCorrect: Boolean,
    val earnedPoints: Int,
    val maxPoints: Int,
    val answeredAt: Instant,
    val givenAnswer: String?,
)

enum class UnitProgressStatus { IN_PROGRESS, COMPLETED }

@OptIn(ExperimentalTime::class)
data class UnitProgress(
    val learningUnitId: Long,
    val status: UnitProgressStatus,
    val startedAt: Instant,
    val completedAt: Instant?,
)

enum class TaskProgressStatus { LEARNING, MASTERED }

/** Summary of how the user does on one task; drives repetition and weak spots. */
@OptIn(ExperimentalTime::class)
data class TaskProgress(
    val taskId: Long,
    val timesCorrect: Int,
    val timesWrong: Int,
    val correctStreak: Int,
    val lastAnsweredAt: Instant,
    val nextReviewAt: Instant?,
    val status: TaskProgressStatus,
)

data class Streak(
    val currentStreak: Int,
    val longestStreak: Int,
    val lastActivityDate: LocalDate?,
)

data class DailyActivity(
    val date: LocalDate,
    val xpEarned: Int,
    val secondsStudied: Int,
    val tasksAnswered: Int,
)
