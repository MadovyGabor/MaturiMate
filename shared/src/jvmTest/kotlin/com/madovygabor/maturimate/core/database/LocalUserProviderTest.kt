package com.madovygabor.maturimate.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.database.user.Attempt_answer
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.userProfile.data.UserProfileRepositoryImpl
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import com.madovygabor.maturimate.features.userProfile.domain.UserSubject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalUserProviderTest {

    private val database = UserDatabase(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { UserDatabase.Schema.create(it) }
    )

    @Test
    fun firstCallCreatesAGuestId() {
        val id = LocalUserProvider(database).userId()

        assertTrue(id.startsWith(LocalUserProvider.GUEST_PREFIX))
        assertTrue(database.profileQueries.selectLocalUser().executeAsOne().is_guest)
    }

    @Test
    fun theIdStaysTheSameAcrossInstances() {
        val first = LocalUserProvider(database).userId()
        val second = LocalUserProvider(database) { "guest-other" }.userId()

        assertEquals(first, second)
    }

    @Test
    fun adoptingAnAccountMovesEveryGuestRow() = runBlocking {
        val provider = LocalUserProvider(database) { "guest-1" }
        val profiles = UserProfileRepositoryImpl(database, provider, Dispatchers.Unconfined)
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        profiles.saveRole(UserRole.STUDENT)
        profiles.saveSubjects(listOf(UserSubject(1, compulsory = true)))
        database.activityQueries.addActivity("guest-1", "2026-10-31", 10, 5, 3)

        provider.adoptAccount("account-1")

        assertEquals("account-1", provider.userId())
        assertFalse(provider.isGuest())
        assertEquals("HU", database.profileQueries.selectProfile("account-1").executeAsOne().teaching_language)
        assertEquals(listOf(1L), database.profileQueries.selectRoles("account-1").executeAsList().map { it.role_id })
        assertEquals(1, database.profileQueries.selectSubjects("account-1").executeAsList().size)
        assertEquals(10L, database.activityQueries.selectTotalXp("account-1").executeAsOne())
        assertNull(database.profileQueries.selectProfile("guest-1").executeAsOneOrNull())
        // the profile follows the new id
        assertEquals(UserRole.STUDENT, withTimeout(5_000) { profiles.profile.first { it?.role != null } }?.role)
    }

    @Test
    fun adoptingAnAccountMovesTheAnswersOfTheGuestsRounds() = runBlocking {
        val provider = LocalUserProvider(database) { "guest-1" }
        database.progressQueries.insertAttempt("guest-1", "UNIT_ROUND", null, 51, 1_000)
        val attemptId = database.progressQueries.lastInsertedId().executeAsOne()
        database.progressQueries.upsertAttemptAnswer(Attempt_answer(attemptId, 100, "guest-1", true, 2, 2, 2_000, "1=B"))

        provider.adoptAccount("account-1")

        assertEquals("account-1", database.progressQueries.selectAnswersOfAttempt(attemptId).executeAsOne().user_id)
        assertEquals(1, database.progressQueries.selectAttempts("account-1").executeAsList().size)
    }

    @Test
    fun anotherAccountDoesNotGetThePreviousAccountsAnswers() = runBlocking {
        val provider = LocalUserProvider(database) { "guest-1" }
        database.progressQueries.insertAttempt("guest-1", "UNIT_ROUND", null, 51, 1_000)
        val attemptId = database.progressQueries.lastInsertedId().executeAsOne()
        database.progressQueries.upsertAttemptAnswer(Attempt_answer(attemptId, 100, "guest-1", true, 2, 2, 2_000, null))
        provider.adoptAccount("account-a")

        provider.adoptAccount("account-b")

        assertEquals(0, database.progressQueries.selectAnswersOfAttempt(attemptId).executeAsList().size)
    }

    // Signing in with a second account on the account step used to hand it the first account's rows.
    @Test
    fun anotherAccountDoesNotGetThePreviousAccountsRows() = runBlocking {
        val provider = LocalUserProvider(database) { "guest-1" }
        val profiles = UserProfileRepositoryImpl(database, provider, Dispatchers.Unconfined)
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        provider.adoptAccount("account-a")
        database.activityQueries.addActivity("account-a", "2026-10-31", 10, 5, 3)

        provider.adoptAccount("account-b")

        assertEquals("account-b", provider.userId())
        assertFalse(provider.isGuest())
        assertNull(database.profileQueries.selectProfile("account-b").executeAsOneOrNull())
        assertNull(database.profileQueries.selectProfile("account-a").executeAsOneOrNull())
        assertEquals(0L, database.activityQueries.selectTotalXp("account-b").executeAsOne())
        assertEquals(0L, database.activityQueries.selectTotalXp("account-a").executeAsOne())
    }

    // A save used to read the id before its transaction, so a sign-up moving the rows in between
    // made it write to the old guest id, where the answer was lost.
    @Test
    fun savesDuringASignUpEndUpUnderTheAccount() = runBlocking {
        val dispatcher = Dispatchers.IO.limitedParallelism(1)
        val provider = LocalUserProvider(database) { "guest-1" }
        val profiles = UserProfileRepositoryImpl(database, provider, dispatcher)

        val saves = (1..30).map { minutes -> async(Dispatchers.Default) { profiles.saveDailyGoal(minutes) } }
        val adoption = async(Dispatchers.Default) { withContext(dispatcher) { provider.adoptAccount("account-1") } }
        (saves + adoption).awaitAll()

        assertNull(database.profileQueries.selectProfile("guest-1").executeAsOneOrNull())
        assertNull(database.profileQueries.selectSettings("guest-1").executeAsOneOrNull())
        assertTrue(database.profileQueries.selectSettings("account-1").executeAsOne().daily_goal_minutes in 1L..30L)
    }
}
