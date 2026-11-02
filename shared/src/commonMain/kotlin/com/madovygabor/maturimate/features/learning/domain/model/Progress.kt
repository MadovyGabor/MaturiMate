package com.madovygabor.maturimate.features.learning.domain.model

import kotlinx.datetime.LocalDate
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Domain models of the user's progress (user database). */

enum class AttemptType { UNIT_ROUND, EXTRA_PRACTICE, REVIEW }

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
    val minutesStudied: Int,
    val tasksAnswered: Int,
)
