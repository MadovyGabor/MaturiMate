package com.madovygabor.maturimate.features.userProfile.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/**
 * The numbers of a student's plan: the exam date in use, the days left, and the practice hours at the daily goal.
 * One place for the plan and the goal screens (review #3, A6); pure, so it is tested without a screen.
 */
data class StudyEstimate(
    /** The date the app counts to: the official date wins over the rule's date ([ExamCalendar.effectiveDate]). */
    val examDate: LocalDate,
    /** Never negative: the exam day and a date in the past are 0. */
    val daysLeft: Int,
    /** Practice from today until the exam at the daily goal, in hours. */
    val totalHours: Int,
    /** One week of practice at the daily goal, in hours. */
    val hoursPerWeek: Int,
) {
    companion object {
        private const val MINUTES_PER_HOUR = 60
        private const val DAYS_PER_WEEK = 7

        /** Null when there is no exam date. */
        fun of(graduationDate: LocalDate?, calendar: ExamCalendar, today: LocalDate, dailyGoalMinutes: Int): StudyEstimate? {
            val examDate = calendar.effectiveDate(graduationDate) ?: return null
            val days = today.daysUntil(examDate).coerceAtLeast(0)
            return StudyEstimate(examDate, days, hours(dailyGoalMinutes, days), hours(dailyGoalMinutes, DAYS_PER_WEEK))
        }

        /** Hours of [minutesPerDay] over [days], rounded to the nearest whole hour, half up (1150 minutes: 19 hours). */
        fun hours(minutesPerDay: Int, days: Int): Int = ((minutesPerDay.toLong() * days + MINUTES_PER_HOUR / 2) / MINUTES_PER_HOUR).toInt()
    }
}
