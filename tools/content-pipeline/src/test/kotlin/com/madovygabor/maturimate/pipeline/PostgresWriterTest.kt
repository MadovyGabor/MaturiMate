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
                ?: File(repoRoot, "dbServer/.env").readLines().first { it.startsWith("DATABASE_URL=") }.substringAfter("=")
            openDatabase(adminUrl).use { it.createStatement().execute("CREATE DATABASE $testDatabase") }
        } catch (e: Exception) {
            // CI sets REQUIRE_POSTGRES, so a missing database fails there instead of being skipped
            if (System.getenv("REQUIRE_POSTGRES") == "true") throw e
            assumeNoException("local PostgreSQL is not available", e)
        }
        connection = openDatabase(adminUrl.replaceDatabase(testDatabase))
        // A stand-in for Supabase Auth, then the schema migrations in name order
        val schema = listOf(File(repoRoot, "dbServer/postgres/00_auth_stub.sql")) +
            File(repoRoot, "supabase/migrations").listFiles { f -> f.extension == "sql" }!!.sortedBy { it.name }
        connection.createStatement().use { s -> schema.forEach { s.execute(it.readText()) } }
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
        assertEquals(3, report.inserted["learning_unit"])
        assertEquals(2, count("SELECT count(*) FROM unit_page WHERE learning_unit_id = 100"))
        assertEquals(4, count("SELECT count(*) FROM page_block WHERE learning_unit_id = 100"))
        assertEquals(1, count("SELECT count(*) FROM learning_unit WHERE practice_kind = 'FINAL'"))
        assertEquals(1, count("SELECT count(*) FROM task_option WHERE why IS NOT NULL"))
        assertEquals(5, count("SELECT count(*) FROM unit_task WHERE learning_unit_id = 101"))
        assertEquals(3, count("SELECT count(*) FROM unit_task WHERE learning_unit_id = 101 AND is_core"))
        assertEquals(3, count("SELECT count(*) FROM unit_task WHERE learning_unit_id = 102 AND is_core"))
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

    @Test
    fun reorderedLearningUnitsDoNotCollide() {
        val content = ContentLoader(fixture).load()
        import(content)

        val swapped = content.copy(
            summaries = content.summaries.map {
                if (it.learningUnitId == 100L) it.copy(sortOrder = 2) else it
            },
            practices = content.practices.map {
                if (it.file.learningUnitId == 101L) it.copy(file = it.file.copy(sortOrder = 1)) else it
            },
        )

        val report = import(swapped)

        assertEquals(mapOf("learning_unit" to 2), report.updated)
        assertEquals(2, count("SELECT sort_order FROM learning_unit WHERE learning_unit_id = 100"))
        assertEquals(1, count("SELECT sort_order FROM learning_unit WHERE learning_unit_id = 101"))
        assertIdempotent(swapped)
    }

    @Test
    fun shiftedLearningUnitOrdersDoNotCollide() {
        val content = ContentLoader(fixture).load()
        val extra = content.summaries.first { it.learningUnitId == 100L }
            .copy(learningUnitId = 103L, title = "Extra", sortOrder = 3)
        val initial = content.copy(summaries = content.summaries + extra)
        import(initial)

        val shifted = initial.copy(
            summaries = initial.summaries.map {
                when (it.learningUnitId) {
                    100L -> it.copy(sortOrder = 2)
                    103L -> it.copy(sortOrder = 1)
                    else -> it
                }
            },
            practices = initial.practices.map {
                if (it.file.learningUnitId == 101L) it.copy(file = it.file.copy(sortOrder = 3)) else it
            },
        )

        val report = import(shifted)

        assertEquals(mapOf("learning_unit" to 3), report.updated)
        assertEquals(2, count("SELECT sort_order FROM learning_unit WHERE learning_unit_id = 100"))
        assertEquals(3, count("SELECT sort_order FROM learning_unit WHERE learning_unit_id = 101"))
        assertEquals(1, count("SELECT sort_order FROM learning_unit WHERE learning_unit_id = 103"))
        assertIdempotent(shifted)
    }

    @Test
    fun removedLearningUnitFreesSortOrderForNewUnit() {
        val content = ContentLoader(fixture).load()
        import(content)

        val replacement = content.summaries.first { it.learningUnitId == 100L }
            .copy(learningUnitId = 104L, title = "Új összefoglaló", sortOrder = 1)
        val replaced = content.copy(
            summaries = content.summaries.filterNot { it.learningUnitId == 100L } + replacement,
        )

        val report = import(replaced)

        assertEquals(1, report.inserted["learning_unit"])
        assertEquals(1, report.deactivated["learning_unit"])
        assertTrue("learning_unit" !in report.updated, "updated: ${report.updated}")
        assertEquals(
            1,
            count("SELECT count(*) FROM learning_unit WHERE learning_unit_id = 100 AND NOT is_published AND sort_order < 0"),
        )
        assertEquals(
            1,
            count("SELECT count(*) FROM learning_unit WHERE learning_unit_id = 104 AND is_published AND sort_order = 1"),
        )
        assertIdempotent(replaced)
    }

    @Test
    fun reorderedTopicsDoNotCollide() {
        val content = ContentLoader(fixture).load()
        val rootTemplate = content.topics.first { it.file.topicId == 10L }
        val childTemplate = content.topics.first { it.file.topicId == 11L }
        val extraRoot = rootTemplate.copy(file = rootTemplate.file.copy(topicId = 20L, title = "Másik főtéma", orderIndex = 2))
        val extraChild = childTemplate.copy(file = childTemplate.file.copy(topicId = 12L, title = "Másik altéma", orderIndex = 2))
        val initial = content.copy(topics = content.topics + extraRoot + extraChild)
        import(initial)

        val swapped = initial.copy(
            topics = initial.topics.map { topic ->
                when (topic.file.topicId) {
                    10L, 11L -> topic.copy(file = topic.file.copy(orderIndex = 2))
                    20L, 12L -> topic.copy(file = topic.file.copy(orderIndex = 1))
                    else -> topic
                }
            },
        )

        val report = import(swapped)

        assertEquals(mapOf("topic" to 4), report.updated)
        assertEquals(2, count("SELECT order_index FROM topic WHERE topic_id = 10"))
        assertEquals(1, count("SELECT order_index FROM topic WHERE topic_id = 20"))
        assertEquals(2, count("SELECT order_index FROM topic WHERE topic_id = 11"))
        assertEquals(1, count("SELECT order_index FROM topic WHERE topic_id = 12"))
        assertIdempotent(swapped)
    }

    @Test
    fun removedTopicFreesOrderIndexForNewTopic() {
        val content = ContentLoader(fixture).load()
        val childTemplate = content.topics.first { it.file.topicId == 11L }
        val extraChild = childTemplate.copy(file = childTemplate.file.copy(topicId = 12L, title = "Régi altéma", orderIndex = 2))
        val initial = content.copy(topics = content.topics + extraChild)
        import(initial)

        val replacement = childTemplate.copy(file = childTemplate.file.copy(topicId = 13L, title = "Új altéma", orderIndex = 2))
        val replaced = content.copy(topics = content.topics + replacement)

        val report = import(replaced)

        assertEquals(1, report.inserted["topic"])
        assertEquals(1, report.deactivated["topic"])
        assertTrue("topic" !in report.updated, "updated: ${report.updated}")
        assertEquals(
            1,
            count("SELECT count(*) FROM topic WHERE topic_id = 12 AND NOT is_published AND order_index < 0"),
        )
        assertEquals(
            1,
            count("SELECT count(*) FROM topic WHERE topic_id = 13 AND is_published AND order_index = 2"),
        )
        assertIdempotent(replaced)
    }

    private fun assertIdempotent(content: ContentSet) {
        val report = import(content)
        assertTrue(report.inserted.isEmpty(), "inserted: ${report.inserted}")
        assertTrue(report.updated.isEmpty(), "updated: ${report.updated}")
        assertTrue(report.deactivated.values.all { it == 0 }, "deactivated: ${report.deactivated}")
    }

    private fun String.replaceDatabase(name: String): String {
        val uri = URI(this)
        return URI(uri.scheme, uri.rawUserInfo, uri.host, uri.port, "/$name", null, null).toString()
    }
}
