package com.madovygabor.maturimate.features.learning.data.mapper

import com.madovygabor.maturimate.database.user.Activity_log
import com.madovygabor.maturimate.database.user.SelectDueTasks
import com.madovygabor.maturimate.database.user.User_streak
import com.madovygabor.maturimate.database.user.User_task_progress
import com.madovygabor.maturimate.database.user.User_unit_progress
import com.madovygabor.maturimate.features.learning.domain.model.Attempt
import com.madovygabor.maturimate.features.learning.domain.model.AttemptStatus
import com.madovygabor.maturimate.features.learning.domain.model.AttemptType
import com.madovygabor.maturimate.features.learning.domain.model.DailyActivity
import com.madovygabor.maturimate.features.learning.domain.model.Streak
import com.madovygabor.maturimate.features.learning.domain.model.TaskProgress
import com.madovygabor.maturimate.features.learning.domain.model.TaskProgressStatus
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgress
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgressStatus
import kotlinx.datetime.LocalDate
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import com.madovygabor.maturimate.database.user.Attempt as AttemptRow

// Maps rows of the user database to domain models and back.
// Timestamps are stored as epoch milliseconds, dates as `YYYY-MM-DD` text.

@OptIn(ExperimentalTime::class)
private fun Long.toInstant() = Instant.fromEpochMilliseconds(this)

@OptIn(ExperimentalTime::class)
fun AttemptRow.toDomain() = Attempt(
    id = attempt_id,
    type = AttemptType.valueOf(attempt_type),
    topicId = topic_id,
    learningUnitId = learning_unit_id,
    status = AttemptStatus.valueOf(status),
    startedAt = started_at.toInstant(),
    finishedAt = finished_at?.toInstant(),
    earnedPoints = earned_points?.toInt(),
    maxPoints = max_points?.toInt(),
)

@OptIn(ExperimentalTime::class)
fun User_unit_progress.toDomain() = UnitProgress(
    learningUnitId = learning_unit_id,
    status = UnitProgressStatus.valueOf(status),
    startedAt = started_at.toInstant(),
    completedAt = completed_at?.toInstant(),
)

@OptIn(ExperimentalTime::class)
fun UnitProgress.toRow(userId: Long) = User_unit_progress(
    user_id = userId,
    learning_unit_id = learningUnitId,
    status = status.name,
    started_at = startedAt.toEpochMilliseconds(),
    completed_at = completedAt?.toEpochMilliseconds(),
)

@OptIn(ExperimentalTime::class)
fun User_task_progress.toDomain() = TaskProgress(
    taskId = task_id,
    timesCorrect = times_correct.toInt(),
    timesWrong = times_wrong.toInt(),
    correctStreak = correct_streak.toInt(),
    lastAnsweredAt = last_answered_at.toInstant(),
    nextReviewAt = next_review_at?.toInstant(),
    status = TaskProgressStatus.valueOf(status),
)

/** Due tasks always have a review time, so SQLDelight generates a separate row type for them. */
@OptIn(ExperimentalTime::class)
fun SelectDueTasks.toDomain() = TaskProgress(
    taskId = task_id,
    timesCorrect = times_correct.toInt(),
    timesWrong = times_wrong.toInt(),
    correctStreak = correct_streak.toInt(),
    lastAnsweredAt = last_answered_at.toInstant(),
    nextReviewAt = next_review_at.toInstant(),
    status = TaskProgressStatus.valueOf(status),
)

@OptIn(ExperimentalTime::class)
fun TaskProgress.toRow(userId: Long) = User_task_progress(
    user_id = userId,
    task_id = taskId,
    times_correct = timesCorrect.toLong(),
    times_wrong = timesWrong.toLong(),
    correct_streak = correctStreak.toLong(),
    last_answered_at = lastAnsweredAt.toEpochMilliseconds(),
    next_review_at = nextReviewAt?.toEpochMilliseconds(),
    status = status.name,
)

fun User_streak.toDomain() = Streak(
    currentStreak = current_streak.toInt(),
    longestStreak = longest_streak.toInt(),
    lastActivityDate = last_activity_date?.let(LocalDate::parse),
)

fun Streak.toRow(userId: Long) = User_streak(
    user_id = userId,
    current_streak = currentStreak.toLong(),
    longest_streak = longestStreak.toLong(),
    last_activity_date = lastActivityDate?.toString(),
)

fun Activity_log.toDomain() = DailyActivity(
    date = LocalDate.parse(activity_date),
    xpEarned = xp_earned.toInt(),
    minutesStudied = minutes_studied.toInt(),
    tasksAnswered = tasks_answered.toInt(),
)
