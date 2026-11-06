package com.madovygabor.maturimate.features.learning.domain

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.learning.domain.model.ContinueTarget
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlin.time.ExperimentalTime

/** The line under the greeting of the home screen; it follows the student's situation. */
enum class MottoKind {
    /** Today's goal is reached. */
    REACHED,

    /** The streak was broken: welcome back. */
    COMEBACK,

    /** Nothing studied yet. */
    FRESH,

    /** A streak is running and today's goal is not reached yet. */
    STREAK,

    /** Otherwise. */
    GENERAL,
}

/** How a day of the current week looks: the goal reached, missed, today (not reached yet) or still ahead. */
enum class WeekDayState { DONE, MISSED, TODAY, LATER }

/** The days of the current week, Monday first. */
data class WeekDay(val date: LocalDate, val state: WeekDayState)

/** Everything the home screen shows. */
@OptIn(ExperimentalTime::class)
data class HomeSummary(
    val firstName: String?,
    val streak: Int,
    val goal: DailyGoalProgress,
    val week: List<WeekDay>,
    /** Days until the written exam; empty when no date is known or it has passed. */
    val daysToExam: Int?,
    /** The step the "Folytatás" card leads to; empty when everything is done. */
    val next: ContinueTarget?,
    val recent: List<RecentActivity>,
    val motto: MottoKind,
    /** How many tasks were answered wrong last; the card of the mistake practice shows it. */
    val mistakes: Int = 0,
)

/**
 * Gathers the home screen: the streak and the daily goal ([StudyTracker]), the last activities
 * ([RecentActivities]), the next step of the path ([LearningCourse]) and the name and exam date of the profile.
 */
@OptIn(ExperimentalTime::class)
class HomeOverview(
    private val tracker: StudyTracker,
    private val course: LearningCourse,
    private val recent: RecentActivities,
    private val profile: UserProfileRepository,
    /** The number of mistakes (the mistake practice lives in the practice feature). */
    private val mistakeCount: suspend () -> Int = { 0 },
) {

    suspend fun load(): OperationResult<HomeSummary, LearningError> {
        val goal = when (val result = tracker.today()) {
            is OperationResult.Success -> result.data
            is OperationResult.Error -> return OperationResult.Error(result.error)
        }
        val streak = (tracker.currentStreak() as? OperationResult.Success)?.data ?: return OperationResult.Error(LearningError.DATABASE_ERROR)
        val week = when (val result = tracker.week()) {
            is OperationResult.Success -> result.data
            is OperationResult.Error -> return OperationResult.Error(result.error)
        }
        val latest = when (val result = recent.latest()) {
            is OperationResult.Success -> result.data
            is OperationResult.Error -> return OperationResult.Error(result.error)
        }
        // "Folytatás" goes on after the lesson or test worked on last; a practice round has no unit and does not move it
        val lastUnit = latest.firstOrNull { it.unitId != null }?.unitId
            ?: (recent.all() as? OperationResult.Success)?.data?.firstOrNull { it.unitId != null }?.unitId
        val next = when (val result = course.nextStep(lastUnit)) {
            is OperationResult.Success -> result.data
            is OperationResult.Error -> return OperationResult.Error(result.error)
        }
        val user = profile.profile.first()
        val daysToExam = user?.graduationDate?.let { goal.date.daysUntil(it) }?.takeIf { it > 0 }
        val hadStreak = tracker.hadStreak()
        return OperationResult.Success(
            HomeSummary(
                firstName = user?.firstName,
                streak = streak,
                goal = goal,
                week = week,
                daysToExam = daysToExam,
                next = next,
                recent = latest,
                motto = mottoOf(goal.reached, streak, hadStreak, studiedBefore = latest.isNotEmpty() || goal.secondsStudied > 0),
                mistakes = mistakeCount(),
            )
        )
    }

    companion object {
        /** The motto for the situation. */
        fun mottoOf(goalReached: Boolean, streak: Int, hadStreak: Boolean, studiedBefore: Boolean): MottoKind = when {
            goalReached -> MottoKind.REACHED
            streak > 0 -> MottoKind.STREAK
            hadStreak -> MottoKind.COMEBACK
            !studiedBefore -> MottoKind.FRESH
            else -> MottoKind.GENERAL
        }
    }
}
