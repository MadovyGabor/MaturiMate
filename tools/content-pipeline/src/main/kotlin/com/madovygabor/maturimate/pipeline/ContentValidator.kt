package com.madovygabor.maturimate.pipeline

import java.io.File

/**
 * Checks a [ContentSet] against the rules of the database design before anything is written.
 * All problems are collected, so one run reports every error.
 */
class ContentValidator(private val describe: (File) -> String = { it.path }) {

    private val errors = mutableListOf<String>()

    fun validate(content: ContentSet): List<String> {
        errors.clear()
        errors += content.readErrors

        checkCatalogues(content)
        checkTopics(content)
        val formats = content.taskFormats.associateBy { it.code }
        val tagNames = content.tags.map { it.name }.toSet()
        content.tasks.forEach { checkTask(it, formats, tagNames) }
        checkUnits(content)
        checkUniqueIds(content)
        return errors.toList()
    }

    private fun error(where: String, message: String) {
        errors += "$where: $message"
    }

    private fun checkCatalogues(content: ContentSet) {
        content.taskFormats.forEach { f ->
            val where = "task_formats.json (${f.code})"
            length(where, "code", f.code, 30)
            length(where, "name", f.name, 100)
            length(where, "description", f.description, 255)
            oneOf(where, "answer_mode", f.answerMode, ANSWER_MODES)
        }
        content.tags.forEach { t ->
            val where = "tags.json (${t.name})"
            length(where, "name", t.name, 100)
            oneOf(where, "tag_type", t.tagType, TAG_TYPES)
        }
        content.subjects.forEach { s ->
            val where = "subject ${s.code}"
            length(where, "code", s.code, 20)
            length(where, "name_hu", s.nameHu, 100)
            length(where, "name_sk", s.nameSk, 100)
            length(where, "description", s.description, 500)
            oneOf(where, "track", s.track, TRACKS)
        }
    }

    private fun checkTopics(content: ContentSet) {
        content.topics.forEach { t ->
            val where = describe(File(t.folder, "topic.json"))
            length(where, "title", t.file.title, 150)
            length(where, "description", t.file.description, 500)
        }
        // order_index is unique among siblings (uq_topic_parent_order, uq_topic_root_order)
        content.topics.groupBy { it.parentTopicId to it.subjectId }.values.forEach { siblings ->
            siblings.groupBy { it.file.orderIndex }.filterValues { it.size > 1 }.forEach { (order, same) ->
                error(describe(same.first().folder), "order_index $order is used by more than one topic at this level")
            }
        }
        // tasks and learning units belong to leaf topics only
        val nonLeaf = content.topics.filter { it.hasChildFolders }.map { it.file.topicId }.toSet()
        content.tasks.filter { it.topicId in nonLeaf }.map { it.source }.distinct().forEach {
            error(describe(it), "tasks must be in a leaf topic (this topic has sub-topics)")
        }
        (content.summaries.filter { it.topicId in nonLeaf }.map { it.source } +
            content.practices.filter { it.topicId in nonLeaf }.map { it.source }).forEach {
            error(describe(it), "learning units must be in a leaf topic (this topic has sub-topics)")
        }
    }

