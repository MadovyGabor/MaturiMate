package com.madovygabor.maturimate.features.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.sync.data.SyncStoreImpl
import com.madovygabor.maturimate.features.sync.data.mergeStreak
import com.madovygabor.maturimate.features.sync.domain.SyncActivity
import com.madovygabor.maturimate.features.sync.domain.SyncAnswer
import com.madovygabor.maturimate.features.sync.domain.SyncAttempt
import com.madovygabor.maturimate.features.sync.domain.SyncExamDate
import com.madovygabor.maturimate.features.sync.domain.SyncChanges
import com.madovygabor.maturimate.features.sync.domain.SyncProfile
import com.madovygabor.maturimate.features.sync.domain.SyncResponse
import com.madovygabor.maturimate.features.sync.domain.SyncSettings
import com.madovygabor.maturimate.features.sync.domain.SyncStreak
import com.madovygabor.maturimate.features.sync.domain.SyncSubject
import com.madovygabor.maturimate.features.sync.domain.SyncUnitProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncStoreImplTest {

    private val database = UserDatabase(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) }
    )
    private val localUser = LocalUserProvider(database, newDeviceId = { "dev-1" }, newGuestId = { "guest-1" })
    private val store = SyncStoreImpl(database, localUser, Dispatchers.Unconfined)
    private val userId get() = localUser.userId()

    private fun response(
        cursor: String = "c1",
        profile: SyncProfile? = null,
        settings: SyncSettings? = null,
        roles: List<Long> = emptyList(),
        subjects: List<SyncSubject> = emptyList(),
        attempts: List<SyncAttempt> = emptyList(),
        units: List<SyncUnitProgress> = emptyList(),
        streak: SyncStreak? = null,
        activity: List<SyncActivity> = emptyList(),
    ) = SyncResponse(
        serverTime = 1000, cursor = cursor, profile = profile, settings = settings, roles = roles, subjects = subjects,
        attempts = attempts, unitProgress = units, streak = streak, activity = activity,
    )

    private fun round(id: String, startedAt: Long = 100, answers: List<SyncAnswer> = emptyList(), unit: Long = 5) = SyncAttempt(
        attemptId = id, attemptType = "UNIT_ROUND", learningUnitId = unit, status = "FINISHED", startedAt = startedAt,
        finishedAt = startedAt + 10, earnedPoints = 1, maxPoints = 1, answers = answers,
    )

    private fun answer(task: Long, correct: Boolean, at: Long) = SyncAnswer(task, correct, if (correct) 1 else 0, 1, at, "1=A")

    private fun apply(r: SyncResponse, guestMerge: Boolean = false, pushedUpTo: Long? = null) =
        runBlocking { store.apply(r, guestMerge, pushedUpTo, now = 9_000) }

    @Test
    fun aPhoneWithoutAProfileTakesTheServersProfileAsItIs() {
        apply(response(profile = SyncProfile("Anna", "Kovács", "HU", "2027-03-15", 50, null, 1, updatedAt = 500), roles = listOf(1)))

        val p = database.profileQueries.selectProfile(userId).executeAsOne()
        assertEquals("Anna", p.first_name)
        assertEquals(500L, p.updated_at)
        assertEquals(listOf(1L), database.profileQueries.selectRoles(userId).executeAsList().map { it.role_id })
    }

    @Test
    fun theNewerChangeWinsTheProfileAndTheSettings() {
        val profile = { name: String, at: Long -> SyncProfile(name, "K", "HU", null, null, null, null, at) }
        val settings = { goal: Long, at: Long -> SyncSettings("HU", goal, false, null, at) }
        apply(response(profile = profile("Old", 100), settings = settings(10, 100)))

        apply(response(profile = profile("Newer", 200), settings = settings(20, 200)))
        assertEquals("Newer", database.profileQueries.selectProfile(userId).executeAsOne().first_name)
        assertEquals(20L, database.profileQueries.selectSettings(userId).executeAsOne().daily_goal_minutes)

        // an older change from the server loses
        apply(response(profile = profile("Older", 150), settings = settings(15, 150)))
        assertEquals("Newer", database.profileQueries.selectProfile(userId).executeAsOne().first_name)
        assertEquals(20L, database.profileQueries.selectSettings(userId).executeAsOne().daily_goal_minutes)
    }

    @Test
    fun theTimeZoneOfThePhoneIsNeverOverwritten() {
        database.profileQueries.ensureSettings(userId, "America/New_York")
        apply(response(settings = SyncSettings("SK", 30, false, null, updatedAt = Long.MAX_VALUE / 2)))
        assertEquals("America/New_York", database.profileQueries.selectSettings(userId).executeAsOne().timezone)
    }

    @Test
    fun theGuestMergeAppliesTheAccountsRowsWhateverTheTimesSay() {
        database.profileQueries.ensureProfile(userId)
        database.profileQueries.updateTeachingLanguage("SK", userId)
        database.profileQueries.ensureSettings(userId, "Europe/Bratislava")
        database.profileQueries.updateDailyGoal(99, userId)

        apply(
            response(
                profile = SyncProfile("Anna", "K", "HU", null, null, null, null, updatedAt = 10),
                settings = SyncSettings("HU", 12, false, null, updatedAt = 10),
            ),
            guestMerge = true,
        )

        assertEquals("HU", database.profileQueries.selectProfile(userId).executeAsOne().teaching_language)
        assertEquals(12L, database.profileQueries.selectSettings(userId).executeAsOne().daily_goal_minutes)
        assertFalse(runBlocking { store.state() }.guestMergePending)
    }

    @Test
    fun roundsAndAnswersAreAddedAndTheTaskCacheIsRebuilt() {
        apply(response(attempts = listOf(
            round("a", 100, listOf(answer(1, false, 101), answer(2, true, 102))),
            round("b", 200, listOf(answer(1, true, 201))),
        )))
        // the same again changes nothing
        apply(response(attempts = listOf(round("a", 100, listOf(answer(1, false, 101))))))

        assertEquals(2, database.progressQueries.selectAttempts(userId).executeAsList().size)
        assertEquals(3, database.progressQueries.selectAnswersOfAttempt("a").executeAsList().size + database.progressQueries.selectAnswersOfAttempt("b").executeAsList().size)
        val task1 = database.progressQueries.selectTaskProgress(userId, 1).executeAsOne()
        assertEquals(1L, task1.times_correct)
        assertEquals(1L, task1.times_wrong)
        // wrong first, right after: one right in a row
        assertEquals(1L, task1.correct_streak)
        assertEquals(201L, task1.last_answered_at)
    }

    @Test
    fun theFurthestUnitProgressWinsAndTheEarlierTimesStay() {
        apply(response(units = listOf(SyncUnitProgress(5, "COMPLETED", 100, 200, 200))))
        apply(response(units = listOf(SyncUnitProgress(5, "IN_PROGRESS", 50, null, 300))))

        val u = database.progressQueries.selectUnitProgressOf(userId, 5).executeAsOne()
        assertEquals("COMPLETED", u.status)
        assertEquals(50L, u.started_at)
        assertEquals(200L, u.completed_at)
        assertEquals(300L, u.updated_at)
    }

    @Test
    fun theStreakRuleKeepsTheLongestAndTakesTheLaterDay() {
        val local = SyncStreak(3, 5, "2026-11-10", 1)
        assertEquals(3, mergeStreak(local, SyncStreak(1, 1, "2026-11-09", 2)).currentStreak)
        assertEquals(4, mergeStreak(local, SyncStreak(4, 4, "2026-11-11", 2)).currentStreak)
        assertEquals(5, mergeStreak(local, SyncStreak(4, 4, "2026-11-11", 2)).longestStreak)
        assertEquals(6, mergeStreak(local, SyncStreak(6, 6, "2026-11-10", 2)).currentStreak)
        assertEquals(6, mergeStreak(local, SyncStreak(6, 6, "2026-11-10", 2)).longestStreak)
        assertEquals(local, mergeStreak(null, local))
    }

    @Test
    fun theSameExamDatesAreNotWrittenAgain() {
        val dates = listOf(SyncExamDate(1, 2027, null, "2027-03-16", 100, null))
        apply(response().copy(examDates = dates))
        var writes = 0
        val query = database.examDateQueries.selectAll()
        val listener = app.cash.sqldelight.Query.Listener { writes++ }
        query.addListener(listener)

        // every sync brings the dates: unchanged, the home screen is not reloaded
        apply(response(cursor = "c2").copy(examDates = dates))
        assertEquals(0, writes)
        // a correction is written
        apply(response(cursor = "c3").copy(examDates = listOf(dates.single().copy(examOn = "2027-03-17", publishedAt = 200))))
        assertEquals(1, writes)
        assertEquals("2027-03-17", query.executeAsOne().exam_on)
        query.removeListener(listener)
    }

    @Test
    fun theMinutesOfAnotherDeviceCountHereButAreNeverSentBack() {
        database.activityQueries.addActivity(userId, "2026-11-10", "dev-1", 300, 5)
        apply(response(activity = listOf(SyncActivity("2026-11-10", "dev-2", 1200, 10, 1))))

        // the day counts both devices (the phone and the computer)
        assertEquals(1500L, database.activityQueries.selectActivity(userId, "2026-11-10", "2026-11-10").executeAsOne().seconds_studied)
        // a full push sends only this device's row: the server would refuse the other one
        val sent = runBlocking { store.pending(since = null, full = true, maxRounds = 10) }.changes.activity
        assertEquals(listOf("dev-1"), sent.map { it.deviceId })
    }

    @Test
    fun theDailyMinutesOfEveryDeviceAreKeptAndOnlyGrow() {
        database.activityQueries.addActivity(userId, "2026-11-10", "dev-1", 300, 5)
        apply(response(activity = listOf(
            SyncActivity("2026-11-10", "dev-1", 200, 3, 1),
            SyncActivity("2026-11-10", "dev-2", 100, 2, 1),
        )))

        val day = database.activityQueries.selectActivity(userId, "2026-11-10", "2026-11-10").executeAsOne()
        assertEquals(400L, day.seconds_studied)
        assertEquals(7L, day.tasks_answered)
        apply(response(activity = listOf(SyncActivity("2026-11-10", "dev-1", 900, 20, 2))))
        assertEquals(1000L, database.activityQueries.selectActivity(userId, "2026-11-10", "2026-11-10").executeAsOne().seconds_studied)
    }

    @Test
    fun aResetFromTheServerDeletesOlderRoundsAndKeepsTheStreakAndMinutes() {
        apply(response(attempts = listOf(round("old", 100, listOf(answer(1, true, 101))), round("new", 900, listOf(answer(2, true, 901))))))
        database.activityQueries.addActivity(userId, "2026-11-10", "dev-1", 300, 5)
        apply(response(streak = SyncStreak(3, 3, "2026-11-10", 1)))

        apply(response(profile = SyncProfile("A", "K", "HU", null, null, progressResetAt = 500, null, updatedAt = 600)))

        assertEquals(listOf("new"), database.progressQueries.selectAttempts(userId).executeAsList().map { it.attempt_id })
        assertTrue(database.progressQueries.selectAnswersOfAttempt("old").executeAsList().isEmpty())
        assertNull(database.progressQueries.selectTaskProgress(userId, 1).executeAsOneOrNull())
        assertEquals(500L, database.profileQueries.selectProfile(userId).executeAsOne().progress_reset_at)
        assertEquals(3L, database.activityQueries.selectStreak(userId).executeAsOne().current_streak)
        assertEquals(300L, database.activityQueries.selectActivity(userId, "2026-11-10", "2026-11-10").executeAsOne().seconds_studied)
    }

    @Test
    fun subjectsAreMergedByTheNewerChangeAndKeepTheEarlierAddedAt() {
        apply(response(subjects = listOf(SyncSubject(1, "CHOSEN", null, 100, null, 100))))
        apply(response(subjects = listOf(SyncSubject(1, "CHOSEN", null, 50, removedAt = 300, updatedAt = 300))))

        assertTrue(database.profileQueries.selectSubjects(userId).executeAsList().isEmpty())
        // brought back by a still newer change
        apply(response(subjects = listOf(SyncSubject(1, "CHOSEN", null, 80, null, 400))))
        val s = database.profileQueries.selectSubjects(userId).executeAsOne()
        assertEquals(50L, s.added_at)
    }

    @Test
    fun theCursorMovesOnlyWithAnAppliedResponse() {
        assertNull(runBlocking { store.state() }.cursor)

        // a response whose settings break a constraint (an unknown language) rolls the profile back too, the cursor stays
        apply(response(profile = SyncProfile("Old", "K", "HU", null, null, null, null, 100), settings = SyncSettings("HU", 10, false, null, 100)))
        val broken = response(
            cursor = "c2",
            profile = SyncProfile("Changed", "K", "HU", null, null, null, null, 200),
            settings = SyncSettings("XX", 10, false, null, 200),
        )
        assertFailsWith<Exception> { apply(broken) }
        assertEquals("c1", runBlocking { store.state() }.cursor)
        assertEquals("Old", database.profileQueries.selectProfile(userId).executeAsOne().first_name)

        apply(response(cursor = "c3"), pushedUpTo = 777)
        val state = runBlocking { store.state() }
        assertEquals("c3", state.cursor)
        assertEquals(777L, state.lastPushAt)
        assertEquals(9_000L, state.lastSuccessAt)
        assertFalse(state.fullPushPending)
    }

    @Test
    fun pendingHoldsWhatChangedAndTheNewestTimeSent() {
        database.profileQueries.ensureProfile(userId)
        database.profileQueries.updateTeachingLanguage("HU", userId)
        database.progressQueries.insertAttempt("r1", userId, "UNIT_ROUND", null, 5, 100)
        database.progressQueries.finishAttempt("FINISHED", 120, 1, 1, "r1")
        database.progressQueries.insertAttempt("r2", userId, "UNIT_ROUND", null, 5, 200)

        val all = runBlocking { store.pending(since = null, full = true, maxRounds = 10) }
        // the round in progress does not travel
        assertEquals(listOf("r1"), all.changes.attempts.map { it.attemptId })
        assertEquals("HU", all.changes.profile?.teachingLanguage)
        assertFalse(all.moreRounds)
        assertTrue(all.pushedUpTo!! >= 120)

        // nothing changed after the mark
        val none = runBlocking { store.pending(since = all.pushedUpTo, full = false, maxRounds = 10) }
        assertTrue(none.changes.isEmpty)

        // two rounds, a batch of one: the mark stops at the round sent
        database.progressQueries.finishAttempt("FINISHED", 220, 1, 1, "r2")
        val batch = runBlocking { store.pending(since = null, full = true, maxRounds = 1) }
        assertTrue(batch.moreRounds)
        assertEquals(120L, batch.pushedUpTo)
    }
}
