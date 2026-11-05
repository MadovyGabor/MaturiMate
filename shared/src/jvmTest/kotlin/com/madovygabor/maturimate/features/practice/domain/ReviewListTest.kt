package com.madovygabor.maturimate.features.practice.domain

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.database.content.ContentDatabase
import com.madovygabor.maturimate.database.content.Learning_unit
import com.madovygabor.maturimate.database.content.Page_block
import com.madovygabor.maturimate.database.content.Subject
import com.madovygabor.maturimate.database.content.Task
import com.madovygabor.maturimate.database.content.Task_format
import com.madovygabor.maturimate.database.content.Topic
import com.madovygabor.maturimate.database.content.Unit_page
import com.madovygabor.maturimate.database.content.Unit_task
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.learning.data.ContentRepositoryImpl
import com.madovygabor.maturimate.features.learning.data.ProgressRepositoryImpl
import com.madovygabor.maturimate.features.learning.domain.LearningError
import com.madovygabor.maturimate.features.learning.domain.model.AttemptAnswer
import com.madovygabor.maturimate.features.learning.domain.model.AttemptType
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgress
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgressStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * "Last seen" and the review list. Subject 1, part "Nyelvtan" (10) with the topics 11 to 17, each with a lesson
 * (topic * 10) and a test (topic * 10 + 1) with one task (topic * 10 + 5), and the hidden topic 18 built the same
 * way. Today is 2026-11-20 at noon, UTC.
 */
@OptIn(ExperimentalTime::class)
class ReviewListTest {

    private val database = ContentDatabase(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { ContentDatabase.Schema.create(it) }
    ).apply {
        subjectQueries.insert(Subject(1, "HU_MJL", "Magyar", "Maďarčina", "HU", null))
        topicQueries.insert(Topic(10, 1, null, "Nyelvtan", null, 1, true))
        taskFormatQueries.insert(Task_format(1, "MC_SINGLE", "MC", "CHOICE", null))
        (11L..18L).forEach { topic ->
            topicQueries.insert(Topic(topic, null, 10, "Téma $topic", null, topic, topic != 18L))
            learningUnitQueries.insert(Learning_unit(topic * 10, topic, "SUMMARY", "Lecke", null, 3, 5, 10, 1, true, 0))
            learningUnitQueries.insertPage(Unit_page(topic * 10, 1, null))
            learningUnitQueries.insertBlock(Page_block(topic * 10, 1, 1, "PARAGRAPH", "Szöveg"))
            learningUnitQueries.insert(Learning_unit(topic * 10 + 1, topic, "PRACTICE", "Teszt", "MINI", 2, 3, 10, 2, true, 0))
            taskQueries.insert(Task(topic * 10 + 5, topic, 1, "Kérdés", null, 1, null, true, 0))
            learningUnitQueries.insertUnitTask(Unit_task(topic * 10 + 1, topic * 10 + 5, 1, true))
        }
    }

    private val content = ContentRepositoryImpl({ database }, Dispatchers.Unconfined)
    private val userDatabase = UserDatabase(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) }
    )
    private val progress = ProgressRepositoryImpl(userDatabase, LocalUserProvider(userDatabase), Dispatchers.Unconfined)
    private val today = Instant.parse("2026-11-20T12:00:00Z")
    private val list = ReviewList(content, progress, now = { today }, timeZone = { TimeZone.UTC })

    private fun <T> OperationResult<T, LearningError>.data(): T = when (this) {
        is OperationResult.Success -> data
        is OperationResult.Error -> fail("expected success, got $error")
    }

    /** The lesson and the test of [topic] finished [daysAgo] days ago. */
    private fun finished(topic: Long, daysAgo: Int) = runBlocking {
        val at = today - daysAgo.days
        listOf(topic * 10, topic * 10 + 1).forEach { progress.saveUnitProgress(UnitProgress(it, UnitProgressStatus.COMPLETED, at, at)).data() }
    }

    /** An answer to the task of [topic], [daysAgo] days ago, in a practice round. */
    private fun answered(topic: Long, daysAgo: Int) = runBlocking {
        val at = today - daysAgo.days
        val attempt = progress.startAttempt(AttemptType.FREE_PRACTICE, null, null, at).data()
        progress.recordAnswer(AttemptAnswer(attempt, topic * 10 + 5, true, 1, 1, at, "1=A")).data()
    }

    private fun load() = runBlocking { list.load().data() }

    @Test
    fun aFinishedTopicNotSeenSinceYesterdayIsDue() {
        finished(11, daysAgo = 1)
        finished(12, daysAgo = 0)

        val items = load()
        assertEquals(listOf(11L), items.map { it.topic.id })
        assertEquals(1, items.single().daysAgo)
        assertEquals("Magyar", items.single().subject.nameHu)
    }

    @Test
    fun aNewerAnswerMakesTheTopicFreshAgain() {
        finished(11, daysAgo = 30)
        finished(12, daysAgo = 30)
        // practice on topic 11 today: it is seen now
        answered(11, daysAgo = 0)

        assertEquals(listOf(12L), load().map { it.topic.id })
    }

    @Test
    fun theNewestOfTheLessonAndTheAnswersCounts() {
        finished(11, daysAgo = 40)
        answered(11, daysAgo = 25)
        // the lesson read again ten days ago
        runBlocking { progress.saveUnitProgress(UnitProgress(110, UnitProgressStatus.COMPLETED, today - 40.days, today - 10.days)).data() }

        assertEquals(10, load().single().daysAgo)
    }

    @Test
    fun onlyFinishedPublishedTopicsAreOffered() {
        // the test of 13 is not done; 18 is hidden
        runBlocking { progress.saveUnitProgress(UnitProgress(130, UnitProgressStatus.COMPLETED, today - 9.days, today - 9.days)).data() }
        answered(13, daysAgo = 9)
        finished(18, daysAgo = 9)

        assertTrue(load().isEmpty())
    }

    @Test
    fun theOldestComeFirstAndAtMostFive() {
        finished(11, daysAgo = 3)
        finished(12, daysAgo = 30)
        finished(13, daysAgo = 8)
        finished(14, daysAgo = 2)
        finished(15, daysAgo = 15)
        finished(16, daysAgo = 4)
        finished(17, daysAgo = 1)

        val items = load()
        assertEquals(listOf(12L, 15L, 13L, 16L, 11L), items.map { it.topic.id })
        assertEquals(listOf(30, 15, 8, 4, 3), items.map { it.daysAgo })
    }

    @Test
    fun startingOverClearsTheList() {
        finished(11, daysAgo = 5)
        assertTrue(load().isNotEmpty())

        runBlocking { progress.resetProgress().data() }
        assertTrue(load().isEmpty())
    }
}
