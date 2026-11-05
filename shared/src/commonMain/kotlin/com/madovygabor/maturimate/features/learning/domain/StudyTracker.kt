package com.madovygabor.maturimate.features.learning.domain

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.learning.domain.model.DailyActivity
import com.madovygabor.maturimate.features.learning.domain.model.Streak
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** What the tracker needs from the user's settings: the daily goal in minutes (empty: the default) and the time zone. */
data class StudySettings(val dailyGoalMinutes: Int?, val timeZone: TimeZone)

/** The study time of a day against the daily goal. */
data class DailyGoalProgress(val date: LocalDate, val secondsStudied: Int, val goalSeconds: Int) {
    val reached: Boolean get() = secondsStudied >= goalSeconds

    /** From 0 to 1; stays at 1 when the goal is passed. */
    val fraction: Float get() = (secondsStudied.toFloat() / goalSeconds).coerceIn(0f, 1f)
}

/** All study so far: the seconds of every day and the answered tasks. */
data class StudyTotals(val secondsStudied: Long, val tasksAnswered: Int)

/**
 * Counts the study time of each day and keeps the streak. It holds the rules; the lesson and test screens only
 * tell it how long the student studied.
 *
 * * The day is the day in the user's time zone. The daily goal is in study minutes; answered tasks do not count.
 * * A day is done when its seconds reach the goal. The first done day after a done day (yesterday) makes the
 *   streak one longer; after a missed day it starts again at one. A day is counted once.
 * * A streak is shown as zero once a whole day has passed without a done day ([shownStreak]): no grace day.
 */
@OptIn(ExperimentalTime::class)
class StudyTracker(
    private val progress: ProgressRepository,
    private val settings: suspend () -> StudySettings,
    private val now: () -> Instant = { Clock.System.now() },
) {

    /** Adds [seconds] of study and [tasks] answered tasks to today, and moves the streak when today's goal is reached. */
    suspend fun record(seconds: Int, tasks: Int = 0): OperationResult<Unit, LearningError> {
        if (seconds <= 0 && tasks <= 0) return OperationResult.Success(Unit)
        val config = settings()
        val today = now().toLocalDateTime(config.timeZone).date
        val added = progress.addActivity(DailyActivity(today, xpEarned = 0, secondsStudied = maxOf(0, seconds), tasksAnswered = tasks))
        if (added is OperationResult.Error) return added
        val day = (progress.activity(today, today) as? OperationResult.Success)?.data?.singleOrNull()
        if (day == null || day.secondsStudied < goalSeconds(config)) return OperationResult.Success(Unit)
        return advanceStreak(today)
    }

    /** Today's study time against the goal. */
    suspend fun today(): OperationResult<DailyGoalProgress, LearningError> {
        val config = settings()
        val today = now().toLocalDateTime(config.timeZone).date
        return when (val result = progress.activity(today, today)) {
            is OperationResult.Success ->
                OperationResult.Success(DailyGoalProgress(today, result.data.sumOf { it.secondsStudied }, goalSeconds(config)))
            is OperationResult.Error -> OperationResult.Error(result.error)
        }
    }

    /** Everything studied so far: the study time of every day and the answered tasks, added up. */
    suspend fun totals(): OperationResult<StudyTotals, LearningError> {
        val today = now().toLocalDateTime(settings().timeZone).date
        return when (val result = progress.activity(FIRST_DAY, today)) {
            is OperationResult.Success -> OperationResult.Success(
                StudyTotals(result.data.sumOf { it.secondsStudied.toLong() }, result.data.sumOf { it.tasksAnswered })
            )
            is OperationResult.Error -> OperationResult.Error(result.error)
        }
    }

    /** The days of the current week, Monday first, with whether each day's goal was reached. */
    suspend fun week(): OperationResult<List<WeekDay>, LearningError> {
        val config = settings()
        val today = now().toLocalDateTime(config.timeZone).date
        val monday = today.minus(DatePeriod(days = today.dayOfWeek.isoDayNumber - 1))
        val sunday = monday.plus(DatePeriod(days = 6))
        val days = when (val result = progress.activity(monday, sunday)) {
            is OperationResult.Success -> result.data.associateBy { it.date }
            is OperationResult.Error -> return OperationResult.Error(result.error)
        }
        val goal = goalSeconds(config)
        return OperationResult.Success((0..6).map { offset ->
            val date = monday.plus(DatePeriod(days = offset))
            val reached = (days[date]?.secondsStudied ?: 0) >= goal
            val state = when {
                reached -> WeekDayState.DONE
                date == today -> WeekDayState.TODAY
                date < today -> WeekDayState.MISSED
                else -> WeekDayState.LATER
            }
            WeekDay(date, state)
        })
    }

    /** The streak as the home screen shows it: zero when a whole day was missed. */
    suspend fun currentStreak(): OperationResult<Int, LearningError> {
        val today = now().toLocalDateTime(settings().timeZone).date
        return when (val result = progress.streak()) {
            is OperationResult.Success -> OperationResult.Success(shownStreak(result.data, today))
            is OperationResult.Error -> OperationResult.Error(result.error)
        }
    }

    /** True when the student has had a streak at some time, even if it is broken now. */
    suspend fun hadStreak(): Boolean =
        ((progress.streak() as? OperationResult.Success)?.data?.longestStreak ?: 0) > 0

    private suspend fun advanceStreak(today: LocalDate): OperationResult<Unit, LearningError> {
        val earlier = (progress.streak() as? OperationResult.Success)?.data
        if (earlier?.lastActivityDate == today) return OperationResult.Success(Unit)
        val current = if (earlier?.lastActivityDate == today.minus(DatePeriod(days = 1))) earlier.currentStreak + 1 else 1
        return progress.saveStreak(Streak(current, maxOf(current, earlier?.longestStreak ?: 0), today))
    }

    private fun goalSeconds(config: StudySettings) = (config.dailyGoalMinutes ?: DEFAULT_GOAL_MINUTES) * 60

    companion object {
        /** The goal of a student who has not chosen one. */
        const val DEFAULT_GOAL_MINUTES = 10

        /** Before any day the app can have recorded. */
        private val FIRST_DAY = LocalDate(2000, 1, 1)

        /** The streak to show on [today]: it still counts after yesterday's done day, and is zero after a missed day. */
        fun shownStreak(streak: Streak?, today: LocalDate): Int {
            val last = streak?.lastActivityDate ?: return 0
            return if (last >= today.minus(DatePeriod(days = 1))) streak.currentStreak else 0
        }
    }
}
