package com.madovygabor.maturimate.features.userProfile.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/** An official date of the written exams, published by the owner on the server and pulled by the sync. */
data class OfficialExamDate(
    val id: Long,
    val year: Int,
    /** Empty: every subject. */
    val subjectId: Long?,
    val date: LocalDate,
    val publishedAt: Long,
)

/** The official dates on this phone, and which offers the user has answered already. */
interface ExamDateRepository {
    val dates: Flow<List<OfficialExamDate>>

    /** True when the user already said yes or no to this official date (this publication of it). */
    suspend fun isAnswered(date: OfficialExamDate): Boolean

    suspend fun markAnswered(date: OfficialExamDate)
}

/**
 * The exam date the app counts down to (roadmap 12.7, decided 2026-11-12):
 * * the user's date is the one the rule gives (the Monday of the second week of March): the official date of that year
 *   replaces it automatically;
 * * the user picked another date by hand: it stays, and [offer] returns the official date so the app asks once;
 * * no official date yet: the rule (or the user's date), as before.
 */
class ExamCalendar(private val dates: List<OfficialExamDate>) {

    /** The official date of [year] for every subject, if published. */
    fun official(year: Int): OfficialExamDate? = dates.firstOrNull { it.year == year && it.subjectId == null }

    fun effectiveDate(graduationDate: LocalDate?): LocalDate? {
        val date = graduationDate ?: return null
        val official = official(date.year) ?: return date
        return if (date == ExamDates.secondWeekWeekday(date.year)) official.date else date
    }

    /** True when the date in use is the official one. */
    fun isOfficial(graduationDate: LocalDate?): Boolean {
        val date = effectiveDate(graduationDate) ?: return false
        return official(date.year)?.date == date
    }

    /** The official date to offer instead of a date picked by hand; null when there is nothing to ask. */
    fun offer(graduationDate: LocalDate?): OfficialExamDate? {
        val date = graduationDate ?: return null
        val official = official(date.year) ?: return null
        if (date == official.date || date == ExamDates.secondWeekWeekday(date.year)) return null
        return official
    }
}
