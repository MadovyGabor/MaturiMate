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
import com.madovygabor.maturimate.database.user.User_role
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

    // A phone with database version 4 has the old `attempt` table (three kinds of round, whole-number ids). The
    // migrations to the newest version must keep every round and every answer, give the rounds ids of the
    // device, and accept the two practice kinds.
    @Test
    fun migrationKeepsTheRoundsAndTheAnswersAndGivesTheRoundsNewIds() {
        val driver = UserSchemaVersions.driverAt(4)
        driver.execute(null, "INSERT INTO attempt (attempt_id, user_id, attempt_type, learning_unit_id, status, started_at, finished_at, earned_points, max_points) VALUES (7, 'u', 'UNIT_ROUND', 50, 'FINISHED', 1000, 2000, 1, 2)", 0)
        driver.execute(null, "INSERT INTO attempt (attempt_id, user_id, attempt_type, topic_id, started_at) VALUES (9, 'u', 'REVIEW', 11, 3000)", 0)
        driver.execute(null, "INSERT INTO attempt_answer VALUES (7, 100, 'u', 0, 0, 1, 1500, '1=A')", 0)
        driver.execute(null, "INSERT INTO attempt_answer VALUES (7, 101, 'u', 1, 1, 1, 1600, '1=B')", 0)

        UserDatabase.Schema.migrate(driver, 4, UserDatabase.Schema.version)

        val db = UserDatabase(driver)
        val rounds = db.progressQueries.selectAttempts("u").executeAsList()
        assertEquals(setOf("UNIT_ROUND", "REVIEW"), rounds.map { it.attempt_type }.toSet())
        assertEquals(2, rounds.map { it.attempt_id }.toSet().size)
        assertTrue(rounds.none { it.attempt_id == "7" || it.attempt_id == "9" }, "the whole-number ids are replaced")
        val unitRound = rounds.first { it.attempt_type == "UNIT_ROUND" }
        assertEquals(50L, unitRound.learning_unit_id)
        assertEquals(2L, unitRound.max_points)
        // the answers follow their round to the new id
        assertEquals(
            listOf(100L to false, 101L to true),
            db.progressQueries.selectAnswersOfAttempt(unitRound.attempt_id).executeAsList().map { it.task_id to it.is_correct },
        )
        // the two practice kinds are accepted without a unit and a topic, and refused with one
        db.progressQueries.insertAttempt("a", "u", "FREE_PRACTICE", null, null, 4_000)
        db.progressQueries.insertAttempt("b", "u", "MISTAKE_PRACTICE", null, null, 5_000)
        assertFailsWith<Exception> { db.progressQueries.insertAttempt("c", "u", "FREE_PRACTICE", null, 50, 6_000) }
        assertFailsWith<Exception> { db.progressQueries.insertAttempt("d", "u", "UNIT_ROUND", null, null, 6_000) }
        // an answer still belongs to its round: removing the round removes the answers (the foreign key is back)
        driver.execute(null, "DELETE FROM attempt WHERE attempt_id = '${unitRound.attempt_id}'", 0)
        assertEquals(0, db.progressQueries.selectAnswersOfAttempt(unitRound.attempt_id).executeAsList().size)
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
    fun aRoundIsStoredUnderTheIdTheDeviceMade() {
        val db = userDatabase()
        val id = "7f3c0c52-3a6b-4a43-9d57-2b8d1d1f0a11"
        db.progressQueries.insertAttempt(id, "user-1", "UNIT_ROUND", null, 50, 1_000)
        db.progressQueries.finishAttempt("FINISHED", 2_000, 4, 5, id)

        val attempt = db.progressQueries.selectAttempts("user-1").executeAsOne()
        assertEquals(id, attempt.attempt_id)
        assertEquals(4L, attempt.earned_points)
    }

    @Test
    fun activityOfTheSameDayIsAddedUp() {
        val db = userDatabase()
        db.activityQueries.addActivity("user-1", "2026-10-28", "device-1", 3, 5)
        db.activityQueries.addActivity("user-1", "2026-10-28", "device-1", 2, 5)

        val day = db.activityQueries.selectActivity("user-1", "2026-10-28", "2026-10-28").executeAsOne()
        assertEquals(5L, day.seconds_studied)
        assertEquals(10L, day.tasks_answered)
    }

    @Test
    fun activityOfTwoDevicesIsOneDayInTheSum() {
        val db = userDatabase()
        db.activityQueries.addActivity("user-1", "2026-10-28", "device-1", 60, 2)
        db.activityQueries.addActivity("user-1", "2026-10-28", "device-2", 90, 3)

        val days = db.activityQueries.selectActivity("user-1", "2026-10-28", "2026-10-28").executeAsList()
        assertEquals(1, days.size)
        assertEquals(150L, days.single().seconds_studied)
        assertEquals(5L, days.single().tasks_answered)
    }

    @Test
    fun attemptScopeCheckRejectsUnitAndTopicTogether() {
        val db = userDatabase()
        val failure = assertFailsWith<Exception> {
            db.progressQueries.insertAttempt("round-1", "user-1", "UNIT_ROUND", 11, 50, 1_000)
        }
        assertTrue(failure.message.orEmpty().contains("CHECK"))
    }

    @Test
    fun guestProfileNeedsNoName() {
        val db = userDatabase()
        db.profileQueries.ensureProfile("guest-1")
        db.profileQueries.updateTeachingLanguage("HU", "guest-1")
        db.profileQueries.updateGraduationDate("2027-03-15", "guest-1")

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
    fun theDeviceIdIsMadeOnceAndKept() {
        val db = userDatabase()
        db.profileQueries.insertLocalDevice("device-1")
        db.profileQueries.insertLocalDevice("device-2")

        assertEquals("device-1", db.profileQueries.selectLocalDevice().executeAsOne())
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
        db.profileQueries.insertSubject("user-1", 3, "AUTO", 1_000, "B2")
        db.profileQueries.insertSubject("user-1", 1, "AUTO", 1_000, null)
        // INSERT OR IGNORE skips the row with an unknown level
        db.profileQueries.insertSubject("user-1", 4, "CHOSEN", 1_000, "C1")

        val subjects = db.profileQueries.selectSubjects("user-1").executeAsList()
        assertEquals(mapOf(3L to "B2", 1L to null), subjects.associate { it.subject_id to it.level })
    }

    @Test
    fun onboardingEndIsStoredOnTheProfile() {
        val db = userDatabase()
        db.profileQueries.ensureProfile("guest-1")
        db.profileQueries.updateOnboardingCompletedAt(5_000, "guest-1")

        assertEquals(5_000L, db.profileQueries.selectProfile("guest-1").executeAsOne().onboarding_completed_at)
    }

    @Test
    fun migrationFromVersion1KeepsRowsAndAddsTheColumns() {
        val driver = UserSchemaVersions.driverAt(1)
        driver.execute(null, "INSERT INTO activity_log (user_id, activity_date, minutes_studied) VALUES ('guest-1', '2026-11-01', 4)", 0)
        driver.execute(null, "INSERT INTO user_profile (user_id, teaching_language, school_type) VALUES ('guest-1', 'HU', 'GYMNASIUM')", 0)
        driver.execute(null, "INSERT INTO user_subject (user_id, subject_id, added_at) VALUES ('guest-1', 3, 1000)", 0)
        driver.execute(null, "INSERT INTO user_settings (user_id, theme) VALUES ('guest-1', 'DARK')", 0)

        UserDatabase.Schema.migrate(driver, 1, UserDatabase.Schema.version)
        val db = UserDatabase(driver)

        val profile = db.profileQueries.selectProfile("guest-1").executeAsOne()
        assertEquals("HU", profile.teaching_language)
        assertNull(profile.onboarding_completed_at)
        assertNull(profile.progress_reset_at)
        assertTrue(profile.updated_at > 0, "a kept row gets the time of the migration")
        assertEquals("Europe/Bratislava", db.profileQueries.selectSettings("guest-1").executeAsOne().timezone)
        // 4 minutes became 240 seconds, on the device of this installation
        val day = db.activityQueries.selectActivity("guest-1", "2026-11-01", "2026-11-01").executeAsOne()
        assertEquals(240L, day.seconds_studied)
        assertTrue(db.profileQueries.selectLocalDevice().executeAsOne().isNotBlank())
        assertNull(db.profileQueries.selectSubjects("guest-1").executeAsOne().level)
        assertFailsWith<Exception> {
            driver.execute(null, "UPDATE user_subject SET level = 'C1' WHERE user_id = 'guest-1'", 0)
        }
    }

    @Test
    fun migrationFromVersion6KeepsSubjectsWithoutAMarker() {
        val driver = UserSchemaVersions.driverAt(6)
        driver.execute(null, "INSERT INTO user_subject (user_id, subject_id, source, added_at, level) VALUES ('u', 1, 'AUTO', 1000, NULL)", 0)
        driver.execute(null, "INSERT INTO user_subject (user_id, subject_id, source, added_at, level) VALUES ('u', 3, 'CHOSEN', 2000, 'B2')", 0)

        UserDatabase.Schema.migrate(driver, 6, UserDatabase.Schema.version)
        val db = UserDatabase(driver)

        val subjects = db.profileQueries.selectSubjects("u").executeAsList()
        assertEquals(listOf(1L, 3L), subjects.map { it.subject_id })
        assertTrue(subjects.all { it.removed_at == null && it.updated_at > 0 })
        assertEquals("B2", subjects.last().level)
    }

    @Test
    fun aDroppedSubjectIsMarkedAndCanComeBack() {
        val db = userDatabase()
        db.profileQueries.insertSubject("u", 2, "CHOSEN", 1_000, null)
        db.profileQueries.deleteSubject("u", 2)
        assertTrue(db.profileQueries.selectSubjects("u").executeAsList().isEmpty())

        // the row is still there, marked
        val row = db.profileQueries.selectSubjects("u").executeAsList()
        assertTrue(row.isEmpty())
        db.profileQueries.insertSubject("u", 2, "CHOSEN", 5_000, null)
        db.profileQueries.reviveSubject("CHOSEN", null, 5_000, "u", 2)
        val back = db.profileQueries.selectSubjects("u").executeAsOne()
        assertNull(back.removed_at)
        assertEquals(5_000L, back.added_at)
    }
}
