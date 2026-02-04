package com.madovygabor.maturimate.core.database

import app.cash.sqldelight.db.SqlDriver
import com.madovygabor.maturimate.database.Database


@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

fun createDatabase(driverFactory: DatabaseDriverFactory): Database {
    return Database(driverFactory.createDriver())
}