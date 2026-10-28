package com.madovygabor.maturimate.pipeline

import org.junit.After
import org.junit.Assume.assumeNoException
import org.junit.Before
import java.io.File
import java.net.URI
import java.sql.Connection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Runs the import against a throwaway database on the local PostgreSQL (docker-compose.yml),
 * with the real DDL and its triggers. Skipped when no database is reachable.
 */
class PostgresWriterTest {

    private val repoRoot = File(System.getProperty("repoRoot"))
    private val fixture = File(javaClass.getResource("/content-valid")!!.toURI())
    private val testDatabase = "maturimate_test_${System.nanoTime()}"

    private lateinit var adminUrl: String
    private lateinit var connection: Connection

    @Before
    fun createDatabase() {
        try {
            adminUrl = System.getenv("DATABASE_URL")
                ?: File(repoRoot, ".env").readLines().first { it.startsWith("DATABASE_URL=") }.substringAfter("=")
            openDatabase(adminUrl).use { it.createStatement().execute("CREATE DATABASE $testDatabase") }
        } catch (e: Exception) {
            assumeNoException("local PostgreSQL is not available", e)
        }
        connection = openDatabase(adminUrl.replaceDatabase(testDatabase))
        connection.createStatement().use { it.execute(File(repoRoot, "db/postgres/01_ddl.sql").readText()) }
    }

    @After
    fun dropDatabase() {
        if (!::connection.isInitialized) return
        connection.close()
        openDatabase(adminUrl).use { it.createStatement().execute("DROP DATABASE $testDatabase") }
    }

    private fun import(content: ContentSet = ContentLoader(fixture).load()) = PostgresWriter(connection).write(content)

    private fun count(sql: String): Int =
        connection.createStatement().use { s -> s.executeQuery(sql).use { it.next(); it.getInt(1) } }

    @Test
    fun importWritesAllRows() {
        val report = import()

        assertEquals(5, report.inserted["task"])
        assertEquals(2, report.inserted["learning_unit"])
        assertEquals(1, count("SELECT count(*) FROM learning_unit WHERE unit_type = 'SUMMARY' AND body_text IS NOT NULL"))
        assertEquals(5, count("SELECT count(*) FROM unit_task WHERE learning_unit_id = 101"))
        assertEquals(3, count("SELECT count(*) FROM unit_task WHERE is_core"))
        assertEquals(2, count("SELECT count(*) FROM task_key WHERE task_id = 1004 AND prompt_option_id IS NOT NULL"))
        assertEquals(3, count("SELECT count(*) FROM task_accepted_answer WHERE task_id = 1005"))
        assertEquals(1, count("SELECT count(*) FROM topic WHERE subject_id IS NULL AND parent_topic_id = 10"))
    }

    @Test
    fun secondImportChangesNothing() {
        import()
        val report = import()

        assertTrue(report.inserted.isEmpty(), "inserted: ${report.inserted}")
        assertTrue(report.updated.isEmpty(), "updated: ${report.updated}")
        assertEquals(5, count("SELECT count(*) FROM task"))
    }

    @Test
    fun changedTaskIsUpdated() {
        import()
        val content = ContentLoader(fixture).load()
        val changed = content.copy(tasks = content.tasks.map {
            if (it.entry.taskId == 1001L) it.copy(entry = it.entry.copy(explanation = "Új magyarázat.")) else it
        })

        val report = import(changed)

        assertEquals(mapOf("task" to 1), report.updated)
        assertEquals(1, count("SELECT count(*) FROM task WHERE explanation = 'Új magyarázat.'"))
    }

    @Test
    fun removedTaskIsDeactivatedNotDeleted() {
        import()
        val content = ContentLoader(fixture).load()
        val withoutTask = content.copy(
            tasks = content.tasks.filter { it.entry.taskId != 1005L },
            practices = content.practices.map { p -> p.copy(file = p.file.copy(tasks = p.file.tasks.filter { it.taskId != 1005L })) },
        )

        val report = import(withoutTask)

        assertEquals(1, report.deactivated["task"])
        assertEquals(1, count("SELECT count(*) FROM task WHERE task_id = 1005 AND NOT is_active"))
    }

    @Test
    fun sequencesContinueAfterImportedIds() {
        import()
        val nextTaskId = count(
            "INSERT INTO task (topic_id, task_format_id, question_text) VALUES (11, 7, 'x') RETURNING task_id"
        )
        assertTrue(nextTaskId > 1005, "next id $nextTaskId")
    }

    @Test
    fun failedImportWritesNothing() {
        val content = ContentLoader(fixture).load()
        // the trigger rejects options on a typed task; the validator is skipped on purpose
        val broken = content.copy(tasks = content.tasks.map {
            if (it.entry.taskId == 1003L) it.copy(entry = it.entry.copy(options = listOf(OptionEntry("A", "x")))) else it
        })

        assertFailsWith<Exception> { import(broken) }
        assertEquals(0, count("SELECT count(*) FROM task"))
    }

    private fun String.replaceDatabase(name: String): String {
        val uri = URI(this)
        return URI(uri.scheme, uri.rawUserInfo, uri.host, uri.port, "/$name", null, null).toString()
    }
}
