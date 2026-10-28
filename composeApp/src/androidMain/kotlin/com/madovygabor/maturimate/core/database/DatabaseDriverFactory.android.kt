package com.madovygabor.maturimate.core.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.madovygabor.maturimate.database.user.UserDatabase

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class DatabaseDriverFactory(private val context: Context) {

    actual fun createUserDriver(): SqlDriver =
        AndroidSqliteDriver(
            schema = UserDatabase.Schema,
            context = context,
            name = USER_DATABASE_NAME,
            callback = object : AndroidSqliteDriver.Callback(UserDatabase.Schema) {
                // SQLite ignores ON DELETE rules unless foreign keys are enabled per connection
                override fun onOpen(db: SupportSQLiteDatabase) {
                    db.setForeignKeyConstraintsEnabled(true)
                }
            }
        )
}
