package com.madovygabor.maturimate.pipeline

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.ContentManifest
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
        kind: ContentKind = ContentKind.FULL,
    ) = SqliteExporter().export(set, File(workDir, dir), version, previous, kind)

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
    fun lessonPagesBlocksAndKindsAreExported() {
        export()
        val q = open().learningUnitQueries

        assertEquals(listOf("Mi a szókép?", "Tipp"), q.selectPages(100).executeAsList().map { it.title })
        assertEquals(
            listOf("PARAGRAPH", "EXAMPLE", "PARAGRAPH", "TIP"),
            q.selectBlocks(100).executeAsList().map { it.block_type },
        )
        assertEquals("TOPIC", q.selectById(101).executeAsOne().practice_kind)
        assertEquals("FINAL", q.selectById(102).executeAsOne().practice_kind)
        assertEquals(null, q.selectById(100).executeAsOne().practice_kind)
    }

    @Test
    fun wrongOptionExplanationIsExported() {
        export()
        val options = open().taskQueries.selectOptions(1001).executeAsList()
        assertEquals("A hasonlat összehasonlít, és kimondja a hasonlító szót.", options.first { it.option_label == "A" }.why)
        assertEquals(null, options.first { it.option_label == "B" }.why)
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

    // a second subject with one topic and one lesson, copied from the first
    private val twoSubjects: ContentSet get() {
        val subject = content.subjects.single().copy(subjectId = 2, code = "SK_SJL")
        val topic = content.topics.first { it.parentTopicId == null }
        val second = topic.copy(file = topic.file.copy(topicId = 20), subjectId = 2, hasChildFolders = false)
        val lesson = content.summaries.first().copy(learningUnitId = 200, topicId = 20)
        return content.copy(
            subjects = content.subjects + subject,
            topics = content.topics + second,
            summaries = content.summaries + lesson,
        )
    }

    @Test
    fun starterHoldsTheFirstLessonOfEachSubjectAndItsTopicsOnly() {
        val manifest = export(set = twoSubjects, kind = ContentKind.STARTER)
        val db = open()

        assertEquals("STARTER", manifest.kind)
        assertEquals(listOf(1L, 2L), manifest.subjectIds)
        assertEquals(2, db.subjectQueries.selectAll().executeAsList().size)
        // the first lesson of the first subject sits in the leaf topic 11, under topic 10
        assertEquals(listOf(10L, 11L), db.topicQueries.selectSubtreeIds(10).executeAsList())
        assertEquals(listOf(100L), db.learningUnitQueries.selectPublishedByTopic(11).executeAsList().map { it.learning_unit_id })
        assertEquals(listOf(200L), db.learningUnitQueries.selectPublishedByTopic(20).executeAsList().map { it.learning_unit_id })
        assertTrue(db.learningUnitQueries.selectUnitTasks(101).executeAsList().isEmpty())
        assertTrue(db.taskQueries.selectById(1001).executeAsOneOrNull() == null)
        // the catalogue stays
        assertTrue(db.tagQueries.selectByTask(1001).executeAsList().isEmpty())
        assertTrue(File(workDir, "out/packs").exists().not())
    }

    @Test
    fun aSubjectWithoutAPublishedLessonIsLeftOutOfTheStarter() {
        val warnings = mutableListOf<String>()
        val noLesson = content.copy(summaries = emptyList())
        val manifest = SqliteExporter().export(noLesson, File(workDir, "out"), 2026102801, kind = ContentKind.STARTER) { warnings += it }

        assertTrue(manifest.subjectIds.isEmpty())
        assertEquals(1, warnings.size)
    }

    @Test
    fun fullWritesOnePackPerSubjectWithTheSharedTables() {
        val manifest = export(set = twoSubjects)

        assertEquals("FULL", manifest.kind)
        assertEquals(listOf(1L, 2L), manifest.subjectIds)
        for (id in listOf(1, 2)) {
            val pack = File(workDir, "out/packs/$id")
            val packManifest = ContentManifest.fromJson(File(pack, "content-manifest.json").readText())
            assertEquals(listOf(id.toLong()), packManifest.subjectIds)
            assertEquals(manifest.contentVersion, packManifest.contentVersion)
            assertEquals(File(pack, "content.db").length(), packManifest.sizeBytes)
        }
        val pack2 = open("out/packs/2")
        assertEquals(listOf("SK_SJL"), pack2.subjectQueries.selectAll().executeAsList().map { it.code })
        assertTrue(pack2.taskQueries.selectById(1001).executeAsOneOrNull() == null)
        // the task formats are repeated in every pack
        assertEquals(content.taskFormats.size, pack2.taskFormatQueries.selectAll().executeAsList().size)
        // pack 1 holds the tasks of subject 1
        assertEquals("GAP_FILL", open("out/packs/1").taskQueries.selectById(1005).executeAsOne().format_code)
    }

    @Test
    fun manifestCarriesKindAndSubjects() {
        val text = File(workDir, "out/content-manifest.json").also { export() }.readText()
        assertTrue("\"kind\": \"FULL\"" in text, text)
        assertTrue("\"subject_ids\"" in text, text)
    }

    @Test
    fun oldManifestWithoutKindReadsAsFull() {
        val old = """{"content_version": 2026102801, "schema_version": 6, "file_name": "content.db", "size_bytes": 5, "sha256": "ab"}"""
        val manifest = ContentManifest.fromJson(old)
        assertEquals("FULL", manifest.kind)
        assertTrue(manifest.subjectIds.isEmpty())
    }
}
