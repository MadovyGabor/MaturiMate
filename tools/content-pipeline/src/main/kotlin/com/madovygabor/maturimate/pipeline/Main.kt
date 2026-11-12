package com.madovygabor.maturimate.pipeline

import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.sql.Connection
import java.sql.DriverManager
import java.util.Properties
import kotlin.system.exitProcess

private const val USAGE = """Usage:
  export <content folder> <output folder> --version=yyyyMMddNN [--kind=full|starter] [--previous=<old content.db>]
      Builds content.db and content-manifest.json for the app.
      --kind       full (default): everything, plus one pack per subject in packs/<subject_id>/
                   starter: the first lesson of each subject only (bundled in the app)
      --previous   fails if a row of that release is missing from the files
  publish <content folder> --version=yyyyMMddNN
      Builds the packs, puts them in the private R2 bucket and writes the release rows (run `import` first).
      Needs DATABASE_URL, R2_ACCOUNT_ID, R2_CONTENT_ACCESS_KEY_ID and R2_CONTENT_SECRET_ACCESS_KEY
      (environment or dbServer/.env); R2_CONTENT_BUCKET defaults to maturimate-content.
  import <content folder> [--dry-run]
      Writes the content into PostgreSQL (the server copy).
      --dry-run    only validate, write nothing
      The database is read from DATABASE_URL (environment or dbServer/.env)."""

fun main(args: Array<String>) {
    val positional = args.filterNot { it.startsWith("--") }
    val options = args.filter { it.startsWith("--") }.associate {
        it.removePrefix("--").substringBefore("=") to it.substringAfter("=", "")
    }

    val command = positional.firstOrNull()
    when {
        command == "import" && positional.size == 2 -> import(File(positional[1]), dryRun = "dry-run" in options)
        command == "publish" && positional.size == 2 -> {
            val version = options["version"]?.toLongOrNull() ?: fail("--version=yyyyMMddNN is required")
            publish(File(positional[1]), version)
        }
        command == "export" && positional.size == 3 -> {
            val version = options["version"]?.toLongOrNull() ?: fail("--version=yyyyMMddNN is required")
            export(File(positional[1]), File(positional[2]), version, options["previous"]?.let(::File), ContentKind.parse(options["kind"] ?: "full"))
        }
        else -> fail(USAGE, code = 2)
    }
}

private fun import(root: File, dryRun: Boolean) {
    val content = loadValidContent(root)
    if (dryRun) {
        println("Dry run: nothing was written.")
        return
    }
    openDatabase(databaseUrl()).use { connection ->
        val report = PostgresWriter(connection).write(content)
        println("Imported:")
        print(report)
    }
}

private fun publish(root: File, version: Long) {
    val content = loadValidContent(root)
    val uploader = R2Uploader(
        accountId = envValue("R2_ACCOUNT_ID"), bucket = envValueOrNull("R2_CONTENT_BUCKET") ?: "maturimate-content",
        accessKeyId = envValue("R2_CONTENT_ACCESS_KEY_ID"), secretAccessKey = envValue("R2_CONTENT_SECRET_ACCESS_KEY"),
    )
    val packs = try {
        openDatabase(databaseUrl()).use { Publisher(it, uploader).publish(content, version) }
    } catch (e: ExportException) {
        fail("Publish failed: ${e.message}")
    }
    packs.forEach { println("Published ${it.objectPath}: ${it.sizeBytes} bytes, sha256 ${it.sha256}") }
}

private fun export(root: File, outDir: File, version: Long, previous: File?, kind: ContentKind) {
    val content = loadValidContent(root)
    val manifest = try {
        SqliteExporter().export(content, outDir, version, previous, kind) { System.err.println("Warning: $it") }
    } catch (e: ExportException) {
        fail("Export failed: ${e.message}")
    }
    println("Exported ${File(outDir, manifest.fileName).path}: version ${manifest.contentVersion}, " +
        "${manifest.sizeBytes} bytes, sha256 ${manifest.sha256}")
}

/** Loads and validates the content folder; stops the program with every problem listed if it is not valid. */
private fun loadValidContent(root: File): ContentSet {
    if (!root.isDirectory) fail("Content folder not found: ${root.absolutePath}", code = 2)
    val loader = ContentLoader(root)
    val content = loader.load()
    val errors = ContentValidator(loader::relative).validate(content)
    if (errors.isNotEmpty()) {
        fail("${errors.size} problem(s), nothing was written:\n" + errors.joinToString("\n") { "  - $it" })
    }
    println(
        "Valid: ${content.subjects.size} subject(s), ${content.topics.size} topic(s), " +
            "${content.summaries.size + content.practices.size} learning unit(s), ${content.tasks.size} task(s)."
    )
    return content
}

private fun fail(message: String, code: Int = 1): Nothing {
    System.err.println(message)
    exitProcess(code)
}

/** A value from the environment, or from `dbServer/.env`. */
fun envValueOrNull(name: String): String? =
    System.getenv(name) ?: File("dbServer/.env").takeIf { it.isFile }?.readLines()
        ?.map { it.trim() }?.firstOrNull { it.startsWith("$name=") }?.substringAfter("=")

fun envValue(name: String): String = envValueOrNull(name) ?: error("$name is not set (environment or dbServer/.env)")

/** DATABASE_URL from the environment, or from `dbServer/.env` (the tool runs in the repository root). */
fun databaseUrl(): String =
    System.getenv("DATABASE_URL")
        ?: File("dbServer/.env").takeIf { it.isFile }?.readLines()
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("DATABASE_URL=") }
            ?.substringAfter("=")
        ?: error("DATABASE_URL is not set (environment or dbServer/.env)")

/** Opens a `postgresql://user:password@host:port/database` URL with the JDBC driver. */
fun openDatabase(url: String): Connection {
    val uri = URI(url)
    val (user, password) = (uri.rawUserInfo ?: "").split(":", limit = 2)
        .map { URLDecoder.decode(it, Charsets.UTF_8) } + listOf("")
    val properties = Properties().apply {
        put("user", user)
        put("password", password)
    }
    val port = if (uri.port == -1) 5432 else uri.port
    return DriverManager.getConnection("jdbc:postgresql://${uri.host}:$port${uri.path}", properties)
}
