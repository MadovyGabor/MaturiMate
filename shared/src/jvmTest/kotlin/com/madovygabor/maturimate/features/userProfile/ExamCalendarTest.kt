package com.madovygabor.maturimate.features.userProfile

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.database.user.Exam_date
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.userProfile.data.ExamDateRepositoryImpl
import com.madovygabor.maturimate.features.userProfile.domain.ExamCalendar
import com.madovygabor.maturimate.features.userProfile.domain.ExamDates
import com.madovygabor.maturimate.features.userProfile.domain.OfficialExamDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

    @Test
    fun anAnswerIsRememberedUntilANewPublication() = runBlocking {
        val database = UserDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { UserDatabase.Schema.create(it) })
        database.examDateQueries.insert(Exam_date(1, 2027, null, "2027-03-16", 100, null))
        val repository = ExamDateRepositoryImpl(database, Dispatchers.Unconfined)

        val date = repository.dates.first().single()
        assertEquals(official, date)
        assertFalse(repository.isAnswered(date))
        repository.markAnswered(date)
        assertTrue(repository.isAnswered(date))
        // the owner publishes the date again (a correction): it asks again
        assertFalse(repository.isAnswered(date.copy(publishedAt = 200)))
    }
}
