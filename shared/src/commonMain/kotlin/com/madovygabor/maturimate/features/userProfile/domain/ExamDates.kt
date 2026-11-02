package com.madovygabor.maturimate.features.userProfile.domain

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.plus

/**
 * Dates around the maturita. The spring written exams start on the Monday of the second week
 * of March (the second Monday of March); the official date replaces it once it is published
 * (ROADMAP 10.6).
 */
object ExamDates {
    /** Exam dates can be chosen up to this many years ahead. */
    const val MAX_YEARS_AHEAD = 3

    /** The [targetDayOfWeek] of the second week of March in [year]: the second such day of the month. */
    fun secondWeekWeekday(
        year: Int,
        targetDayOfWeek: DayOfWeek = DayOfWeek.MONDAY
    ): LocalDate {
        val marchFirst = LocalDate(year, Month.MARCH, 1)
        // the first target day on or after 1 March, then one week later
        val daysUntilFirstTargetDay = (targetDayOfWeek.ordinal - marchFirst.dayOfWeek.ordinal + 7) % 7
        val firstTargetDay = marchFirst.plus(DatePeriod(days = daysUntilFirstTargetDay))
        return firstTargetDay.plus(DatePeriod(days = 7))
    }

    /** The next two spring written exams after [today]. */
    fun nextSpringExams(today: LocalDate): List<LocalDate> {
        val thisYearExam = secondWeekWeekday(today.year)
        val first = if (today < thisYearExam) thisYearExam else secondWeekWeekday(today.year + 1)
        val second = secondWeekWeekday(first.year + 1)
        return listOf(first, second)
    }

    /** The latest exam date that can be chosen. */
    fun latest(today: LocalDate): LocalDate = today.plus(DatePeriod(years = MAX_YEARS_AHEAD))

    fun isValid(date: LocalDate, today: LocalDate): Boolean = date >= today && date <= latest(today)
}
