package com.madovygabor.maturimate.features.userProfile.domain

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.plus

/**
 * Dates around the maturita. The spring written exams are taken as 15 March, as in the
 * prototype: an example until the official dates are checked (docs/onboarding/README.md).
 */
object ExamDates {
    private const val WRITTEN_EXAM_DAY = 15

    /** Exam dates can be chosen up to this many years ahead. */
    const val MAX_YEARS_AHEAD = 3

    /** The next two spring written exams after [today]. */
    fun nextSpringExams(today: LocalDate): List<LocalDate> {
        val thisYear = LocalDate(today.year, Month.MARCH, WRITTEN_EXAM_DAY)
        val first = if (today < thisYear) thisYear else LocalDate(today.year + 1, Month.MARCH, WRITTEN_EXAM_DAY)
        return listOf(first, LocalDate(first.year + 1, Month.MARCH, WRITTEN_EXAM_DAY))
    }

    /** The latest exam date that can be chosen. */
    fun latest(today: LocalDate): LocalDate = today.plus(DatePeriod(years = MAX_YEARS_AHEAD))

    fun isValid(date: LocalDate, today: LocalDate): Boolean = date >= today && date <= latest(today)
}
