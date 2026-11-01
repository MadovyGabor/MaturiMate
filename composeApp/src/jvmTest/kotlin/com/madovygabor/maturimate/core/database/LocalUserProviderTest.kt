package com.madovygabor.maturimate.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.userProfile.data.UserProfileRepositoryImpl
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import com.madovygabor.maturimate.features.userProfile.domain.UserSubject
import kotlinx.coroutines.Dispatchers
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
}
