package com.madovygabor.maturimate.core.database

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The phone has no triggers, so every write to a synced table must set `updated_at` itself; the sync decides
 * "newer wins" by that time (decision 2026-11-11). A write that forgets it is not seen as a change, and the row is
 * lost or never uploaded, with no error. This test reads the `.sq` text of the user database and fails for any UPDATE
 * of such a table that does not set `updated_at`. INSERT statements are fine: the column has a default time.
 *
 * Limit: it sees only SQL written in the `.sq` files, which is where all SQL of the app lives.
 */
class UpdatedAtGuardTest {

    /** The tables with `updated_at` that the sync compares. A new synced table is added here. */
    private val syncedTables = listOf("user_profile", "user_settings", "user_unit_progress", "user_streak", "activity_log")

    private val updateStatement = Regex(
        """UPDATE\s+(?:OR\s+\w+\s+)?(\w+)\s+SET\b(.*?);""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    private fun userSqFiles(): List<File> {
        val dir = File("src/commonMain/sqldelight/user")
        assertTrue(dir.isDirectory, "the user .sq directory was not found at ${dir.absolutePath}")
        return dir.walkTopDown().filter { it.isFile && it.extension == "sq" }.toList()
            .also { assertTrue(it.isNotEmpty(), "no .sq files found in ${dir.absolutePath}") }
    }

    private fun updatesOf(sql: String): List<Pair<String, String>> {
        val withoutComments = sql.lines().joinToString("\n") { it.substringBefore("--") }
        return updateStatement.findAll(withoutComments).map { it.groupValues[1] to it.value }.toList()
    }

    @Test
    fun everyUpdateOfASyncedTableSetsUpdatedAt() {
        var seen = 0
        val missing = mutableListOf<String>()
        for (file in userSqFiles()) {
            for ((table, statement) in updatesOf(file.readText())) {
                if (table !in syncedTables) continue
                seen++
                // the SET part ends at WHERE; updated_at must be assigned there, not only be read in the WHERE
                val assignments = statement.substringBefore(" WHERE ", statement)
                if (!Regex("""\bupdated_at\s*=""").containsMatchIn(assignments)) {
                    missing += "${file.name}: ${statement.replace(Regex("\\s+"), " ")}"
                }
            }
        }
        assertTrue(seen > 0, "no UPDATE of a synced table was found: the test is not looking at the right files")
        assertTrue(missing.isEmpty(), "UPDATE without updated_at on a synced table:\n" + missing.joinToString("\n"))
    }

    @Test
    fun theGuardSeesAMissingUpdatedAt() {
        // a statement like the old adoptAccount one must be reported, one that sets the time must not
        val bad = updatesOf("UPDATE OR REPLACE user_profile SET user_id = :a WHERE user_id = :b;")
        val good = updatesOf("UPDATE user_profile SET first_name = :n, updated_at = 5 WHERE user_id = :b;")
        assertTrue(bad.single().second.substringBefore(" WHERE ").contains("updated_at").not())
        assertTrue(good.single().second.substringBefore(" WHERE ").contains("updated_at"))
    }
}
