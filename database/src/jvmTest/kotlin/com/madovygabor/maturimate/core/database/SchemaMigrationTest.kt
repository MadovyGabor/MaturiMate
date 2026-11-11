package com.madovygabor.maturimate.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.database.user.UserDatabase
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A phone that upgrades must end with the very schema a fresh install has. The `.sq` files (fresh install) and the
 * `.sqm` files (upgrade) are written by hand, twice; this test is what keeps them in step. A change to the user
 * database is a new `.sqm` file *and* a change to the `.sq` files, and this test stays green (roadmap 11.1e).
 */
class SchemaMigrationTest {

    private fun freshSchema(): Map<String, String> {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn())
        UserDatabase.Schema.create(driver)
        return UserSchemaVersions.schemaOf(driver)
    }

    @Test
    fun everyOlderVersionMigratesToTheSchemaOfAFreshInstall() {
        val fresh = freshSchema()
        for (version in 1L until UserDatabase.Schema.version) {
            val driver = UserSchemaVersions.driverAt(version)
            UserDatabase.Schema.migrate(driver, version, UserDatabase.Schema.version)
            val migrated = UserSchemaVersions.schemaOf(driver)
            assertEquals(fresh.keys, migrated.keys, "tables and indexes after migrating from version $version")
            for ((name, sql) in fresh) assertEquals(sql, migrated[name], "$name after migrating from version $version")
        }
    }
}
