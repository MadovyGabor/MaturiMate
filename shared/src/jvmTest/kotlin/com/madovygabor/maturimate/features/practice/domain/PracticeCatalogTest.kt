package com.madovygabor.maturimate.features.practice.domain

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
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
import com.madovygabor.maturimate.features.learning.data.ContentRepositoryImpl
import com.madovygabor.maturimate.features.learning.domain.LearningError
import com.madovygabor.maturimate.features.learning.domain.ProgressRepository
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgress
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgressStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The sample content: subject 1 with
 * * the part "Nyelvtan" (10): the topics "Szóképek" (11: lesson 50, mini test 51 with tasks 100 and 101, topic test 52
 *   with tasks 101, 102 and the switched-off 103), "Alakzatok" (12: lesson 60, mini test 61 with task 110), a hidden
 *   topic (13: mini test 81 with task 130) and a final test 70 on the part with task 120;
 * * the part "Irodalom" (20): "Romantika" (21: only a lesson 80) and "Realizmus" (22: no unit);
 * * the part "Szövegértés" (30), which is a topic itself: mini test 91 with task 140.
 */
@OptIn(ExperimentalTime::class)
class PracticeCatalogTest {

    private val database = ContentDatabase(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { ContentDatabase.Schema.create(it) }
    ).apply {
        subjectQueries.insert(Subject(1, "HU_MJL", "Magyar", "Maďarčina", "HU", null))
        topicQueries.insert(Topic(10, 1, null, "Nyelvtan", null, 1, true))
        topicQueries.insert(Topic(11, null, 10, "Szóképek", null, 1, true))
        topicQueries.insert(Topic(12, null, 10, "Alakzatok", null, 2, true))
        topicQueries.insert(Topic(13, null, 10, "Rejtett", null, 3, false))
        topicQueries.insert(Topic(20, 1, null, "Irodalom", null, 2, true))
        topicQueries.insert(Topic(21, null, 20, "Romantika", null, 1, true))
        topicQueries.insert(Topic(22, null, 20, "Realizmus", null, 2, true))
        topicQueries.insert(Topic(30, 1, null, "Szövegértés", null, 3, true))
        taskFormatQueries.insert(Task_format(1, "MC_SINGLE", "Multiple choice", "CHOICE", null))
        lesson(50, 11, 1)
        practice(51, 11, 2, "MINI", 100, 101)
        practice(52, 11, 3, "TOPIC", 101, 102, 103)
        lesson(60, 12, 1)
        practice(61, 12, 2, "MINI", 110)
        practice(70, 10, 1, "FINAL", 120)
        lesson(80, 21, 1)
        practice(81, 13, 1, "MINI", 130)
        practice(91, 30, 1, "MINI", 140)
    }

    private fun ContentDatabase.lesson(id: Long, topic: Long, order: Long) {
        learningUnitQueries.insert(Learning_unit(id, topic, "SUMMARY", "Lecke $id", null, 3, 5, 10, order, true, 0))
        learningUnitQueries.insertPage(Unit_page(id, 1, null))
        learningUnitQueries.insertBlock(Page_block(id, 1, 1, "PARAGRAPH", "Szöveg"))
    }

    private fun ContentDatabase.practice(id: Long, topic: Long, order: Long, kind: String, vararg tasks: Long) {
        learningUnitQueries.insert(Learning_unit(id, topic, "PRACTICE", "Teszt $id", kind, 4, 3, 10, order, true, 0))
        tasks.forEachIndexed { index, task ->
            // the task row is shared between units; the first unit that uses it creates it (103 is switched off)
            if (taskQueries.selectById(task).executeAsOneOrNull() == null) {
                taskQueries.insert(Task(task, topic, 1, "Kérdés $task", null, 1, null, task != 103L, 0))
            }
            learningUnitQueries.insertUnitTask(Unit_task(id, task, index + 1L, true))
        }
    }

    private val content = ContentRepositoryImpl({ database }, Dispatchers.Unconfined)

    private fun catalog(vararg completed: Long, progressFails: Boolean = false) = PracticeCatalog(
        content,
        object : ProgressRepository by unsupported<ProgressRepository>() {
            override suspend fun unitProgress(): OperationResult<List<UnitProgress>, LearningError> =
                if (progressFails) OperationResult.Error(LearningError.DATABASE_ERROR)
                else OperationResult.Success(completed.map {
                    UnitProgress(it, UnitProgressStatus.COMPLETED, Instant.fromEpochMilliseconds(0), Instant.fromEpochMilliseconds(1))
                })
        },
    )

    private fun <T> OperationResult<T, LearningError>.data(): T = when (this) {
        is OperationResult.Success -> data
        is OperationResult.Error -> fail("expected success, got $error")
    }

    private fun List<PracticeSubject>.node(topicId: Long): PracticeNode {
        fun find(nodes: List<PracticeNode>): PracticeNode? =
            nodes.firstNotNullOfOrNull { if (it.topic.id == topicId) it else find(it.children) }
        return find(single().parts) ?: fail("no node $topicId")
    }

    @Test
    fun aTopicIsFinishedWhenEveryLessonAndTestOfItIsDone() = runBlocking {
        val open = catalog(50, 51).subjects().data().node(11)
        assertFalse(open.finished)
        assertEquals(0, open.taskCount)
        assertEquals(emptyList(), open.leafIds)

        val done = catalog(50, 51, 52).subjects().data().node(11)
        assertTrue(done.finished)
        assertEquals(listOf(11L), done.leafIds)
    }

