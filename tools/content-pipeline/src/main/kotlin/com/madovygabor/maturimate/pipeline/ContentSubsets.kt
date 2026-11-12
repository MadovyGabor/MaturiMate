package com.madovygabor.maturimate.pipeline

/** `FULL` = every lesson and task; `STARTER` = the first lesson of each subject, bundled in the app. */
enum class ContentKind {
    FULL, STARTER;

    companion object {
        fun parse(text: String): ContentKind =
            entries.firstOrNull { it.name.equals(text, ignoreCase = true) }
                ?: throw ExportException("unknown kind: $text (full or starter)")
    }
}

/** The topics of a subject: its root topics (the only ones that carry `subjectId`) and everything below them. */
private fun ContentSet.topicsOf(subjectId: Long): List<LoadedTopic> {
    val ids = topics.filter { it.subjectId == subjectId }.map { it.file.topicId }.toMutableSet()
    do {
        val before = ids.size
        topics.filter { it.parentTopicId in ids }.forEach { ids += it.file.topicId }
    } while (ids.size != before)
    return topics.filter { it.file.topicId in ids }
}

/** Only what belongs to one subject (the shared catalogues, task formats and tags, stay whole). */
fun ContentSet.forSubject(subjectId: Long): ContentSet {
    val subjectTopics = topicsOf(subjectId)
    val topicIds = subjectTopics.map { it.file.topicId }.toSet()
    return copy(
        subjects = subjects.filter { it.subjectId == subjectId },
        topics = subjectTopics,
        summaries = summaries.filter { it.topicId in topicIds },
        practices = practices.filter { it.topicId in topicIds },
        tasks = tasks.filter { it.topicId in topicIds },
    )
}

/**
 * The starter content: for each subject the first published lesson. The path goes down from the first published root
 * topic by `order_index`, taking the first published child at each level, to a leaf; the lesson is the first published
 * `SUMMARY` of that leaf by `sort_order`. The topics above it are kept, no tasks and no practice units. A subject
 * without a published lesson is left out (a warning goes to [warn]).
 */
fun ContentSet.starter(warn: (String) -> Unit = {}): ContentSet {
    val keptTopics = mutableListOf<LoadedTopic>()
    val keptSummaries = mutableListOf<SummaryUnit>()
    val keptSubjects = mutableListOf<SubjectFile>()
    for (subject in subjects) {
        val ofSubject = topicsOf(subject.subjectId).filter { it.file.isPublished }
        var current = ofSubject.filter { it.parentTopicId == null }.minByOrNull { it.file.orderIndex }
        val path = mutableListOf<LoadedTopic>()
        while (current != null) {
            path += current
            val parent = current.file.topicId
            current = ofSubject.filter { it.parentTopicId == parent }.minByOrNull { it.file.orderIndex }
        }
        val leaf = path.lastOrNull()
        val lesson = leaf?.let { l ->
            summaries.filter { it.topicId == l.file.topicId && it.isPublished }.minByOrNull { it.sortOrder }
        }
        if (lesson == null) {
            warn("subject ${subject.code} has no published lesson, it is left out of the starter")
            continue
        }
        keptSubjects += subject
        keptTopics += path
        keptSummaries += lesson
    }
    return copy(subjects = keptSubjects, topics = keptTopics, summaries = keptSummaries, practices = emptyList(), tasks = emptyList())
}
