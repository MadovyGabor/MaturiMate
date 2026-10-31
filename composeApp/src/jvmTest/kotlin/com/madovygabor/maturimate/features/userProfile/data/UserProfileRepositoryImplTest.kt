package com.madovygabor.maturimate.features.userProfile.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.userProfile.domain.DataError
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class UserProfileRepositoryImplTest {

    private val database = UserDatabase(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) }
    )
    private val localUser = LocalUserProvider(database, newGuestId = { "guest-1" })
    private val repository = UserProfileRepositoryImpl(database, localUser, Dispatchers.Unconfined, timeZone = { "Europe/Budapest" })

    private fun OperationResult<Unit, DataError>.ok() {
        if (this is OperationResult.Error) fail("expected success, got $error")
    }

    private suspend fun current(): UserProfile = repository.profile.first() ?: fail("expected a profile")

    @Test
    fun noProfileBeforeTheFirstSave() = runBlocking {
        assertNull(repository.profile.first())
    }

    @Test
    fun firstSaveCreatesProfileAndSettings() = runBlocking {
        repository.saveGraduationDate(LocalDate(2027, 3, 15)).ok()

        val profile = current()
        assertEquals(LocalDate(2027, 3, 15), profile.graduationDate)
        assertNull(profile.teachingLanguage)
        assertEquals("Europe/Budapest", profile.timezone)
        assertEquals(10, profile.dailyGoalMinutes)
        assertFalse(profile.isOnboardingCompleted)
    }

    @Test
    fun teachingLanguageAlsoSetsTheAppLanguage() = runBlocking {
        repository.saveTeachingLanguage(TeachingLanguage.HU).ok()

        val profile = current()
        assertEquals(TeachingLanguage.HU, profile.teachingLanguage)
        assertEquals(TeachingLanguage.HU, profile.uiLanguage)
    }

    @Test
    fun eachSaveKeepsTheOtherFields() = runBlocking {
        repository.saveTeachingLanguage(TeachingLanguage.SK).ok()
        repository.saveGraduationDate(LocalDate(2027, 3, 15)).ok()
        repository.saveDailyGoal(20).ok()
        repository.saveGraduationDate(LocalDate(2028, 3, 14)).ok()

        val profile = current()
        assertEquals(TeachingLanguage.SK, profile.teachingLanguage)
        assertEquals(LocalDate(2028, 3, 14), profile.graduationDate)
        assertEquals(20, profile.dailyGoalMinutes)
    }

    @Test
    fun onboardingEndIsSaved() = runBlocking {
        repository.completeOnboarding(Instant.fromEpochMilliseconds(5_000)).ok()

        val profile = current()
        assertTrue(profile.isOnboardingCompleted)
        assertEquals(Instant.fromEpochMilliseconds(5_000), profile.onboardingCompletedAt)
    }

    @Test
    fun rowsBelongToTheLocalGuest() = runBlocking {
        repository.saveTeachingLanguage(TeachingLanguage.HU).ok()

        assertEquals("guest-1", database.profileQueries.selectLocalUser().executeAsOne().user_id)
        assertEquals("HU", database.profileQueries.selectProfile("guest-1").executeAsOne().teaching_language)
    }

    @Test
    fun profileFlowFollowsChanges() = runBlocking {
        repository.saveDailyGoal(10).ok()
        val changed = async { repository.profile.first { it?.dailyGoalMinutes == 15 } }

        repository.saveDailyGoal(15).ok()

        assertEquals(15, withTimeout(5_000) { changed.await() }?.dailyGoalMinutes)
    }

    @Test
    fun invalidValueIsAnIoError() = runBlocking {
        // ck_user_settings_goal allows 1 to 240 minutes
        assertEquals(OperationResult.Error(DataError.IO_ERROR), repository.saveDailyGoal(0))
        assertNull(repository.profile.first())
    }
}
