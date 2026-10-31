package com.madovygabor.maturimate.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.database.content.ContentDatabase
import com.madovygabor.maturimate.database.content.Learning_unit
import com.madovygabor.maturimate.database.content.Subject
import com.madovygabor.maturimate.database.content.Task
import com.madovygabor.maturimate.database.content.Task_format
import com.madovygabor.maturimate.database.content.Task_key
import com.madovygabor.maturimate.database.content.Task_option
import com.madovygabor.maturimate.database.content.Topic
import com.madovygabor.maturimate.database.content.Unit_task
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.database.user.User_profile
import com.madovygabor.maturimate.database.user.User_role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DatabaseSchemaTest {

    private fun contentDatabase(): ContentDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn())
        ContentDatabase.Schema.create(driver)
        return ContentDatabase(driver)
    }

    private fun userDatabase(): UserDatabase {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn())
        UserDatabase.Schema.create(driver)
        return UserDatabase(driver)
    }

    private fun ContentDatabase.insertSampleUnit() {
        subjectQueries.insert(Subject(1, "HU_MJL", "Magyar nyelv és irodalom", "Maďarský jazyk a literatúra", "HU", null))
        topicQueries.insert(Topic(10, 1, null, "Stilisztika", null, 1, true))
        topicQueries.insert(Topic(11, null, 10, "Szóképek", null, 1, true))
        taskFormatQueries.insert(Task_format(1, "MC_SINGLE", "Multiple choice", "CHOICE", null))
        taskQueries.insert(Task(100, 11, 1, "Melyik szókép?", null, 1, null, true, 0))
        taskQueries.insertOption(Task_option(1000, 100, "ANSWER", "A", "metafora"))
        taskQueries.insertOption(Task_option(1001, 100, "ANSWER", "B", "hasonlat"))
        taskQueries.insertKey(Task_key(100, 1, null, 1000))
        learningUnitQueries.insert(Learning_unit(50, 11, "PRACTICE", "Gyakorlás", null, 3, 5, 10, 1, true, 0))
        learningUnitQueries.insertUnitTask(Unit_task(50, 100, 1, true))
    }

    @Test
    fun contentQueriesReturnTheInsertedUnit() {
        val db = contentDatabase()
        db.insertSampleUnit()

        assertEquals(listOf(10L, 11L), db.topicQueries.selectSubtreeIds(10).executeAsList())
        assertEquals("MC_SINGLE", db.taskQueries.selectById(100).executeAsOne().format_code)
        assertEquals(1000L, db.taskQueries.selectKeys(100).executeAsOne().option_id)
        assertEquals(listOf(100L), db.learningUnitQueries.selectUnitTasks(50).executeAsList().map { it.task_id })
    }

    @Test
    fun foreignKeysAreEnforced() {
        val db = contentDatabase()
        // key points to an option of another task
        db.insertSampleUnit()
        db.taskQueries.insert(Task(101, 11, 1, "Másik", null, 1, null, true, 0))
        assertFailsWith<Exception> { db.taskQueries.insertKey(Task_key(101, 1, null, 1000)) }
    }

    @Test
    fun checkConstraintsAreCopied() {
        val db = contentDatabase()
        assertFailsWith<Exception> {
            db.subjectQueries.insert(Subject(1, "X", "x", "x", "EN", null))
        }
    }

    @Test
    fun attemptIdIsAssignedBySqlite() {
        val db = userDatabase()
        val id = db.transactionWithResult {
            db.progressQueries.insertAttempt("user-1", "UNIT_ROUND", null, 50, 1_000)
            db.progressQueries.lastInsertedId().executeAsOne()
        }
        db.progressQueries.finishAttempt("FINISHED", 2_000, 4, 5, id)

        val attempt = db.progressQueries.selectAttempts("user-1").executeAsOne()
        assertEquals(id, attempt.attempt_id)
        assertEquals(4L, attempt.earned_points)
    }

    @Test
    fun activityOfTheSameDayIsAddedUp() {
        val db = userDatabase()
        db.activityQueries.addActivity("user-1", "2026-10-28", 10, 3, 5)
        db.activityQueries.addActivity("user-1", "2026-10-28", 10, 2, 5)

        val day = db.activityQueries.selectActivity("user-1", "2026-10-28", "2026-10-28").executeAsOne()
        assertEquals(5L, day.minutes_studied)
        assertEquals(20L, db.activityQueries.selectTotalXp("user-1").executeAsOne())
    }

    @Test
    fun attemptScopeCheckRejectsUnitAndTopicTogether() {
        val db = userDatabase()
        val failure = assertFailsWith<Exception> {
            db.progressQueries.insertAttempt("user-1", "UNIT_ROUND", 11, 50, 1_000)
        }
        assertTrue(failure.message.orEmpty().contains("CHECK"))
    }
    @Test
    fun guestProfileNeedsNoName() {
        val db = userDatabase()
        db.profileQueries.upsertProfile(User_profile("guest-1", null, null, null, null, null, "HU", "2027-03-15"))

        assertEquals("HU", db.profileQueries.selectProfile("guest-1").executeAsOne().teaching_language)
    }

    @Test
    fun localUserKeepsTheFirstId() {
        val db = userDatabase()
        db.profileQueries.insertLocalUser("guest-1", true)
        db.profileQueries.insertLocalUser("guest-2", true)

        assertEquals("guest-1", db.profileQueries.selectLocalUser().executeAsOne().user_id)
    }

    @Test
    fun roleCheckSkipsUnknownRoles() {
        val db = userDatabase()
        db.profileQueries.insertRole(User_role("user-1", 1, 1_000))
        // INSERT OR IGNORE also skips rows that fail a CHECK
        db.profileQueries.insertRole(User_role("user-1", 9, 1_000))

        assertEquals(listOf(1L), db.profileQueries.selectRoles("user-1").executeAsList().map { it.role_id })
    }
}
