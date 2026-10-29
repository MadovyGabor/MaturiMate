package com.madovygabor.maturimate.pipeline

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.database.content.ContentDatabase
import java.io.File
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SqliteExporterTest {

    private val fixture = File(javaClass.getResource("/content-valid")!!.toURI())
    private val content = ContentLoader(fixture).load()
    private val workDir = Files.createTempDirectory("export").toFile()

    @AfterTest
    fun cleanUp() {
        workDir.deleteRecursively()
    }

    private fun export(
        dir: String = "out",
        version: Long = 2026102801,
        set: ContentSet = content,
        previous: File? = null,
    ) = SqliteExporter().export(set, File(workDir, dir), version, previous)

    private fun open(dir: String = "out"): ContentDatabase {
        val driver = JdbcSqliteDriver("jdbc:sqlite:${File(workDir, "$dir/content.db").absolutePath}", foreignKeysOn())
        return ContentDatabase(driver)
    }

    @Test
    fun exportedFileOpensWithTheAppSchema() {
        export()
        val db = open()

        assertEquals("HU_MJL", db.subjectQueries.selectAll().executeAsOne().code)
        assertEquals(listOf(10L, 11L), db.topicQueries.selectSubtreeIds(10).executeAsList())
        assertEquals(2, db.learningUnitQueries.selectPublishedByTopic(11).executeAsList().size)
        assertEquals(5, db.learningUnitQueries.selectUnitTasks(101).executeAsList().size)
        assertEquals("GAP_FILL", db.taskQueries.selectById(1005).executeAsOne().format_code)
        assertEquals(3, db.taskQueries.selectAcceptedAnswers(1005).executeAsList().size)
        assertEquals(2, db.taskQueries.selectKeys(1004).executeAsList().count { it.prompt_option_id != null })
        assertEquals(listOf("metafora"), db.tagQueries.selectByTask(1001).executeAsList().map { it.name })
    }

    @Test
    fun childIdsMatchTheImporter() {
        export()
        val key = open().taskQueries.selectKeys(1001).executeAsOne()
        // option "B" is the second option of task 1001
        assertEquals(childId(1001, 1), key.option_id)
    }

    @Test
    fun userVersionIsTheSchemaVersion() {
        export()
        val version = DriverManager.getConnection("jdbc:sqlite:${File(workDir, "out/content.db").absolutePath}").use { c ->
            c.createStatement().use { s -> s.executeQuery("PRAGMA user_version").use { it.next(); it.getLong(1) } }
        }
        assertEquals(ContentDatabase.Schema.version, version)
    }

    @Test
    fun sameFilesGiveTheSameHash() {
        val first = export("a")
        val second = export("b")
        assertEquals(first.sha256, second.sha256)
        assertEquals(File(workDir, "a/content.db").length(), first.sizeBytes)
    }

    @Test
    fun manifestIsWritten() {
        val manifest = export()
        val text = File(workDir, "out/content-manifest.json").readText()
        assertTrue("\"content_version\": 2026102801" in text, text)
        assertTrue(manifest.sha256 in text)
    }

    @Test
    fun removedTaskFailsAgainstThePreviousRelease() {
        export("v1")
        val withoutTask = content.copy(
            tasks = content.tasks.filter { it.entry.taskId != 1005L },
            practices = content.practices.map { p -> p.copy(file = p.file.copy(tasks = p.file.tasks.filter { it.taskId != 1005L })) },
        )

        val failure = assertFailsWith<ExportException> {
            export("v2", version = 2026102901, set = withoutTask, previous = File(workDir, "v1/content.db"))
        }
        assertTrue("task 1005" in failure.message!!, failure.message)
    }

    @Test
    fun switchedOffTaskPassesAgainstThePreviousRelease() {
        export("v1")
        val switchedOff = content.copy(tasks = content.tasks.map {
            if (it.entry.taskId == 1005L) it.copy(entry = it.entry.copy(isActive = false)) else it
        })

        export("v2", version = 2026102901, set = switchedOff, previous = File(workDir, "v1/content.db"))
        assertEquals(false, open("v2").taskQueries.selectById(1005).executeAsOne().is_active)
    }

    @Test
    fun versionMustGrow() {
        export("v1")
        assertFailsWith<ExportException> { export("v2", version = 2026102801, previous = File(workDir, "v1/content.db")) }
    }

    @Test
    fun versionMustBeADate() {
        assertFailsWith<ExportException> { export(version = 123) }
        assertFailsWith<ExportException> { export(version = 2026133101) }
    }
}
