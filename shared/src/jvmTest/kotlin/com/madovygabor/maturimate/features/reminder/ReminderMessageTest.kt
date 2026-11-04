package com.madovygabor.maturimate.features.reminder

import com.madovygabor.maturimate.features.reminder.domain.ReminderKind
import com.madovygabor.maturimate.features.reminder.domain.ReminderMessage
import com.madovygabor.maturimate.features.reminder.domain.chooseReminder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReminderMessageTest {

    @Test
    fun theKindFollowsTheStreak() {
        assertEquals(ReminderKind.PLAIN, chooseReminder(0, hadStreak = false, daysToExam = null, dayNumber = 1).kind)
        assertEquals(ReminderKind.COMEBACK, chooseReminder(0, hadStreak = true, daysToExam = null, dayNumber = 1).kind)
        assertEquals(ReminderMessage(ReminderKind.STREAK_SHORT, 1, 3), chooseReminder(3, true, null, 1))
        assertEquals(ReminderKind.STREAK_SHORT, chooseReminder(6, true, null, 1).kind)
        assertEquals(ReminderKind.STREAK_LONG, chooseReminder(7, true, null, 1).kind)
    }

    @Test
    fun theVariantChangesFromDayToDayAndStaysInRange() {
        val variants = (1L..30L).map { chooseReminder(3, true, null, it) }.map { it.variant }
        assertTrue(variants.all { it in 0 until ReminderKind.STREAK_SHORT.variants })
        assertEquals(ReminderKind.STREAK_SHORT.variants, variants.toSet().size)
        assertTrue((1L..29L).all { chooseReminder(3, true, null, it).variant != chooseReminder(3, true, null, it + 1).variant })
    }

    @Test
    fun theExamTextComesOneDayInSevenAndOnlyWithADateAhead() {
        val days = (1L..14L).filter { chooseReminder(3, true, daysToExam = 100, dayNumber = it).kind == ReminderKind.EXAM }
        assertEquals(listOf(7L, 14L), days)
        assertEquals(100, chooseReminder(3, true, 100, 7).number)
        assertEquals(ReminderKind.STREAK_SHORT, chooseReminder(3, true, daysToExam = 0, dayNumber = 7).kind)
        assertEquals(ReminderKind.STREAK_SHORT, chooseReminder(3, true, daysToExam = null, dayNumber = 7).kind)
    }
}
