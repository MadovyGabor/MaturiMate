package com.madovygabor.maturimate.pipeline

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class ContentValidatorTest {

    private val fixture = File(javaClass.getResource("/content-valid")!!.toURI())
    private val valid = ContentLoader(fixture).load()

    private fun errorsOf(content: ContentSet) = ContentValidator().validate(content)

    private fun assertError(content: ContentSet, expected: String) {
        val errors = errorsOf(content)
        if (errors.none { expected in it }) fail("expected an error containing '$expected', got $errors")
    }

    private fun withTask(taskId: Long, change: (TaskEntry) -> TaskEntry) =
        valid.copy(tasks = valid.tasks.map { if (it.entry.taskId == taskId) it.copy(entry = change(it.entry)) else it })

    @Test
    fun validFixtureHasNoErrors() {
        assertEquals(emptyList(), errorsOf(valid))
        assertEquals(2, valid.topics.size)
        assertEquals(1, valid.summaries.size)
        assertEquals(1, valid.practices.size)
        assertEquals(5, valid.tasks.size)
    }

    @Test
    fun summaryFrontMatterAndTextAreRead() {
        val summary = valid.summaries.single()
        assertEquals(100, summary.learningUnitId)
        assertEquals("OWN", summary.provenance.sourceType)
        assertTrue(summary.bodyText.startsWith("A **szókép**"))
    }

    @Test
    fun privateFilesAreNeverRead() {
        // notes_private.json in the fixture is not valid JSON; reading it would add a read error
        assertTrue(valid.readErrors.isEmpty())
    }

    @Test
    fun onlyTheRootTopicCarriesTheSubject() {
        val (root, leaf) = valid.topics
        assertEquals(1L, root.subjectId)
        assertEquals(null, root.parentTopicId)
        assertEquals(null, leaf.subjectId)
        assertEquals(10L, leaf.parentTopicId)
    }

    @Test
    fun mcSingleNeedsExactlyOneKey() = assertError(
        withTask(1001) { it.copy(key = it.key + KeyEntry(slot = 2, option = "A")) },
        "MC_SINGLE has exactly one key",
    )

    @Test
    fun keyMustPointToAnExistingOption() = assertError(
        withTask(1001) { it.copy(key = listOf(KeyEntry(option = "Z"))) },
        "not an ANSWER option",
    )

    @Test
    fun trueFalseNeedsTwoOptions() = assertError(
        withTask(1002) { it.copy(options = it.options + OptionEntry("X", "talán")) },
        "TRUE_FALSE has exactly two options",
    )

    @Test
    fun promptsOnlyInMatching() = assertError(
        withTask(1001) { it.copy(options = it.options + OptionEntry("1", "x", role = "PROMPT")) },
        "only MATCHING tasks can have PROMPT options",
    )

    @Test
    fun matchingNeedsOneKeyPerPrompt() = assertError(
        withTask(1004) { it.copy(key = it.key.take(1)) },
        "one key per PROMPT option",
    )

    @Test
    fun gapMarkersMustNotHaveHoles() = assertError(
        withTask(1005) { it.copy(questionText = "A [[1]] és a [[3]].") },
        "without holes",
    )

    @Test
    fun everyGapNeedsAnAcceptedAnswer() = assertError(
        withTask(1005) { it.copy(acceptedAnswers = it.acceptedAnswers.filter { a -> a.slot == 1 }) },
        "slot 2 has no accepted answer",
    )

    @Test
    fun gapMarkersOnlyInGapFormats() = assertError(
        withTask(1003) { it.copy(questionText = "Mi ez: [[1]]?") },
        "only allowed in GAP_CHOICE and GAP_FILL",
    )

    @Test
    fun typedTaskCannotHaveOptions() = assertError(
        withTask(1003) { it.copy(options = listOf(OptionEntry("A", "x"))) },
        "cannot have options",
    )

    @Test
    fun htmlIsRejected() = assertError(
        withTask(1001) { it.copy(questionText = "Melyik <b>szókép</b>?") },
        "contains HTML",
    )

    @Test
    fun unclosedBoldIsRejected() = assertError(
        withTask(1001) { it.copy(questionText = "Melyik **szókép?") },
        "unclosed **",
    )

    @Test
    fun unknownTagIsRejected() = assertError(
        withTask(1001) { it.copy(tags = listOf("nincs ilyen")) },
        "is not in tags.json",
    )

    @Test
    fun unknownFormatIsRejected() = assertError(
        withTask(1001) { it.copy(format = "ESSAY") },
        "unknown format",
    )

    @Test
    fun sourceTypeIsRequired() = assertError(
        withTask(1001) { it.copy(sourceType = null) },
        "source_type is missing",
    )

    @Test
    fun aiReviewedNeedsReviewDate() = assertError(
        withTask(1003) { it.copy(reviewedAt = null) },
        "AI_REVIEWED needs reviewed_at",
    )

    @Test
    fun publicDomainNeedsSourceNote() = assertError(
        withTask(1001) { it.copy(sourceType = "PUBLIC_DOMAIN") },
        "PUBLIC_DOMAIN needs source_note",
    )

    @Test
    fun taskIdsAreUnique() = assertError(
        valid.copy(tasks = valid.tasks + valid.tasks.first()),
        "task: id 1001 is used more than once",
    )

    @Test
    fun roundSizeCannotExceedCoreTasks() = assertError(
        valid.copy(practices = valid.practices.map { it.copy(file = it.file.copy(roundSize = 4)) }),
        "round_size 4 is larger than the 3 core tasks",
    )

    @Test
    fun practiceTasksMustExist() = assertError(
        valid.copy(practices = valid.practices.map {
            it.copy(file = it.file.copy(tasks = it.file.tasks + UnitTaskEntry(9999)))
        }),
        "task 9999 does not exist",
    )

    @Test
    fun tasksOnlyInLeafTopics() = assertError(
        valid.copy(tasks = valid.tasks.map { it.copy(topicId = 10) }),
        "tasks must be in a leaf topic",
    )

    @Test
    fun unitSortOrderIsUniqueInTopic() = assertError(
        valid.copy(practices = valid.practices.map { it.copy(file = it.file.copy(sortOrder = 1)) }),
        "sort_order 1 is used by more than one unit",
    )
}
