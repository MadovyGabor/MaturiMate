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
import com.madovygabor.maturimate.database.user.Attempt_answer
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.database.user.User_profile
import com.madovygabor.maturimate.database.user.User_role
import com.madovygabor.maturimate.database.user.User_subject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
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
        taskQueries.insertOption(Task_option(1000, 100, "ANSWER", "A", "metafora", null))
        taskQueries.insertOption(Task_option(1001, 100, "ANSWER", "B", "hasonlat", null))
        taskQueries.insertKey(Task_key(100, 1, null, 1000))
        learningUnitQueries.insert(Learning_unit(50, 11, "PRACTICE", "Gyakorlás", "TOPIC", 3, 5, 10, 1, true, 0))
        learningUnitQueries.insertUnitTask(Unit_task(50, 100, 1, true))
    }

    // A phone with database version 2 gets the table of the answers by the migration 2.sqm.
    @Test
    fun migrationAddsTheTableOfTheAnswers() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn())
        UserDatabase.Schema.create(driver)
        driver.execute(null, "DROP TABLE attempt_answer", 0)

        UserDatabase.Schema.migrate(driver, 2, 3)

        val db = UserDatabase(driver)
        db.progressQueries.insertAttempt("u", "UNIT_ROUND", null, 50, 1_000)
        val attemptId = db.progressQueries.lastInsertedId().executeAsOne()
        db.progressQueries.upsertAttemptAnswer(Attempt_answer(attemptId, 100, "u", false, 0, 1, 2_000, "1=A"))
        assertEquals(false, db.progressQueries.selectAnswersOfAttempt(attemptId).executeAsOne().is_correct)
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
        db.profileQueries.upsertProfile(User_profile("guest-1", null, null, null, null, null, "HU", "2027-03-15", null))

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

    @Test
    fun subjectLevelIsB1B2OrEmpty() {
        val db = userDatabase()
        db.profileQueries.insertSubject(User_subject("user-1", 3, null, "AUTO", 1_000, "B2"))
        db.profileQueries.insertSubject(User_subject("user-1", 1, null, "AUTO", 1_000, null))
        // INSERT OR IGNORE skips the row with an unknown level
        db.profileQueries.insertSubject(User_subject("user-1", 4, null, "CHOSEN", 1_000, "C1"))

        val subjects = db.profileQueries.selectSubjects("user-1").executeAsList()
        assertEquals(mapOf(3L to "B2", 1L to null), subjects.associate { it.subject_id to it.level })
    }

    @Test
    fun onboardingEndIsStoredOnTheProfile() {
        val db = userDatabase()
        db.profileQueries.upsertProfile(User_profile("guest-1", null, null, null, null, null, "SK", null, 5_000))

        assertEquals(5_000L, db.profileQueries.selectProfile("guest-1").executeAsOne().onboarding_completed_at)
    }

    @Test
    fun migrationFromVersion1KeepsRowsAndAddsTheColumns() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn())
        // the two changed tables as version 1 created them
        driver.execute(null, """
            CREATE TABLE user_profile (
                user_id TEXT NOT NULL PRIMARY KEY, first_name TEXT, last_name TEXT, display_name TEXT,
                date_of_birth TEXT, school_type TEXT, teaching_language TEXT, graduation_date TEXT
            )
        """.trimIndent(), 0)
        driver.execute(null, """
            CREATE TABLE user_subject (
                user_id TEXT NOT NULL, subject_id INTEGER NOT NULL, target_score REAL,
                source TEXT NOT NULL DEFAULT 'CHOSEN', added_at INTEGER NOT NULL,
                PRIMARY KEY (user_id, subject_id)
            )
        """.trimIndent(), 0)
        driver.execute(null, "INSERT INTO user_profile (user_id, teaching_language) VALUES ('guest-1', 'HU')", 0)
        driver.execute(null, "INSERT INTO user_subject (user_id, subject_id, added_at) VALUES ('guest-1', 3, 1000)", 0)

        UserDatabase.Schema.migrate(driver, 1, UserDatabase.Schema.version)
        val db = UserDatabase(driver)

        val profile = db.profileQueries.selectProfile("guest-1").executeAsOne()
        assertEquals("HU", profile.teaching_language)
        assertNull(profile.onboarding_completed_at)
        assertNull(db.profileQueries.selectSubjects("guest-1").executeAsOne().level)
        assertFailsWith<Exception> {
            driver.execute(null, "UPDATE user_subject SET level = 'C1' WHERE user_id = 'guest-1'", 0)
        }
    }
}