    @Test
    fun aTaskInSeveralTestsAndASwitchedOffTaskAreCountedRight() = runBlocking {
        // tasks 100, 101 (in two tests) and 102; 103 is switched off
        assertEquals(3, catalog(50, 51, 52).subjects().data().node(11).taskCount)
    }

    @Test
    fun aPartIsFinishedWhenAllItsTopicsAreAndListsOnlyTheFinishedOnes() = runBlocking {
        val all = catalog(50, 51, 52, 60, 61).subjects().data().node(10)
        assertTrue(all.finished)
        // the hidden topic and the final test of the part add nothing
        assertEquals(4, all.taskCount)
        assertEquals(listOf(11L, 12L), all.leafIds)
        assertEquals(listOf("Szóképek", "Alakzatok"), all.children.map { it.topic.title })

        val some = catalog(50, 51, 52, 60).subjects().data().node(10)
        assertFalse(some.finished)
        assertEquals(3, some.taskCount)
        assertEquals(listOf(11L), some.leafIds)
    }

    @Test
    fun aFinishedTopicWithNoTaskIsFinishedButCannotBePracticed() = runBlocking {
        val node = catalog(80).subjects().data().node(21)
        assertTrue(node.finished)
        assertEquals(0, node.taskCount)
        assertEquals(emptyList(), node.leafIds)
    }

    @Test
    fun aTopicWithNoUnitIsNeverFinished() = runBlocking {
        val tree = catalog(80).subjects().data()
        assertFalse(tree.node(22).finished)
        assertFalse(tree.node(20).finished)
    }

    @Test
    fun aPartThatIsATopicItselfIsALeaf() = runBlocking {
        val node = catalog(91).subjects().data().node(30)
        assertTrue(node.finished)
        assertEquals(1, node.taskCount)
        assertEquals(listOf(30L), node.leafIds)
    }

    @Test
    fun theTreeHasTheSubjectAndItsPartsInOrder() = runBlocking {
        val subjects = catalog().subjects().data()
        assertEquals(listOf("HU_MJL"), subjects.map { it.subject.code })
        assertEquals(listOf("Nyelvtan", "Irodalom", "Szövegértés"), subjects.single().parts.map { it.topic.title })
        assertFalse(subjects.single().parts.any { it.finished })
    }

    @Test
    fun theHiddenTopicIsNotInTheTree() = runBlocking {
        val part = catalog(50, 51, 52, 60, 61, 81).subjects().data().node(10)
        assertEquals(listOf(11L, 12L), part.children.map { it.topic.id })
    }

    @Test
    fun theSelectionGivesTheTasksOfTheFinishedTopicsOnce() = runBlocking {
        val catalog = catalog(50, 51, 52, 60, 61)
        assertEquals(setOf(100L, 101L, 102L, 110L), catalog.pool(listOf(10)).data().toSet())
        // a topic named twice, and a part with a topic of it, add nothing twice
        val pool = catalog.pool(listOf(11, 10, 11)).data()
        assertEquals(4, pool.size)
        assertEquals(pool.size, pool.toSet().size)
        assertEquals(setOf(110L), catalog.pool(listOf(12)).data().toSet())
    }

    @Test
    fun aTopicThatIsNotFinishedUnknownHiddenOrWithoutTasksAddsNothing() = runBlocking {
        val catalog = catalog(50, 51, 60, 61, 80)
        assertEquals(emptyList(), catalog.pool(listOf(11)).data()) // lesson done, test 52 is not
        assertEquals(setOf(110L), catalog.pool(listOf(10)).data().toSet()) // only the finished topic of the part
        assertEquals(emptyList(), catalog.pool(listOf(999)).data())
        assertEquals(emptyList(), catalog.pool(listOf(13)).data())
        assertEquals(emptyList(), catalog.pool(listOf(21, 22)).data())
        assertEquals(emptyList(), catalog.pool(emptyList()).data())
    }

    @Test
    fun theTasksOfOneTopicCountFinishedOrNot() = runBlocking {
        // nothing finished: the pool of a topic still gives its tasks (once, without the switched-off 103)
        assertEquals(setOf(100L, 101L, 102L), catalog().topicPool(listOf(11)).data().toSet())
        assertEquals(setOf(100L, 101L, 102L, 110L), catalog().topicPool(listOf(10)).data().toSet())
        assertEquals(emptyList(), catalog().topicPool(listOf(13)).data())
    }

    @Test
    fun aHiddenTopicAddsNothingEvenWhenFinished() = runBlocking {
        // the test 81 of the hidden topic 13 is done, so the topic would be finished if it were shown
        val catalog = catalog(50, 51, 52, 60, 61, 81)
        assertEquals(emptyList(), catalog.pool(listOf(13)).data())
        assertEquals(setOf(100L, 101L, 102L, 110L), catalog.pool(listOf(10)).data().toSet())
    }

    @Test
    fun aFailedReadOfTheProgressIsAnError() = runBlocking {
        assertEquals(OperationResult.Error(LearningError.DATABASE_ERROR), catalog(progressFails = true).subjects())
        assertEquals(OperationResult.Error(LearningError.DATABASE_ERROR), catalog(progressFails = true).pool(listOf(11)))
    }
}

/** A stand-in that rejects every call; a test overrides the one call it needs. */
private inline fun <reified T : Any> unsupported(): T =
    java.lang.reflect.Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        throw UnsupportedOperationException(method.name)
    } as T
