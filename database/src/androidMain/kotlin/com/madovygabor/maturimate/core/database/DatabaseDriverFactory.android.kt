package com.madovygabor.maturimate.core.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.madovygabor.maturimate.database.content.ContentDatabase
import com.madovygabor.maturimate.database.user.UserDatabase

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class DatabaseDriverFactory(private val context: Context) {

    actual fun createUserDriver(): SqlDriver = driver(UserDatabase.Schema, USER_DATABASE_NAME)

    actual fun contentStorage(): ContentStorage =
        FileContentStorage(context.getDatabasePath(CONTENT_DATABASE_NAME).parentFile!!)

    // the exported file carries PRAGMA user_version = schema version, so the helper neither creates nor migrates it
    actual fun createContentDriver(): SqlDriver = driver(ContentDatabase.Schema, CONTENT_DATABASE_NAME)

    private fun driver(schema: SqlSchema<QueryResult.Value<Unit>>, name: String): SqlDriver =
        AndroidSqliteDriver(
            schema = schema,
            context = context,
            name = name,
            callback = object : AndroidSqliteDriver.Callback(schema) {
                // SQLite ignores ON DELETE rules unless foreign keys are enabled per connection
                override fun onOpen(db: SupportSQLiteDatabase) {
                    db.setForeignKeyConstraintsEnabled(true)
                }
            }
        )
}
