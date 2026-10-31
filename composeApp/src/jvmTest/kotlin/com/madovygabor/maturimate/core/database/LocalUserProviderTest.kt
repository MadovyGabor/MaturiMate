package com.madovygabor.maturimate.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.database.user.UserDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
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
}
