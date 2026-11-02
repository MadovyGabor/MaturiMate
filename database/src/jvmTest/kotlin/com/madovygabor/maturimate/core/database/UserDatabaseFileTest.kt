package com.madovygabor.maturimate.core.database

import com.madovygabor.maturimate.database.user.UserDatabase
import java.nio.file.Files
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

/** The desktop user database file, as the app opens it. */
class UserDatabaseFileTest {

    // The driver opens one connection per thread; a write that meets another thread's write
    // waits (sqlite-jdbc's default busy timeout is 3 seconds) instead of failing with SQLITE_BUSY.
    @Test
    fun writesFromManyThreadsAllSucceed() {
        val dir = Files.createTempDirectory("maturimate-user-db").toFile()
        val database = UserDatabase(DatabaseDriverFactory(dir).createUserDriver())
        val pool = Executors.newFixedThreadPool(8)

        val writes = (1..200).map { day ->
            pool.submit {
                database.transaction {
                    database.activityQueries.addActivity("user-1", "day-$day", 1, 1, 1)
                }
            }
        }
        writes.forEach { it.get(30, TimeUnit.SECONDS) }
        pool.shutdown()

        assertEquals(200L, database.activityQueries.selectTotalXp("user-1").executeAsOne())
        dir.deleteRecursively()
    }
}
