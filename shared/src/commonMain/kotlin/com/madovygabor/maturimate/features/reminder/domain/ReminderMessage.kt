package com.madovygabor.maturimate.features.reminder.domain

/** The kinds of reminder text; each kind has several variants, and the day picks one. */
enum class ReminderKind(val variants: Int) {
    /** No streak yet: a new student. */
    PLAIN(3),

    /** A streak of 1 to 6 days. */
    STREAK_SHORT(3),

    /** A streak of 7 days or more. */
    STREAK_LONG(3),

    /** Days until the exam; only now and then. */
    EXAM(2),

    /** The streak was broken: welcome back. */
    COMEBACK(2),
}

/** Which text to show: the [kind], the [variant] (from 0) and the number the text mentions (streak or days), if any. */
data class ReminderMessage(val kind: ReminderKind, val variant: Int, val number: Int?)

/** After this many days the streak counts as long. */
const val LONG_STREAK_DAYS = 7

/** The days between two exam reminders. */
private const val EXAM_EVERY_DAYS = 7

/**
 * Picks the reminder text so that it changes from day to day without being random: the variant follows
 * [dayNumber] (any number that grows by one a day, for example the epoch day).
 *
 * * [streak] is the streak as shown (zero after a missed day); [hadStreak] says that the student has had one
 *   before, which makes a zero a comeback instead of a new start.
 * * [daysToExam] is empty when no exam date is known or it has passed. The exam text comes one day in seven.
 */
fun chooseReminder(streak: Int, hadStreak: Boolean, daysToExam: Int?, dayNumber: Long): ReminderMessage {
    val day = dayNumber.coerceAtLeast(0)
    fun variant(kind: ReminderKind) = (day % kind.variants).toInt()
    if (daysToExam != null && daysToExam > 0 && day % EXAM_EVERY_DAYS == 0L) {
        return ReminderMessage(ReminderKind.EXAM, variant(ReminderKind.EXAM), daysToExam)
    }
    return when {
        streak >= LONG_STREAK_DAYS -> ReminderMessage(ReminderKind.STREAK_LONG, variant(ReminderKind.STREAK_LONG), streak)
        streak > 0 -> ReminderMessage(ReminderKind.STREAK_SHORT, variant(ReminderKind.STREAK_SHORT), streak)
        hadStreak -> ReminderMessage(ReminderKind.COMEBACK, variant(ReminderKind.COMEBACK), null)
        else -> ReminderMessage(ReminderKind.PLAIN, variant(ReminderKind.PLAIN), null)
    }
}
