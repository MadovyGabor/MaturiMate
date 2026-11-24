package com.madovygabor.maturimate.features.userProfile.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.database.user.Exam_date
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.userProfile.domain.OfficialExamDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExamDateRepositoryImplTest {

    private val official = OfficialExamDate(1, 2027, null, LocalDate(2027, 3, 16), publishedAt = 100)

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
