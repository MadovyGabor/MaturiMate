package com.madovygabor.maturimate.features.learning.domain

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.database.content.ContentDatabase
import com.madovygabor.maturimate.database.content.Learning_unit
import com.madovygabor.maturimate.database.content.Page_block
import com.madovygabor.maturimate.database.content.Subject
import com.madovygabor.maturimate.database.content.Topic
import com.madovygabor.maturimate.database.content.Unit_page
import com.madovygabor.maturimate.features.learning.data.ContentRepositoryImpl
import com.madovygabor.maturimate.features.learning.domain.model.StepCount
import com.madovygabor.maturimate.features.learning.domain.model.StepKind
import com.madovygabor.maturimate.features.learning.domain.model.StepState
import com.madovygabor.maturimate.features.learning.domain.model.TopicLevel
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgress
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgressStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.fail
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The sample tree: subject 1 with the part "Nyelvtan" (10), whose topics are "Szóképek" (11: lesson, mini
 * test, topic test) and "Alakzatok" (12: a lesson), a hidden topic (13) that must be ignored, and a final
 * test on the part itself.
 */
@OptIn(ExperimentalTime::class)
class LearningCourseTest {

    private val database = ContentDatabase(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { ContentDatabase.Schema.create(it) }
    ).apply {
        subjectQueries.insert(Subject(1, "HU_MJL", "Magyar", "Maďarčina", "HU", null))
        topicQueries.insert(Topic(10, 1, null, "Nyelvtan", null, 1, true))
        topicQueries.insert(Topic(11, null, 10, "Szóképek", null, 1, true))
        topicQueries.insert(Topic(12, null, 10, "Alakzatok", null, 2, true))
        topicQueries.insert(Topic(13, null, 10, "Rejtett", null, 3, false))
        lesson(50, 11, 1)
        practice(51, 11, 2, "MINI")
        practice(52, 11, 3, "TOPIC")
        lesson(60, 12, 1)
        practice(70, 10, 1, "FINAL")
        lesson(80, 13, 1)
    }

    private fun ContentDatabase.lesson(id: Long, topic: Long, order: Long) {
        learningUnitQueries.insert(Learning_unit(id, topic, "SUMMARY", "Lecke $id", null, 3, 5, 10, order, true, 0))
        learningUnitQueries.insertPage(Unit_page(id, 1, null))
        learningUnitQueries.insertBlock(Page_block(id, 1, 1, "PARAGRAPH", "Szöveg"))
    }

    private fun ContentDatabase.practice(id: Long, topic: Long, order: Long, kind: String) {
        learningUnitQueries.insert(Learning_unit(id, topic, "PRACTICE", "Teszt $id", kind, 4, 3, 10, order, true, 0))
    }

    private val content = ContentRepositoryImpl({ database }, Dispatchers.Unconfined)

    private fun course(vararg completed: Long, progressFails: Boolean = false, guest: Boolean = false) = LearningCourse(
        content,
        object : ProgressRepository by unsupported<ProgressRepository>() {
            override suspend fun unitProgress(): OperationResult<List<UnitProgress>, LearningError> =
                if (progressFails) OperationResult.Error(LearningError.DATABASE_ERROR)
                else OperationResult.Success(completed.map {
                    UnitProgress(it, UnitProgressStatus.COMPLETED, Instant.fromEpochMilliseconds(0), Instant.fromEpochMilliseconds(1))
                })
        },
        isGuest = { guest }
    )

    private fun <T> OperationResult<T, LearningError>.data(): T = when (this) {
        is OperationResult.Success -> data
        is OperationResult.Error -> fail("expected success, got $error")
    }

    @Test
    fun nextStepIsTheFirstOpenStepWithTheWayToIt() = runBlocking {
        val target = course(50).nextStep().data()!!
        assertEquals(51L, target.unit.id)
        assertEquals(StepKind.MINI_TEST, target.kind)
        assertEquals(1L, target.subjectId)
        // the part, then the topic that holds the step
        assertEquals(listOf(10L, 11L), target.topicIds)
        assertEquals("Szóképek", target.topicTitle)
    }

    @Test
    fun nextStepMovesOnToTheNextTopicAndThenToTheFinalTestOfThePart() = runBlocking {
        assertEquals(60L, course(50, 51, 52).nextStep().data()!!.unit.id)
        val final = course(50, 51, 52, 60).nextStep().data()!!
        assertEquals(70L, final.unit.id)
        assertEquals(StepKind.FINAL_TEST, final.kind)
        assertEquals(listOf(10L), final.topicIds)
    }

    @Test
    fun nextStepContinuesWhereTheStudentWorkedLast() = runBlocking {
        // the student did the lesson of the second topic first: the next step follows it, not the first topic
        assertEquals(70L, course(60).nextStep(afterUnitId = 60).data()!!.unit.id)
        // a unit that is not done (a test that was not passed) comes up again
        assertEquals(51L, course(50).nextStep(afterUnitId = 51).data()!!.unit.id)
        // at the end the search goes on from the beginning
        assertEquals(51L, course(50, 60, 70).nextStep(afterUnitId = 60).data()!!.unit.id)
        // an unknown unit means the first open step
        assertEquals(50L, course().nextStep(afterUnitId = 999).data()!!.unit.id)
    }

