package com.madovygabor.maturimate.pipeline

import kotlinx.serialization.json.Json
import java.io.File

/** A topic with its place in the tree and the files of its folder. */
data class LoadedTopic(
    val file: TopicFile,
    val subjectId: Long?,
    val parentTopicId: Long?,
    val folder: File,
    val hasChildFolders: Boolean,
)

/** A `SUMMARY` learning unit read from a Markdown file. */
data class SummaryUnit(
    val learningUnitId: Long,
    val topicId: Long,
    val title: String,
    val bodyText: String,
    val estimatedMinutes: Int,
    val xpReward: Int,
    val sortOrder: Int,
    val isPublished: Boolean,
    val provenance: Provenance,
    val source: File,
)

data class PracticeUnit(val topicId: Long, val file: PracticeUnitFile, val source: File)

data class LoadedTask(val topicId: Long, val entry: TaskEntry, val source: File)

/** Everything read from a content folder, before validation. */
data class ContentSet(
    val taskFormats: List<TaskFormatFile>,
    val tags: List<TagFile>,
    val subjects: List<SubjectFile>,
    val topics: List<LoadedTopic>,
    val summaries: List<SummaryUnit>,
    val practices: List<PracticeUnit>,
    val tasks: List<LoadedTask>,
    /** Files that could not be read; reported together with the validation errors. */
    val readErrors: List<String>,
)

/**
 * Reads the content folder:
 *
 * ```
 * <root>/task_formats.json, tags.json
 * <root>/<subject>/subject.json
 * <root>/<subject>/<topic>/topic.json         (any depth)
 * <leaf topic>/tasks.json                     tasks of the leaf topic
 * <leaf topic>/<name>.md                      SUMMARY units (front matter + text)
 * <leaf topic>/<name>.json                    PRACTICE units
 * ```
 *
 * Files named `*_private.json` are never read.
 */
class ContentLoader(private val root: File) {

    private val json = Json { ignoreUnknownKeys = false }
    private val readErrors = mutableListOf<String>()

    fun load(): ContentSet {
        val topics = mutableListOf<LoadedTopic>()
        val summaries = mutableListOf<SummaryUnit>()
        val practices = mutableListOf<PracticeUnit>()
        val tasks = mutableListOf<LoadedTask>()
        val subjects = mutableListOf<SubjectFile>()

        for (subjectDir in childDirs(root)) {
            val subject = read<SubjectFile>(File(subjectDir, "subject.json")) ?: continue
            subjects += subject
            for (topicDir in childDirs(subjectDir)) {
                loadTopic(topicDir, subject.subjectId, null, topics, summaries, practices, tasks)
            }
        }

        return ContentSet(
            taskFormats = read<List<TaskFormatFile>>(File(root, "task_formats.json")) ?: emptyList(),
            tags = readOptional<List<TagFile>>(File(root, "tags.json")) ?: emptyList(),
            subjects = subjects,
            topics = topics,
            summaries = summaries,
            practices = practices,
            tasks = tasks,
            readErrors = readErrors.toList(),
        )
    }

    private fun loadTopic(
        dir: File,
        subjectId: Long?,
        parentTopicId: Long?,
        topics: MutableList<LoadedTopic>,
        summaries: MutableList<SummaryUnit>,
        practices: MutableList<PracticeUnit>,
        tasks: MutableList<LoadedTask>,
    ) {
        val topic = read<TopicFile>(File(dir, "topic.json")) ?: return
        val children = childDirs(dir)
        // only root topics carry the subject (ck_topic_root_subject)
        topics += LoadedTopic(topic, subjectId.takeIf { parentTopicId == null }, parentTopicId, dir, children.isNotEmpty())

        for (file in contentFiles(dir)) {
            when {
                file.name == "topic.json" -> Unit
                file.name == "tasks.json" ->
                    read<List<TaskEntry>>(file)?.forEach { tasks += LoadedTask(topic.topicId, it, file) }
                file.extension == "md" -> readSummary(file, topic.topicId)?.let { summaries += it }
                file.extension == "json" ->
                    read<PracticeUnitFile>(file)?.let { practices += PracticeUnit(topic.topicId, it, file) }
            }
        }

        for (child in children) {
            loadTopic(child, subjectId, topic.topicId, topics, summaries, practices, tasks)
        }
    }

    private fun readSummary(file: File, topicId: Long): SummaryUnit? {
        val text = file.readText().replace("\r\n", "\n")
        val match = FRONT_MATTER.find(text)
        if (match == null) {
            readErrors += "${relative(file)}: missing front matter (--- ... ---)"
            return null
        }
        val fields = match.groupValues[1].lines()
            .filter { it.isNotBlank() }
            .associate { line ->
                val (key, value) = line.split(":", limit = 2).map { it.trim() } + listOf("")
                key to value.removeSurrounding("\"")
            }

        fun required(key: String): String? = fields[key]?.takeIf { it.isNotEmpty() }.also {
            if (it == null) readErrors += "${relative(file)}: front matter field '$key' is missing"
        }
        fun int(key: String): Int? = required(key)?.let { value ->
            value.toIntOrNull().also { if (it == null) readErrors += "${relative(file)}: '$key' is not a number" }
        }

        val id = required("learning_unit_id")?.toLongOrNull()
        val title = required("title")
        val minutes = int("estimated_minutes")
        val sortOrder = int("sort_order")
        if (id == null || title == null || minutes == null || sortOrder == null) return null

        return SummaryUnit(
            learningUnitId = id,
            topicId = topicId,
            title = title,
            bodyText = text.substring(match.range.last + 1).trim(),
            estimatedMinutes = minutes,
            xpReward = fields["xp_reward"]?.toIntOrNull() ?: 10,
            sortOrder = sortOrder,
            isPublished = fields["is_published"] == "true",
            provenance = Provenance(fields["source_type"], fields["source_note"], fields["reviewed_at"]),
            source = file,
        )
    }

    private inline fun <reified T> read(file: File): T? {
        if (!file.isFile) {
            readErrors += "${relative(file)}: file is missing"
            return null
        }
        return readOptional<T>(file)
    }

    private inline fun <reified T> readOptional(file: File): T? {
        if (!file.isFile) return null
        return try {
            json.decodeFromString<T>(file.readText())
        } catch (e: Exception) {
            readErrors += "${relative(file)}: ${e.message?.lineSequence()?.firstOrNull()}"
            null
        }
    }

    private fun childDirs(dir: File): List<File> =
        dir.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }.orEmpty().sortedBy { it.name }

    private fun contentFiles(dir: File): List<File> =
        dir.listFiles { f -> f.isFile && !f.name.endsWith("_private.json") && !f.name.startsWith(".") }
            .orEmpty().sortedBy { it.name }

    fun relative(file: File): String = file.relativeTo(root).invariantSeparatorsPath

    private companion object {
        val FRONT_MATTER = Regex("""\A---\n(.*?)\n---\n?""", RegexOption.DOT_MATCHES_ALL)
    }
}
