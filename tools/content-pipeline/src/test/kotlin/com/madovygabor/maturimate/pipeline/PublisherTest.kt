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

/** The release rows of `publish`, against a throwaway database (skipped when none is reachable, like PostgresWriterTest). */
class PublisherTest {

    private val repoRoot = File(System.getProperty("repoRoot"))
    private val fixture = File(javaClass.getResource("/content-valid")!!.toURI())
    private val testDatabase = "maturimate_publish_${System.nanoTime()}"
    private lateinit var adminUrl: String
    private lateinit var connection: Connection
    private val uploaded = mutableMapOf<String, Int>()
    private val store = ObjectStore { key, bytes -> uploaded[key] = bytes.size }

    @Before
    fun createDatabase() {
        try {
            adminUrl = System.getenv("DATABASE_URL")
                ?: File(repoRoot, "dbServer/.env").readLines().first { it.startsWith("DATABASE_URL=") }.substringAfter("=")
            openDatabase(adminUrl).use { it.createStatement().execute("CREATE DATABASE $testDatabase") }
        } catch (e: Exception) {
            if (System.getenv("REQUIRE_POSTGRES") == "true") throw e
            assumeNoException("local PostgreSQL is not available", e)
        }
        val uri = URI(adminUrl)
        connection = openDatabase(URI(uri.scheme, uri.rawUserInfo, uri.host, uri.port, "/$testDatabase", null, null).toString())
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

    private fun count(sql: String): Int = connection.createStatement().use { s -> s.executeQuery(sql).use { it.next(); it.getInt(1) } }

    @Test
    fun publishRefusesToRunBeforeTheImport() {
        val content = ContentLoader(fixture).load()
        assertFailsWith<ExportException> { Publisher(connection, store).publish(content, 2026111201) }
        assertTrue(uploaded.isEmpty())
    }

    @Test
    fun packsAreUploadedAndTheNewReleaseReplacesTheOldOne() {
        val content = ContentLoader(fixture).load()
        PostgresWriter(connection).write(content)

        val first = Publisher(connection, store).publish(content, 2026111201)
        val second = Publisher(connection, store).publish(content, 2026111202)

        assertEquals(1, first.size)
        assertTrue(first.single().objectPath.endsWith("/1/2026111201/content.db"), first.single().objectPath)
        assertEquals(2, uploaded.size)
        assertEquals(2, count("SELECT count(*) FROM content_release"))
        assertEquals(1, count("SELECT count(*) FROM content_release WHERE is_current"))
        assertEquals(1, count("SELECT count(*) FROM content_release WHERE is_current AND content_version = 2026111202"))
        assertEquals(second.single().sha256, connection.createStatement().use { s ->
            s.executeQuery("SELECT sha256 FROM content_release WHERE is_current").use { it.next(); it.getString(1) }
        })
    }
}
