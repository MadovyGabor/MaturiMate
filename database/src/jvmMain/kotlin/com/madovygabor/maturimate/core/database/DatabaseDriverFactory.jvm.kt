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
            properties = foreignKeysOn(),
            schema = UserDatabase.Schema
        )
    }

    actual fun contentStorage(): ContentStorage = FileContentStorage(appDir)

    actual fun createContentDriver(): SqlDriver =
        JdbcSqliteDriver("jdbc:sqlite:${File(appDir, CONTENT_DATABASE_NAME).absolutePath}", foreignKeysOn())
}

fun foreignKeysOn() = Properties().apply { put("foreign_keys", "true") }