    private fun checkTask(task: LoadedTask, formats: Map<String, TaskFormatFile>, tagNames: Set<String>) {
        val t = task.entry
        val where = "${describe(task.source)} (task ${t.taskId})"

        if (t.questionText.isBlank()) error(where, "question_text is empty")
        checkText(where, "question_text", t.questionText)
        t.explanation?.let { checkText(where, "explanation", it) }
        if (t.maxPoints < 1) error(where, "max_points must be at least 1")
        if (t.answerWordLimit != null && t.answerWordLimit <= 0) error(where, "answer_word_limit must be positive")
        checkProvenance(where, Provenance(t.sourceType, t.sourceNote, t.reviewedAt))

        t.tags.filter { it !in tagNames }.forEach { error(where, "tag '$it' is not in tags.json") }
        if (t.tags.size != t.tags.toSet().size) error(where, "a tag is listed twice")
        t.attachments.forEach {
            length(where, "file_url", it.fileUrl, 500)
            length(where, "alt_text", it.altText, 300)
            oneOf(where, "media_type", it.mediaType, MEDIA_TYPES)
        }

        val format = formats[t.format]
        if (format == null) {
            error(where, "unknown format '${t.format}'")
            return
        }

        val gaps = GAP_MARKER.findAll(t.questionText).map { it.groupValues[1].toInt() }.toSortedSet()
        val gapFormat = t.format in GAP_FORMATS
        if (gapFormat) {
            if (gaps.isEmpty()) error(where, "${t.format} needs [[n]] gap markers in question_text")
            else if (gaps.toList() != (1..gaps.size).toList()) error(where, "gap markers must be [[1]] to [[n]] without holes")
        } else if (gaps.isNotEmpty()) {
            error(where, "[[n]] gap markers are only allowed in GAP_CHOICE and GAP_FILL")
        }

        when (format.answerMode) {
            "CHOICE" -> checkChoiceTask(where, t, gaps.size)
            "TEXT" -> checkTextTask(where, t, if (gapFormat) gaps.size else 1)
        }
    }

    private fun checkChoiceTask(where: String, t: TaskEntry, gapCount: Int) {
        if (t.acceptedAnswers.isNotEmpty()) error(where, "a choice task cannot have accepted_answers")
        if (t.options.size > MAX_CHILD_ROWS) error(where, "at most $MAX_CHILD_ROWS options")

        t.options.forEach {
            length(where, "option label", it.label, 5)
            length(where, "option text", it.text, 500)
            oneOf(where, "option role", it.role, OPTION_ROLES)
            checkText(where, "option text", it.text)
        }
        t.options.groupBy { it.role to it.label }.filterValues { it.size > 1 }.keys.forEach { (role, label) ->
            error(where, "option label '$label' is used twice for role $role")
        }

        val answers = t.options.filter { it.role == "ANSWER" }.map { it.label }.toSet()
        val prompts = t.options.filter { it.role == "PROMPT" }.map { it.label }.toSet()
        if (answers.size < 2) error(where, "a choice task needs at least two ANSWER options")
        if (prompts.isNotEmpty() && t.format != "MATCHING") error(where, "only MATCHING tasks can have PROMPT options")

        val slots = t.key.map { it.slot }
        if (t.key.isEmpty()) error(where, "a choice task needs a key")
        if (slots.sorted() != (1..slots.size).toList()) error(where, "key slots must be 1 to n, each once")
        t.key.forEach { k ->
            if (k.option !in answers) error(where, "key slot ${k.slot} points to '${k.option}', which is not an ANSWER option")
            if (t.format == "MATCHING") {
                if (k.prompt == null) error(where, "MATCHING key slot ${k.slot} needs a prompt")
                else if (k.prompt !in prompts) error(where, "key slot ${k.slot} prompt '${k.prompt}' is not a PROMPT option")
            } else if (k.prompt != null) {
                error(where, "only MATCHING keys can have a prompt")
            }
        }

        when (t.format) {
            "MC_SINGLE" -> if (t.key.size != 1) error(where, "MC_SINGLE has exactly one key (slot 1)")
            "TRUE_FALSE" -> {
                if (answers.size != 2) error(where, "TRUE_FALSE has exactly two options")
                if (t.key.size != 1) error(where, "TRUE_FALSE has exactly one key (slot 1)")
            }
            "MATCHING" -> {
                if (prompts.size < 2) error(where, "MATCHING needs at least two PROMPT options")
                if (t.key.size != prompts.size) error(where, "MATCHING needs one key per PROMPT option")
                if (t.key.mapNotNull { it.prompt }.toSet().size != t.key.size) error(where, "each prompt is matched once")
            }
            "ORDERING" -> {
                if (t.key.size != answers.size) error(where, "ORDERING needs one key per item")
                if (t.key.map { it.option }.toSet().size != t.key.size) error(where, "each item appears once in the order")
            }
            "GAP_CHOICE" -> if (t.key.size != gapCount) error(where, "GAP_CHOICE needs one key per gap")
        }
    }

