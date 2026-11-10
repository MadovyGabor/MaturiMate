package com.madovygabor.maturimate.core.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.database.user.UserDatabase

/**
 * User databases as older versions of the app created them, for the migration tests. Version 1 comes from
 * `user-schema-v1.sql` (a copy of the schema at that time, never edited); a later version is version 1 migrated
 * with the files `1.sqm` and on, which are the very files a phone runs.
 */
object UserSchemaVersions {

    /** An in-memory database in the shape of [version] (1 to [UserDatabase.Schema.version]). */
    fun driverAt(version: Long): JdbcSqliteDriver {
        require(version in 1..UserDatabase.Schema.version) { "no such version: $version" }
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn())
        statementsOfVersion1().forEach { driver.execute(null, it, 0) }
        if (version > 1) UserDatabase.Schema.migrate(driver, 1, version)
        return driver
    }

    private fun statementsOfVersion1(): List<String> {
        val text = checkNotNull(UserSchemaVersions::class.java.getResource("/user-schema-v1.sql")) { "user-schema-v1.sql is missing" }.readText()
        return text.lines().joinToString("\n") { it.substringBefore("--") }.split(';').map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * The schema of [driver] as text, one entry per table, index and trigger, so two databases can be compared:
     * comments, quotes, case and spacing are left out (SQLite keeps the comments of a CREATE TABLE and quotes the name
     * of a renamed table).
     */
    fun schemaOf(driver: JdbcSqliteDriver): Map<String, String> {
        val result = linkedMapOf<String, String>()
        driver.executeQuery(
            null,
            "SELECT name, sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' ORDER BY name",
            { cursor ->
                while (cursor.next().value) result[cursor.getString(0)!!] = normalise(cursor.getString(1)!!)
                QueryResult.Unit
            },
            0,
        )
        return result
    }

    private fun normalise(sql: String): String = sql.lines().joinToString(" ") { it.substringBefore("--") }
        .replace("\"", "")
        .lowercase()
        .replace(Regex("\\s+"), " ")
        .replace(Regex("\\s*([(),])\\s*"), "$1")
        .trim()
}
