package com.madovygabor.maturimate.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.database.Database
import java.io.File

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
/**
 * Desktop-specific implementation of the DatabaseDriverFactory.
 * This class is responsible for locating the database file on the host machine
 * and initializing the SQLite driver for the JVM environment.
 */
actual class DatabaseDriverFactory {

    /**
     * Creates and configures the [SqlDriver] for the Desktop platform.
     * It ensures the storage directory exists and triggers schema creation for new databases.
     * * @return A configured [SqlDriver] pointing to the local SQLite file.
     */
    actual fun createDriver(): SqlDriver {
        // Resolve the database file path: C:/Users/{User}/.maturimate/maturimate.db
        // Using a hidden folder (.) is standard practice for application data on Desktop.
        val dbFile = File(System.getProperty("user.home"), ".maturimate/maturimate.db")

        // Ensure the parent directory exists; create it if it doesn't.
        val parentDir = dbFile.parentFile
        if (!parentDir.exists()) {
            parentDir.mkdirs()
        }

        // Note: Some drivers might create the file immediately upon instantiation.
        // We check existence to determine if we need to run the initial schema creation.
        val isNewDatabase = !dbFile.exists()

        val driver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")

        // On Desktop/JVM, SQLDelight requires manual schema initialization for fresh files.
        if (isNewDatabase) {
            Database.Schema.create(driver)
        }

        return driver
    }
}