    private fun checkTextTask(where: String, t: TaskEntry, slotCount: Int) {
        if (t.options.isNotEmpty()) error(where, "a typed-answer task cannot have options")
        if (t.key.isNotEmpty()) error(where, "a typed-answer task cannot have a key")
        if (t.acceptedAnswers.size > MAX_CHILD_ROWS) error(where, "at most $MAX_CHILD_ROWS accepted answers")
        t.acceptedAnswers.forEach {
            length(where, "accepted answer", it.text, 300)
            if (it.text.isBlank()) error(where, "an accepted answer is empty")
            if (it.slot !in 1..slotCount) error(where, "accepted answer slot ${it.slot} has no gap")
        }
        (1..slotCount).filter { slot -> t.acceptedAnswers.none { it.slot == slot } }.forEach {
            error(where, "slot $it has no accepted answer")
        }
        t.acceptedAnswers.groupBy { it.slot to it.text }.filterValues { it.size > 1 }.keys.forEach { (slot, text) ->
            error(where, "accepted answer '$text' is listed twice for slot $slot")
        }
    }

    private fun checkUnits(content: ContentSet) {
        val tasksById = content.tasks.associateBy { it.entry.taskId }

        content.summaries.forEach { u ->
            val where = describe(u.source)
            length(where, "title", u.title, 200)
            if (u.bodyText.isBlank()) error(where, "a SUMMARY unit needs text")
            checkText(where, "text", u.bodyText)
            positive(where, "estimated_minutes", u.estimatedMinutes)
            if (u.xpReward < 0) error(where, "xp_reward cannot be negative")
            checkProvenance(where, u.provenance)
        }

        content.practices.forEach { p ->
            val u = p.file
            val where = describe(p.source)
            length(where, "title", u.title, 200)
            positive(where, "estimated_minutes", u.estimatedMinutes)
            positive(where, "round_size", u.roundSize)
            if (u.xpReward < 0) error(where, "xp_reward cannot be negative")
            checkProvenance(where, Provenance(u.sourceType, u.sourceNote, u.reviewedAt))

            if (u.tasks.isEmpty()) error(where, "a PRACTICE unit needs tasks")
            u.tasks.filter { it.taskId !in tasksById }.forEach { error(where, "task ${it.taskId} does not exist") }
            if (u.tasks.map { it.taskId }.toSet().size != u.tasks.size) error(where, "a task is listed twice")
            val core = u.tasks.count { it.isCore }
            if (u.roundSize > core) error(where, "round_size ${u.roundSize} is larger than the $core core tasks")
        }

        // sort_order is unique within a topic (uq_learning_unit_order)
        val orders = content.summaries.map { Triple(it.topicId, it.sortOrder, it.source) } +
            content.practices.map { Triple(it.topicId, it.file.sortOrder, it.source) }
        orders.groupBy { it.first to it.second }.filterValues { it.size > 1 }.forEach { (key, same) ->
            error(describe(same.first().third), "sort_order ${key.second} is used by more than one unit in this topic")
        }
    }

