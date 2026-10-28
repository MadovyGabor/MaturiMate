package com.madovygabor.maturimate.features.learning.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.ContentDatabaseSource
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.database.content.ContentDatabase
import com.madovygabor.maturimate.database.content.Learning_unit
import com.madovygabor.maturimate.database.content.Subject
import com.madovygabor.maturimate.database.content.Task
import com.madovygabor.maturimate.database.content.Task_accepted_answer
import com.madovygabor.maturimate.database.content.Task_format
import com.madovygabor.maturimate.database.content.Task_key
import com.madovygabor.maturimate.database.content.Task_option
import com.madovygabor.maturimate.database.content.Topic
import com.madovygabor.maturimate.database.content.Unit_task
import com.madovygabor.maturimate.features.learning.domain.LearningError
import com.madovygabor.maturimate.features.learning.domain.model.AcceptedAnswer
import com.madovygabor.maturimate.features.learning.domain.model.KeySlot
import com.madovygabor.maturimate.features.learning.domain.model.LearningUnit
import com.madovygabor.maturimate.features.learning.domain.model.TaskFormat
import com.madovygabor.maturimate.features.learning.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.fail

class ContentRepositoryImplTest {

    private val database = ContentDatabase(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { ContentDatabase.Schema.create(it) }
    ).apply {
        subjectQueries.insert(Subject(1, "HU_MJL", "Magyar", "Maďarčina", "HU", null))
        topicQueries.insert(Topic(10, 1, null, "Stilisztika", null, 1, true))
        topicQueries.insert(Topic(11, null, 10, "Szóképek", null, 1, true))
        topicQueries.insert(Topic(12, null, 10, "Rejtett", null, 2, false))
        taskFormatQueries.insert(Task_format(1, "MATCHING", "Matching", "CHOICE", null))
        taskFormatQueries.insert(Task_format(2, "GAP_FILL", "Gap fill", "TEXT", null))
        taskQueries.insert(Task(100, 11, 1, "Párosítsd!", null, 2, null, true, 0))
        taskQueries.insertOption(Task_option(10001, 100, "PROMPT", "1", "Olyan, mint a tűz."))
        taskQueries.insertOption(Task_option(10002, 100, "ANSWER", "A", "hasonlat"))
        taskQueries.insertKey(Task_key(100, 1, 10001, 10002))
        taskQueries.insert(Task(101, 11, 2, "A metafora [[1]].", null, 1, null, false, 0))
        taskQueries.insertAcceptedAnswer(Task_accepted_answer(10101, 101, 1, "azonosít", false))
        learningUnitQueries.insert(Learning_unit(50, 11, "SUMMARY", "Mi a szókép?", "A **szókép**...", 3, 5, 10, 1, true, 0))
        learningUnitQueries.insert(Learning_unit(51, 11, "PRACTICE", "Gyakorlás", null, 4, 1, 10, 2, true, 0))
        learningUnitQueries.insertUnitTask(Unit_task(51, 101, 1, false))
        learningUnitQueries.insertUnitTask(Unit_task(51, 100, 2, true))
    }

    private val repository = ContentRepositoryImpl({ database }, Dispatchers.Unconfined)

    private fun <T> OperationResult<T, LearningError>.data(): T = when (this) {
        is OperationResult.Success -> data
        is OperationResult.Error -> fail("expected success, got $error")
    }

    @Test
    fun subjectsAreMapped() = runBlocking {
        val subject = repository.subjects().data().single()
        assertEquals("HU_MJL", subject.code)
        assertEquals(Track.HU, subject.track)
    }

    @Test
    fun onlyPublishedTopicsAreReturned() = runBlocking {
        assertEquals(listOf(10L), repository.rootTopics(1).data().map { it.id })
        assertEquals(listOf(11L), repository.childTopics(10).data().map { it.id })
        assertEquals(listOf(10L, 11L, 12L), repository.subtreeTopicIds(10).data())
    }

    @Test
    fun unitsAreMappedByType() = runBlocking {
        val (summary, practice) = repository.units(11).data()
        assertIs<LearningUnit.Summary>(summary)
        assertEquals("A **szókép**...", summary.bodyText)
        assertIs<LearningUnit.Practice>(practice)
        assertEquals(1, practice.roundSize)
    }

    @Test
    fun unitTasksListCoreTasksFirst() = runBlocking {
        val tasks = repository.unitTasks(51).data()
        assertEquals(listOf(100L, 101L), tasks.map { it.taskId })
        assertEquals(listOf(true, false), tasks.map { it.isCore })
        assertEquals(false, tasks.last().isActive)
    }

    @Test
    fun keysAreMappedToOptionLabels() = runBlocking {
        val task = repository.task(100).data()
        assertEquals(TaskFormat.MATCHING, task.format)
        assertEquals(listOf(KeySlot(slot = 1, optionLabel = "A", promptLabel = "1")), task.key)
    }

    @Test
    fun typedTaskHasAcceptedAnswers() = runBlocking {
        val task = repository.task(101).data()
        assertEquals(listOf(AcceptedAnswer(1, "azonosít", caseSensitive = false)), task.acceptedAnswers)
        assertEquals(false, task.isActive)
    }

    @Test
    fun missingRowIsNotFound() = runBlocking {
        assertEquals(OperationResult.Error(LearningError.NOT_FOUND), repository.task(999))
        assertEquals(OperationResult.Error(LearningError.NOT_FOUND), repository.unit(999))
    }

    @Test
    fun noInstalledContentIsReported() = runBlocking {
        val empty = ContentRepositoryImpl(ContentDatabaseSource { null }, Dispatchers.Unconfined)
        assertEquals(OperationResult.Error(LearningError.NO_CONTENT), empty.subjects())
    }
}
