package com.madovygabor.maturimate.features.userProfile

import com.madovygabor.maturimate.features.userProfile.domain.ExamCalendar
import com.madovygabor.maturimate.features.userProfile.domain.ExamDates
import com.madovygabor.maturimate.features.userProfile.domain.OfficialExamDate
import com.madovygabor.maturimate.features.userProfile.domain.StudyEstimate
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StudyEstimateTest {

    private val today = LocalDate(2026, 10, 31)
    private val none = ExamCalendar(emptyList())

    @Test
    fun noExamDateGivesNoEstimate() {
        assertNull(StudyEstimate.of(null, none, today, 10))
    }

    @Test
    fun theDateTheDaysAndTheHoursAreCounted() {
        val estimate = StudyEstimate.of(LocalDate(2027, 3, 15), none, today, 10)!!

        assertEquals(LocalDate(2027, 3, 15), estimate.examDate)
        assertEquals(135, estimate.daysLeft)
        // 1350 minutes = 22.5 hours, half rounds up
        assertEquals(23, estimate.totalHours)
        assertEquals(1, estimate.hoursPerWeek)
    }

    @Test
    fun theOfficialDateWinsOverTheRulesDate() {
        val rule = ExamDates.secondWeekWeekday(2027)
        val official = OfficialExamDate(1, 2027, null, rule.plus(2, DateTimeUnit.DAY), publishedAt = 1)

        val estimate = StudyEstimate.of(rule, ExamCalendar(listOf(official)), today, 10)!!

        assertEquals(official.date, estimate.examDate)
        assertEquals(today.daysUntil(official.date), estimate.daysLeft)
    }

    @Test
    fun aDatePickedByHandStaysEvenWhenAnOfficialDateExists() {
        val own = LocalDate(2027, 3, 1)
        val official = OfficialExamDate(1, 2027, null, LocalDate(2027, 3, 16), publishedAt = 1)

        assertEquals(own, StudyEstimate.of(own, ExamCalendar(listOf(official)), today, 10)!!.examDate)
    }

    @Test
    fun hoursAreRoundedToTheNearestHourAndHalfGoesUp() {
        // the review's example: 10 minutes x 115 days = 1150 minutes = 19.17 hours
        assertEquals(19, StudyEstimate.hours(10, 115))
        assertEquals(23, StudyEstimate.hours(10, 135))   // 22.5
        assertEquals(22, StudyEstimate.hours(10, 134))   // 22.33
        assertEquals(0, StudyEstimate.hours(10, 2))      // 20 minutes
        assertEquals(1, StudyEstimate.hours(10, 3))      // 30 minutes: half goes up
    }

    @Test
    fun hoursAWeekAreOneWeekOfTheDailyGoal() {
        assertEquals(1, StudyEstimate.of(LocalDate(2027, 3, 15), none, today, 5)!!.hoursPerWeek)   // 35 min
        assertEquals(1, StudyEstimate.of(LocalDate(2027, 3, 15), none, today, 10)!!.hoursPerWeek)  // 70 min
        assertEquals(2, StudyEstimate.of(LocalDate(2027, 3, 15), none, today, 15)!!.hoursPerWeek)  // 105 min
        assertEquals(2, StudyEstimate.of(LocalDate(2027, 3, 15), none, today, 20)!!.hoursPerWeek)  // 140 min
    }

    @Test
    fun theExamDayItselfIsZeroDaysAndZeroHours() {
        val estimate = StudyEstimate.of(today, none, today, 10)!!
        assertEquals(0, estimate.daysLeft)
        assertEquals(0, estimate.totalHours)
    }

    @Test
    fun aPastDateIsZeroDaysNotNegative() {
        val estimate = StudyEstimate.of(LocalDate(2026, 3, 16), none, today, 10)!!
        assertEquals(LocalDate(2026, 3, 16), estimate.examDate)
        assertEquals(0, estimate.daysLeft)
        assertEquals(0, estimate.totalHours)
    }

    @Test
    fun aYearOfPracticeUsesTheSameRounding() {
        assertEquals(30, StudyEstimate.hours(5, 365))
        assertEquals(61, StudyEstimate.hours(10, 365))
        assertEquals(91, StudyEstimate.hours(15, 365))
        assertEquals(122, StudyEstimate.hours(20, 365))
    }
}
