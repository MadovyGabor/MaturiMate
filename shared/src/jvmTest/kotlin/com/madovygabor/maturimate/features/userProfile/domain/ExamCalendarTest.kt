package com.madovygabor.maturimate.features.userProfile.domain

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExamCalendarTest {

    private val rule2027 = ExamDates.secondWeekWeekday(2027)
    private val official = OfficialExamDate(1, 2027, null, LocalDate(2027, 3, 16), publishedAt = 100)
    private val calendar = ExamCalendar(listOf(official, OfficialExamDate(2, 2027, 5, LocalDate(2027, 3, 20), 100)))

    @Test
    fun theOfficialDateReplacesTheRulesDate() {
        assertEquals(official.date, calendar.effectiveDate(rule2027))
        assertTrue(calendar.isOfficial(rule2027))
        assertNull(calendar.offer(rule2027))
    }

    @Test
    fun aDatePickedByHandStaysAndTheOfficialOneIsOffered() {
        val own = LocalDate(2027, 3, 1)
        assertEquals(own, calendar.effectiveDate(own))
        assertFalse(calendar.isOfficial(own))
        assertEquals(official, calendar.offer(own))
        // the user's date is the official one already: nothing to ask
        assertNull(calendar.offer(official.date))
    }

    @Test
    fun withoutAnOfficialDateTheRuleStays() {
        val empty = ExamCalendar(emptyList())
        assertEquals(rule2027, empty.effectiveDate(rule2027))
        assertNull(empty.offer(LocalDate(2027, 3, 1)))
        assertNull(empty.effectiveDate(null))
        // another year's official date does not count
        assertEquals(LocalDate(2028, 3, 1), calendar.effectiveDate(LocalDate(2028, 3, 1)))
    }
}
