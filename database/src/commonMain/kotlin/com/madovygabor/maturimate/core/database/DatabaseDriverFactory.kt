package com.madovygabor.maturimate.core.database

import app.cash.sqldelight.db.SqlDriver
import com.madovygabor.maturimate.database.user.UserDatabase

/**
 * Creates the platform-specific SQLite drivers.
 * Every driver opens its connection with foreign keys enabled.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class DatabaseDriverFactory {
    fun createUserDriver(): SqlDriver
}

const val USER_DATABASE_NAME = "maturimate_user.db"

fun createUserDatabase(driverFactory: DatabaseDriverFactory): UserDatabase =
    UserDatabase(driverFactory.createUserDriver())