    private fun checkUniqueIds(content: ContentSet) {
        fun duplicates(table: String, ids: List<Long>) =
            ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach {
                errors += "$table: id $it is used more than once"
            }
        duplicates("task_format", content.taskFormats.map { it.taskFormatId })
        duplicates("tag", content.tags.map { it.tagId })
        duplicates("subject", content.subjects.map { it.subjectId })
        duplicates("topic", content.topics.map { it.file.topicId })
        duplicates("task", content.tasks.map { it.entry.taskId })
        duplicates("learning_unit", content.summaries.map { it.learningUnitId } + content.practices.map { it.file.learningUnitId })
        content.taskFormats.groupBy { it.code }.filterValues { it.size > 1 }.keys.forEach { errors += "task_format: code $it is used twice" }
        content.tags.groupBy { it.name }.filterValues { it.size > 1 }.keys.forEach { errors += "tag: name $it is used twice" }
        content.subjects.groupBy { it.code }.filterValues { it.size > 1 }.keys.forEach { errors += "subject: code $it is used twice" }
    }

    private fun checkProvenance(where: String, p: Provenance) {
        when (p.sourceType) {
            null -> error(where, "source_type is missing")
            !in SOURCE_TYPES -> error(where, "source_type must be one of $SOURCE_TYPES")
            "AI_REVIEWED" -> if (p.reviewedAt == null || !DATE.matches(p.reviewedAt))
                error(where, "AI_REVIEWED needs reviewed_at (YYYY-MM-DD)")
            "PUBLIC_DOMAIN" -> if (p.sourceNote.isNullOrBlank()) error(where, "PUBLIC_DOMAIN needs source_note (work and edition)")
            "TEACHER" -> if (p.sourceNote.isNullOrBlank()) error(where, "TEACHER needs source_note (reference to the written permission)")
        }
        length(where, "source_note", p.sourceNote, 300)
    }

    /** Only `**bold**`, `*italic*` and `[[n]]` are allowed in texts. */
    private fun checkText(where: String, field: String, text: String) {
        FORBIDDEN_MARKUP.forEach { (pattern, name) ->
            if (pattern.containsMatchIn(text)) error(where, "$field contains $name, which the text format does not support")
        }
        val singleStars = text.replace("**", "").count { it == '*' }
        if (text.split("**").size % 2 == 0) error(where, "$field has an unclosed **")
        if (singleStars % 2 != 0) error(where, "$field has an unclosed *")
    }

    private fun length(where: String, field: String, value: String?, max: Int) {
        if (value != null && value.length > max) error(where, "$field is longer than $max characters")
    }

    private fun oneOf(where: String, field: String, value: String, allowed: Set<String>) {
        if (value !in allowed) error(where, "$field '$value' must be one of $allowed")
    }

    private fun positive(where: String, field: String, value: Int) {
        if (value <= 0) error(where, "$field must be positive")
    }

    companion object {
        /** Child rows get ids `task_id * 100 + position`, so a task has at most 99 of each. */
        const val MAX_CHILD_ROWS = 99

        val ANSWER_MODES = setOf("CHOICE", "TEXT")
        val TAG_TYPES = setOf("AUTHOR", "WORK", "PERIOD", "CONCEPT")
        val TRACKS = setOf("HU", "SK", "BOTH")
        val MEDIA_TYPES = setOf("IMAGE", "DIAGRAM", "AUDIO")
        val OPTION_ROLES = setOf("ANSWER", "PROMPT")
        val SOURCE_TYPES = setOf("OWN", "TEACHER", "AI_REVIEWED", "PUBLIC_DOMAIN")
        val GAP_FORMATS = setOf("GAP_CHOICE", "GAP_FILL")

        private val GAP_MARKER = Regex("""\[\[(\d+)]]""")
        private val DATE = Regex("""\d{4}-\d{2}-\d{2}""")
        private val FORBIDDEN_MARKUP = listOf(
            Regex("""</?[a-zA-Z][^>]*>""") to "HTML",
            Regex("""]\(""") to "a link",
            Regex("""(?m)^\s{0,3}#{1,6}\s""") to "a heading",
            Regex("""(?m)^\s{0,3}>""") to "a quote block",
            Regex("""(?m)^\s*([-+]|\d+\.)\s""") to "a list",
            Regex("""`""") to "code",
            Regex("""__""") to "underline",
        )
    }
}
