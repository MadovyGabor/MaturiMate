package com.madovygabor.maturimate.core.database

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.madovygabor.maturimate.database.Database

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
/**
 * Android-specific implementation of the DatabaseDriverFactory.
 * Leverages the Android context to initialize the SQLite driver and manage database lifecycle.
 *
 * @property context The Android application context required by the [AndroidSqliteDriver].
 */
actual class DatabaseDriverFactory(private val context: Context) {

    /**
     * Creates and returns an [SqlDriver] for Android.
     * Uses the generated [Database.Schema] to handle table creation and migrations.
     *
     * @return A configured [AndroidSqliteDriver] instance named "maturimate.db".
     */
    actual fun createDriver(): SqlDriver {
        // The AndroidSqliteDriver handles the underlying SQLiteOpenHelper logic,
        // including automatic schema creation and version management on Android.
        return AndroidSqliteDriver(
            schema = Database.Schema,
            context = context,
            name = "maturimate.db"
        )
    }
}