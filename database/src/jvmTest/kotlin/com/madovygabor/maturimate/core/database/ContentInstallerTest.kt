package com.madovygabor.maturimate.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.database.content.ContentDatabase
import com.madovygabor.maturimate.database.content.Subject
import com.madovygabor.maturimate.database.content.Task_format
import com.madovygabor.maturimate.database.content.Topic
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContentInstallerTest {

    private val dir = Files.createTempDirectory("content").toFile()
    private val work = Files.createTempDirectory("work").toFile()
    private val storage = FileContentStorage(dir, JdbcSqliteRunner())
    private val installer = ContentInstaller(storage)

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
        work.deleteRecursively()
    }

    /** A real content file with these subjects, each with one root topic whose title says [label]. */
    private fun dbBytes(subjects: List<Long>, label: String = "x"): ByteArray {
        val file = File(work, "db-${System.nanoTime()}.db")
        JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", foreignKeysOn()).use { driver ->
            ContentDatabase.Schema.create(driver)
            val db = ContentDatabase(driver)
            db.taskFormatQueries.insert(Task_format(1, "MC_SINGLE", "Multiple choice", "CHOICE", null))
            subjects.forEach {
                db.subjectQueries.insert(Subject(it, "S$it", "hu $it", "sk $it", "HU", null))
                db.topicQueries.insert(Topic(it * 10, it, null, "$label $it", null, 1, true))
            }
            driver.execute(null, "PRAGMA user_version = ${ContentDatabase.Schema.version}", 0)
        }
        return file.readBytes().also { file.delete() }
    }

    private fun manifest(
        bytes: ByteArray,
        version: Long,
        kind: String = ContentManifest.KIND_FULL,
        subjects: List<Long> = emptyList(),
        schema: Long = ContentDatabase.Schema.version,
    ) = ContentManifest(version, schema, CONTENT_DATABASE_NAME, bytes.size.toLong(), storage.sha256(bytes), kind, subjects).toJson()

    private fun release(version: Long, subjects: List<Long> = listOf(1), kind: String = ContentManifest.KIND_FULL, label: String = "x"): Pair<ByteArray, String> {
        val bytes = dbBytes(subjects, label)
        return bytes to manifest(bytes, version, kind, subjects)
    }

    private fun installedTitles(): List<String> =
        JdbcSqliteDriver("jdbc:sqlite:${File(dir, "content.db").absolutePath}").use { driver ->
            ContentDatabase(driver).topicQueries.selectSubtreeIds(10) // opens the file with the app schema
            JdbcSqliteRunner().queryStrings(File(dir, "content.db").absolutePath, "SELECT title FROM topic ORDER BY topic_id")
        }

    @Test
    fun firstStartInstallsTheBundledFile() {
        assertNull(installer.installedManifest())
        val (db, manifest) = release(2026102801)

        assertEquals(ContentInstallResult.Installed(2026102801), installer.installIfNewer(db, manifest))
        assertTrue(storage.databaseFile.readBytes().contentEquals(db))
        assertEquals(2026102801, installer.installedManifest()?.contentVersion)
    }

    @Test
    fun sameOrOlderVersionIsNotInstalled() {
        val (db, manifest) = release(2026102801)
        installer.installIfNewer(db, manifest)
        val (olderDb, olderManifest) = release(2026102701)

        assertFalse(installer.isNewer(manifest))
        assertEquals(ContentInstallResult.UpToDate(2026102801), installer.installIfNewer(db, manifest))
        assertEquals(ContentInstallResult.UpToDate(2026102801), installer.installIfNewer(olderDb, olderManifest))
        assertTrue(storage.databaseFile.readBytes().contentEquals(db))
    }

    @Test
    fun newerVersionReplacesTheInstalledFile() {
        val first = release(2026102801)
        installer.installIfNewer(first.first, first.second)
        val (newDb, newManifest) = release(2026110101, label = "new")

        assertTrue(installer.isNewer(newManifest))
        assertEquals(ContentInstallResult.Installed(2026110101), installer.installIfNewer(newDb, newManifest))
        assertTrue(storage.databaseFile.readBytes().contentEquals(newDb))
        assertFalse(dir.listFiles()!!.any { it.name.endsWith(".tmp") })
    }

    @Test
    fun wrongHashIsRejected() {
        val (_, manifest) = release(2026102801)
        val result = installer.installIfNewer(dbBytes(listOf(1), "tampered"), manifest)

        assertIs<ContentInstallResult.Rejected>(result)
        assertNull(installer.installedManifest())
    }

    @Test
    fun otherSchemaVersionIsRejected() {
        val bytes = dbBytes(listOf(1))
        val manifest = manifest(bytes, 2026102801, schema = ContentDatabase.Schema.version + 1)
        assertIs<ContentInstallResult.Rejected>(installer.installIfNewer(bytes, manifest))
    }

    @Test
    fun brokenManifestIsRejected() {
        assertIs<ContentInstallResult.Rejected>(installer.installIfNewer(ByteArray(0), "{"))
        assertFalse(installer.isNewer("{"))
    }

    @Test
    fun twoPacksMergeIntoOneFile() {
        val starter = release(2026110101, listOf(1, 2), ContentManifest.KIND_STARTER, "starter")
        installer.installIfNewer(starter.first, starter.second)
        val pack1 = release(2026111001, listOf(1), label = "pack")
        val pack2 = release(2026111001, listOf(2), label = "pack")

        assertEquals(PackResult.Saved(1, 2026111001), installer.savePack(1, pack1.first, pack1.second))
        assertEquals(PackResult.Saved(2, 2026111001), installer.savePack(2, pack2.first, pack2.second))
        assertTrue(installer.hasPendingParts())
        installer.rebuild()

        assertFalse(installer.hasPendingParts())
        // the packs win over the starter's text, one row per topic
        assertEquals(listOf("pack 1", "pack 2"), installedTitles())
        val manifest = installer.installedManifest()!!
        assertEquals(ContentManifest.KIND_FULL, manifest.kind)
        assertEquals(2026111001, manifest.contentVersion)
        assertEquals(ContentManifest.KIND_FULL, installer.installedSubject(1)?.kind)
        assertFalse(dir.listFiles()!!.any { it.name.endsWith(".tmp") })
    }

    @Test
    fun aStarterNeverReplacesAFullSubject() {
        val starter = release(2026110101, listOf(1, 2), ContentManifest.KIND_STARTER, "starter")
        installer.installIfNewer(starter.first, starter.second)
        val pack1 = release(2026111001, listOf(1), label = "pack")
        installer.savePack(1, pack1.first, pack1.second)
        installer.rebuild()

        // a newer bundled starter arrives with an app update
        val newer = release(2026112001, listOf(1, 2), ContentManifest.KIND_STARTER, "newer")
        assertEquals(ContentInstallResult.Installed(2026112001), installer.installIfNewer(newer.first, newer.second))

        // subject 1 keeps its pack's text, subject 2 (no pack) gets the newer starter
        assertEquals(listOf("pack 1", "newer 2"), installedTitles())
        assertEquals(ContentManifest.KIND_FULL, installer.installedSubject(1)?.kind)
        assertEquals(ContentManifest.KIND_STARTER, installer.installedSubject(2)?.kind)
        assertEquals(ContentManifest.KIND_STARTER, installer.installedManifest()?.kind)
    }

    @Test
    fun aPackThatWaitsForTheBuildCountsAsAvailable() {
        val starter = release(2026110101, listOf(1, 2), ContentManifest.KIND_STARTER, "starter")
        installer.installIfNewer(starter.first, starter.second)
        val pack1 = release(2026111001, listOf(1), label = "pack")
        installer.savePack(1, pack1.first, pack1.second)

        // not built yet: the installed file still holds the starter, but the phone has the pack
        assertEquals(SubjectContent(ContentManifest.KIND_STARTER, 2026110101), installer.installedSubject(1))
        assertEquals(SubjectContent(ContentManifest.KIND_FULL, 2026111001), installer.availableSubject(1))
        // a subject without a pack is what the installed file holds
        assertEquals(SubjectContent(ContentManifest.KIND_STARTER, 2026110101), installer.availableSubject(2))
        assertNull(installer.availableSubject(3))
    }

    @Test
    fun aFullReplacesAStarterOfTheSameVersion() {
        val starter = release(2026110101, listOf(1), ContentManifest.KIND_STARTER, "starter")
        installer.installIfNewer(starter.first, starter.second)
        assertEquals(ContentManifest.KIND_STARTER, installer.installedSubject(1)?.kind)

        val pack = release(2026110101, listOf(1), label = "pack")
        assertEquals(PackResult.Saved(1, 2026110101), installer.savePack(1, pack.first, pack.second))
        installer.rebuild()

        assertEquals(listOf("pack 1"), installedTitles())
        assertEquals(ContentManifest.KIND_FULL, installer.installedSubject(1)?.kind)
        // the same pack again changes nothing
        assertEquals(PackResult.UpToDate(2026110101), installer.savePack(1, pack.first, pack.second))
    }

    @Test
    fun brokenPackLeavesTheInstalledFile() {
        val first = release(2026110101, listOf(1), ContentManifest.KIND_STARTER, "starter")
        installer.installIfNewer(first.first, first.second)
        val before = storage.databaseFile.readBytes()

        // not a SQLite file, but with a matching manifest: the build fails
        val garbage = "not a database".encodeToByteArray()
        val result = installer.savePack(1, garbage, manifest(garbage, 2026111001, subjects = listOf(1)))
        assertIs<PackResult.Saved>(result)
        assertFailsWithAny { installer.rebuild() }

        assertTrue(storage.databaseFile.readBytes().contentEquals(before))
        assertFalse(dir.listFiles()!!.any { it.name.endsWith(".tmp") })
        // a pack of the wrong kind or subject is refused outright
        val wrong = release(2026111002, listOf(2))
        assertIs<PackResult.Rejected>(installer.savePack(1, wrong.first, wrong.second))
        val starterAsPack = release(2026111003, listOf(1), ContentManifest.KIND_STARTER)
        assertIs<PackResult.Rejected>(installer.savePack(1, starterAsPack.first, starterAsPack.second))
    }

    @Test
    fun legacyManifestIsFull() {
        // a file installed before parts existed: no state, no parts, a manifest without kind
        val bytes = dbBytes(listOf(1, 2), "old")
        File(dir, "content.db").writeBytes(bytes)
        File(dir, "content-manifest.json").writeText(
            """{"content_version": 2026101001, "schema_version": ${ContentDatabase.Schema.version}, "file_name": "content.db", "size_bytes": ${bytes.size}, "sha256": "${storage.sha256(bytes)}"}"""
        )

        assertEquals(SubjectContent(ContentManifest.KIND_FULL, 2026101001), installer.installedSubject(2))
        // a pack for subject 1 is merged in front of the old file, which is kept as the bundled part
        val pack = release(2026111001, listOf(1), label = "pack")
        installer.savePack(1, pack.first, pack.second)
        installer.rebuild()
        assertEquals(listOf("pack 1", "old 2"), installedTitles())
    }

    // ---- parts that clash (card 13) ------------------------------------------------------------------------------------

    /** One subject of a synthetic content file: a root topic, a child topic, a lesson and a task of the child. */
    private data class Tree(val subject: Long, val root: Long, val child: Long, val unit: Long)

    private fun treeDb(vararg trees: Tree, label: String = "x", danglingChild: Boolean = false): ByteArray {
        val file = File(work, "tree-${System.nanoTime()}.db")
        JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}").use { driver ->
            ContentDatabase.Schema.create(driver)
            fun sql(text: String) = driver.execute(null, text, 0)
            sql("INSERT INTO task_format VALUES (1, 'MC_SINGLE', 'Multiple choice', 'CHOICE', NULL)")
            trees.forEach { t ->
                val task = t.unit + 1000
                sql("INSERT INTO subject VALUES (${t.subject}, 'S${t.subject}', 'hu', 'sk', 'HU', NULL)")
                sql("INSERT INTO topic VALUES (${t.root}, ${t.subject}, NULL, '$label root ${t.subject}', NULL, 1, 1)")
                val parent = if (danglingChild) 9999 else t.root
                sql("INSERT INTO topic VALUES (${t.child}, NULL, $parent, '$label child ${t.subject}', NULL, 1, 1)")
                sql("INSERT INTO learning_unit VALUES (${t.unit}, ${t.child}, 'SUMMARY', '$label unit', NULL, 1, 5, 10, 1, 1, 0)")
                sql("INSERT INTO task VALUES ($task, ${t.child}, 1, 'q', NULL, 1, NULL, 1, 0)")
                sql("INSERT INTO unit_task VALUES (${t.unit}, $task, 1, 1)")
                sql("INSERT INTO task_option VALUES ($task, $task, 'ANSWER', 'A', 'a', NULL)")
                sql("INSERT INTO task_key VALUES ($task, 1, NULL, $task)")
            }
            sql("PRAGMA user_version = ${ContentDatabase.Schema.version}")
        }
        return file.readBytes().also { file.delete() }
    }

    private fun installedIds(table: String, column: String): List<Long> =
        JdbcSqliteRunner().queryStrings(File(dir, "content.db").absolutePath, "SELECT $column FROM $table ORDER BY $column").map { it.toLong() }

    private fun assertInstalledFileIsConsistent() {
        val path = File(dir, "content.db").absolutePath
        assertEquals(listOf("ok"), JdbcSqliteRunner().queryStrings(path, "PRAGMA integrity_check"))
        assertTrue(JdbcSqliteRunner().queryStrings(path, "PRAGMA foreign_key_check").isEmpty())
    }

    @Test
    fun aFullPackReplacesABundledStarterOfTheSameSubjectEvenWhenTheirIdsClash() {
        // the same subject in both, with different ids but the same position (root topic order 1): a blind merge of the
        // starter drops its root topic and keeps its child topic and lesson, which then point at nothing
        val starter = treeDb(Tree(1, 5, 6, 7), label = "starter")
        installer.installIfNewer(starter, manifest(starter, 2000010101, ContentManifest.KIND_STARTER, listOf(1)))
        val pack = treeDb(Tree(1, 10, 11, 12), label = "pack")
        assertEquals(PackResult.Saved(1, 2026111402), installer.savePack(1, pack, manifest(pack, 2026111402, subjects = listOf(1))))

        installer.rebuild()

        assertInstalledFileIsConsistent()
        assertEquals(listOf(10L, 11L), installedIds("topic", "topic_id"))
        assertEquals(listOf(12L), installedIds("learning_unit", "learning_unit_id"))
        assertEquals(listOf(1012L), installedIds("task", "task_id"))
        assertEquals(ContentManifest.KIND_FULL, installer.installedManifest()?.kind)
        assertEquals(2026111402, installer.installedManifest()?.contentVersion)
        assertEquals(SubjectContent(ContentManifest.KIND_FULL, 2026111402), installer.installedSubject(1))
        // the replaced starter is still a part on disk, and nothing waits for a build any more
        assertFalse(installer.hasPendingParts())
    }

    @Test
    fun partsWithDisjointSubjectsAreMerged() {
        val starter = treeDb(Tree(2, 25, 26, 27), label = "starter")
        installer.installIfNewer(starter, manifest(starter, 2000010101, ContentManifest.KIND_STARTER, listOf(2)))
        val pack = treeDb(Tree(1, 10, 11, 12), label = "pack")
        installer.savePack(1, pack, manifest(pack, 2026111402, subjects = listOf(1)))

        installer.rebuild()

        assertInstalledFileIsConsistent()
        assertEquals(listOf(10L, 11L, 25L, 26L), installedIds("topic", "topic_id"))
        assertEquals(listOf(12L, 27L), installedIds("learning_unit", "learning_unit_id"))
        assertEquals(ContentManifest.KIND_FULL, installer.installedSubject(1)?.kind)
        assertEquals(ContentManifest.KIND_STARTER, installer.installedSubject(2)?.kind)
    }

    @Test
    fun aPartialOverlapKeepsTheNewPacksSubjectWholeAndTheRestOfTheOldPart() {
        // the starter holds subjects 1 and 2, the pack only 1: subject 1 comes from the pack alone (the starter's rows of it
        // are removed from the copy that is merged), subject 2 from the starter
        val starter = treeDb(Tree(1, 5, 6, 7), Tree(2, 25, 26, 27), label = "starter")
        installer.installIfNewer(starter, manifest(starter, 2000010101, ContentManifest.KIND_STARTER, listOf(1, 2)))
        val pack = treeDb(Tree(1, 10, 11, 12), label = "pack")
        installer.savePack(1, pack, manifest(pack, 2026111402, subjects = listOf(1)))

        installer.rebuild()

        assertInstalledFileIsConsistent()
        assertEquals(listOf(10L, 11L, 25L, 26L), installedIds("topic", "topic_id"))
        assertEquals(listOf(12L, 27L), installedIds("learning_unit", "learning_unit_id"))
        assertEquals(listOf(1012L, 1027L), installedIds("task", "task_id"))
        assertEquals(listOf(1012L, 1027L), installedIds("task_option", "option_id"))
        assertEquals(listOf(1L, 2L), installedIds("subject", "subject_id"))
        assertEquals(ContentManifest.KIND_FULL, installer.installedSubject(1)?.kind)
        assertEquals(ContentManifest.KIND_STARTER, installer.installedSubject(2)?.kind)
        assertEquals(ContentManifest.KIND_STARTER, installer.installedManifest()?.kind)
        // the part on disk is untouched: the rows are removed only in the copy that is merged
        assertTrue(File(dir, "parts/bundled.db").readBytes().contentEquals(starter))
    }

    @Test
    fun aFailedBuildKeepsTheInstalledFileAndIsNotTriedAgainForTheSameParts() {
        val starter = treeDb(Tree(1, 5, 6, 7), label = "starter")
        installer.installIfNewer(starter, manifest(starter, 2000010101, ContentManifest.KIND_STARTER, listOf(1)))
        val before = storage.databaseFile.readBytes()
        // valid alone as far as the manifest goes, but its child topic points at a topic that does not exist
        val broken = treeDb(Tree(1, 10, 11, 12), label = "pack", danglingChild = true)
        installer.savePack(1, broken, manifest(broken, 2026111402, subjects = listOf(1)))
        var attempts = 0

        val first = installer.swapPending(beforeBuild = { attempts++ })
        assertIs<SwapResult.Failed>(first)
        assertTrue(first.reason.startsWith("foreign_key_check failed"), first.reason)
        // counts per table only, never a row
        assertTrue("topic=1" in first.reason, first.reason)
        assertFalse("pack" in first.reason, first.reason)
        assertTrue(storage.databaseFile.readBytes().contentEquals(before))
        assertFalse(dir.listFiles()!!.any { it.name.endsWith(".tmp") })
        assertTrue(installer.installFailed())

        // the same parts again: not built again
        assertEquals(SwapResult.Failed(first.reason, attempted = false), installer.swapPending(beforeBuild = { attempts++ }))
        assertEquals(1, attempts)
        assertTrue(storage.databaseFile.readBytes().contentEquals(before))

        // a different version of the pack arrives: tried again, and it builds
        val fixed = treeDb(Tree(1, 10, 11, 12), label = "pack")
        installer.savePack(1, fixed, manifest(fixed, 2026111503, subjects = listOf(1)))
        assertFalse(installer.installFailed())
        assertEquals(SwapResult.Swapped, installer.swapPending(beforeBuild = { attempts++ }))
        assertEquals(2, attempts)
        assertFalse(installer.installFailed())
        assertInstalledFileIsConsistent()
        assertEquals(listOf(10L, 11L), installedIds("topic", "topic_id"))
    }

    @Test
    fun aFailedBuildIsTriedAgainAtTheNextAppStart() {
        val starter = treeDb(Tree(1, 5, 6, 7), label = "starter")
        installer.installIfNewer(starter, manifest(starter, 2000010101, ContentManifest.KIND_STARTER, listOf(1)))
        val broken = treeDb(Tree(1, 10, 11, 12), danglingChild = true)
        installer.savePack(1, broken, manifest(broken, 2026111402, subjects = listOf(1)))
        assertIs<SwapResult.Failed>(installer.swapPending())

        // a new process: nothing is remembered, the build runs (and fails) once more
        val restarted = ContentInstaller(FileContentStorage(dir, JdbcSqliteRunner()))
        assertFalse(restarted.installFailed())
        var attempts = 0
        assertIs<SwapResult.Failed>(restarted.swapPending(beforeBuild = { attempts++ }))
        assertEquals(1, attempts)
    }

    @Test
    fun nothingPendingMeansNothingBuilt() {
        val starter = treeDb(Tree(1, 5, 6, 7))
        installer.installIfNewer(starter, manifest(starter, 2000010101, ContentManifest.KIND_STARTER, listOf(1)))
        var attempts = 0
        assertEquals(SwapResult.NothingPending, installer.swapPending(beforeBuild = { attempts++ }))
        assertEquals(0, attempts)
    }

    private fun assertFailsWithAny(block: () -> Unit) {
        val failed = runCatching(block).isFailure
        assertTrue(failed, "expected the build to fail")
    }
}
