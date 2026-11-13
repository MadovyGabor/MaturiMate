package com.madovygabor.maturimate.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.database.user.UserDatabase
import java.io.File
import java.util.Properties

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class DatabaseDriverFactory(private val appDir: File) {

    // The schema version is kept in PRAGMA user_version; the driver creates or migrates the file
    actual fun createUserDriver(): SqlDriver {
        appDir.mkdirs()
        return JdbcSqliteDriver(
            url = "jdbc:sqlite:${File(appDir, USER_DATABASE_NAME).absolutePath}",
            properties = foreignKeysOn().apply {
                // a write that meets another one waits up to 10 s instead of the driver's 3 s, so a busy machine
                // does not turn a wait into SQLITE_BUSY; it only matters while the file is locked
                put("busy_timeout", USER_BUSY_TIMEOUT_MS.toString())
            },
            schema = UserDatabase.Schema
        )
    }

    actual fun contentStorage(): ContentStorage = FileContentStorage(appDir, JdbcSqliteRunner())

    actual fun createContentDriver(): SqlDriver =
        JdbcSqliteDriver("jdbc:sqlite:${File(appDir, CONTENT_DATABASE_NAME).absolutePath}", foreignKeysOn())
}

fun foreignKeysOn() = Properties().apply { put("foreign_keys", "true") }

/** How long a write of the desktop user database waits for another one to finish, in milliseconds. */
const val USER_BUSY_TIMEOUT_MS = 10_000