    @Test
    fun nextStepIsEmptyWhenEverythingIsDone() = runBlocking {
        assertNull(course(50, 51, 52, 60, 70).nextStep().data())
    }

    @Test
    fun subjectProgressCountsPublishedUnitsBelowTheParts() = runBlocking {
        val subject = course(50, 51).subjects().data().single()
        assertEquals("HU_MJL", subject.code)
        assertEquals(1, subject.parts)
        // 50, 51, 52, 60 and the final test 70; the hidden topic's unit 80 is not counted
        assertEquals(StepCount(done = 2, total = 5), subject.steps)
    }

    @Test
    fun partsListTheRootTopicsWithTheirProgress() = runBlocking {
        val parts = course(50).parts(1).data()
        assertEquals(null, parts.title)
        assertEquals(null, parts.finalTest)
        val part = parts.topics.single()
        assertEquals(10L, part.topic.id)
        assertEquals(StepCount(1, 5), part.steps)
        assertEquals(2, part.lessons)
        // two published topics below it (the hidden one is not counted); 5 steps, 2 of them lessons, so 3 tests
        assertEquals(2, part.childTopics)
        assertEquals(3, part.tests)
    }

    @Test
    fun leafTopicIsAPathWithTheFirstOpenStepAsCurrent() = runBlocking {
        val path = course(50, 51).topic(11).data()
        assertIs<TopicLevel.Path>(path)
        assertEquals("Szóképek", path.title)
        assertEquals("Nyelvtan", path.parentTitle)
        assertEquals(listOf(StepKind.LESSON, StepKind.MINI_TEST, StepKind.TOPIC_TEST), path.steps.map { it.kind })
        assertEquals(listOf(StepState.DONE, StepState.DONE, StepState.CURRENT), path.steps.map { it.state })
    }

    @Test
    fun aPathWithNothingDoneStartsAtTheFirstStep() = runBlocking {
        val path = course().topic(11).data()
        assertIs<TopicLevel.Path>(path)
        assertEquals(listOf(StepState.CURRENT, StepState.TODO, StepState.TODO), path.steps.map { it.state })
    }

    @Test
    fun topicWithTopicsBelowIsABranchWithItsFinalTest() = runBlocking {
        val branch = course(50, 51).topic(10).data()
        assertIs<TopicLevel.Branch>(branch)
        assertEquals("Nyelvtan", branch.title)
        assertEquals(null, branch.parentTitle)
        assertEquals(listOf(11L, 12L), branch.topics.map { it.topic.id })
        assertEquals(StepCount(2, 3), branch.topics[0].steps)
        // a topic with lessons and tests holds no topics: 1 lesson and 2 tests
        assertEquals(0, branch.topics[0].childTopics)
        assertEquals(1, branch.topics[0].lessons)
        assertEquals(2, branch.topics[0].tests)
        assertEquals(StepCount(0, 1), branch.topics[1].steps)
        val final = branch.finalTest!!
        assertEquals(StepKind.FINAL_TEST, final.kind)
        assertEquals(StepState.TODO, final.state)
    }

    @Test
    fun finalTestIsCurrentWhenEverythingBelowIsDone() = runBlocking {
        val branch = course(50, 51, 52, 60).topic(10).data()
        assertEquals(StepState.CURRENT, (branch as TopicLevel.Branch).finalTest!!.state)
    }

    @Test
    fun finalTestIsDoneWhenItIsCompleted() = runBlocking {
        val branch = course(50, 51, 52, 60, 70).topic(10).data()
        assertEquals(StepState.DONE, (branch as TopicLevel.Branch).finalTest!!.state)
    }

    @Test
    fun unknownTopicIsNotFound() = runBlocking {
        assertEquals(OperationResult.Error(LearningError.NOT_FOUND), course().topic(999))
    }

    @Test
    fun aFailedProgressReadIsAnError() = runBlocking {
        assertEquals(OperationResult.Error(LearningError.DATABASE_ERROR), course(progressFails = true).subjects())
        assertEquals(OperationResult.Error(LearningError.DATABASE_ERROR), course(progressFails = true).topic(11))
    }

    @Test
    fun aGuestWhoHasDoneALessonSeesTheSignUpAsTheNextStep() = runBlocking {
        // an account goes on as before
        val account = course(50).topic(11).data() as TopicLevel.Path
        assertEquals(StepState.CURRENT, account.steps.first { it.state != StepState.DONE }.state)
        // a guest with no lesson done can open the first one
        val fresh = course(guest = true).topic(11).data() as TopicLevel.Path
        assertEquals(StepState.CURRENT, fresh.steps.first().state)
        // a guest after the first lesson: the next step is the sign-up
        val after = course(50, guest = true).topic(11).data() as TopicLevel.Path
        assertEquals(StepState.DONE, after.steps.first().state)
        assertEquals(StepState.SIGN_UP, after.steps[1].state)
        assertTrue(after.steps.none { it.state == StepState.CURRENT })
    }
}

/** A stand-in that rejects every call; a test overrides the one call it needs. */
private inline fun <reified T : Any> unsupported(): T =
    java.lang.reflect.Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        throw UnsupportedOperationException(method.name)

    } as T
