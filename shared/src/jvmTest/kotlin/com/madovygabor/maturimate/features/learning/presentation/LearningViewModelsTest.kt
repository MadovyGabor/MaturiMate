package com.madovygabor.maturimate.features.learning.presentation

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.database.content.ContentDatabase
import com.madovygabor.maturimate.database.content.Learning_unit
import com.madovygabor.maturimate.database.content.Page_block
import com.madovygabor.maturimate.database.content.Subject
import com.madovygabor.maturimate.database.content.Topic
import com.madovygabor.maturimate.database.content.Unit_page
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.learning.data.ContentRepositoryImpl
import com.madovygabor.maturimate.features.learning.domain.LearningCourse
import com.madovygabor.maturimate.features.learning.domain.LearningError
import com.madovygabor.maturimate.features.learning.domain.ProgressRepository
import com.madovygabor.maturimate.features.learning.domain.model.StepCount
import com.madovygabor.maturimate.features.learning.domain.model.TopicLevel
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgress
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgressStatus
import com.madovygabor.maturimate.features.userProfile.data.UserProfileRepositoryImpl
import com.madovygabor.maturimate.features.userProfile.domain.CatalogSubject
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import com.madovygabor.maturimate.features.userProfile.domain.UserSubject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** The view models of the subjects tab and of the topic levels, over an in-memory content and user database. */
@OptIn(ExperimentalTime::class)
class LearningViewModelsTest {

    // subject 1: the part "Nyelvtan" (10) with the leaf topic "Szóképek" (11): a lesson and a mini test
    private val database = ContentDatabase(
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { ContentDatabase.Schema.create(it) }
    ).apply {
        subjectQueries.insert(Subject(1, "HU_MJL", "Magyar", "Maďarčina", "HU", null))
        topicQueries.insert(Topic(10, 1, null, "Nyelvtan", null, 1, true))
        topicQueries.insert(Topic(11, null, 10, "Szóképek", null, 1, true))
        learningUnitQueries.insert(Learning_unit(50, 11, "SUMMARY", "Lecke", null, 3, 5, 10, 1, true, 0))
        learningUnitQueries.insertPage(Unit_page(50, 1, null))
        learningUnitQueries.insertBlock(Page_block(50, 1, 1, "PARAGRAPH", "Szöveg"))
        learningUnitQueries.insert(Learning_unit(51, 11, "PRACTICE", "Mini", "MINI", 2, 3, 10, 2, true, 0))
    }

    private val content = ContentRepositoryImpl({ database }, Dispatchers.Unconfined)

    private fun course(completed: Set<Long> = emptySet(), fails: Boolean = false) = LearningCourse(
        content,
        object : ProgressRepository by unsupported<ProgressRepository>() {
            override suspend fun unitProgress(): OperationResult<List<UnitProgress>, LearningError> =
                if (fails) OperationResult.Error(LearningError.DATABASE_ERROR)
                else OperationResult.Success(completed.map {
                    UnitProgress(it, UnitProgressStatus.COMPLETED, Instant.fromEpochMilliseconds(0), Instant.fromEpochMilliseconds(1))
                })
        },
    )

    private suspend fun profileOf(vararg subjects: CatalogSubject): UserProfileRepository {
        val userDatabase = UserDatabase(
            JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) }
        )
        return UserProfileRepositoryImpl(userDatabase, LocalUserProvider(userDatabase), Dispatchers.Unconfined).apply {
            saveTeachingLanguage(TeachingLanguage.HU)
            saveRole(UserRole.STUDENT)
            saveSubjects(subjects.map { UserSubject(it.id, compulsory = true) })
        }
    }

    private suspend fun SubjectListViewModel.loaded() = withTimeout(5_000) {
        refresh()
        state.first { it !is SubjectListUiState.Loading }
    }

    private suspend fun TopicViewModel.loaded() = withTimeout(5_000) {
        refresh()
        state.first { it !is TopicUiState.Loading }
    }

    @Test
    fun subjectsShowTheUsersSubjectsWithTheirProgress() = runBlocking<Unit> {
        val repository = profileOf(CatalogSubject.ENGLISH_B2, CatalogSubject.HUNGARIAN)
        val state = SubjectListViewModel(repository, course(completed = setOf(50))).loaded()

        assertIs<SubjectListUiState.Ready>(state)
        // in the order of the catalog, whatever order they were saved in
        assertEquals(listOf(CatalogSubject.HUNGARIAN, CatalogSubject.ENGLISH_B2), state.cards.map { it.subject })
        assertEquals(StepCount(1, 2), state.cards[0].progress!!.steps)
        // no content for English yet: "Hamarosan"
        assertEquals(null, state.cards[1].progress)
    }

    @Test
    fun subjectsFailWhenTheProgressCannotBeRead() = runBlocking<Unit> {
        val state = SubjectListViewModel(profileOf(CatalogSubject.HUNGARIAN), course(fails = true)).loaded()
        assertIs<SubjectListUiState.Failed>(state)
    }

    @Test
    fun aLaterFailedReadKeepsWhatIsShown() = runBlocking<Unit> {
        var fails = false
        val repository = profileOf(CatalogSubject.HUNGARIAN)
        val viewModel = SubjectListViewModel(
            repository,
            LearningCourse(content, object : ProgressRepository by unsupported<ProgressRepository>() {
                override suspend fun unitProgress(): OperationResult<List<UnitProgress>, LearningError> =
                    if (fails) OperationResult.Error(LearningError.DATABASE_ERROR) else OperationResult.Success(emptyList())
            }),
        )
        assertIs<SubjectListUiState.Ready>(viewModel.loaded())

        fails = true
        viewModel.refresh()
        assertIs<SubjectListUiState.Ready>(viewModel.state.value)
    }

    @Test
    fun partsOfASubjectAreListed() = runBlocking<Unit> {
        val state = TopicViewModel(course(), TopicTarget.SubjectParts(1), SnackbarController()).loaded()

        assertIs<TopicUiState.Ready>(state)
        val level = assertIs<TopicLevel.Branch>(state.level)
        assertEquals(listOf(10L), level.topics.map { it.topic.id })
    }

    @Test
    fun aLeafTopicShowsItsPath() = runBlocking<Unit> {
        val state = TopicViewModel(course(completed = setOf(50)), TopicTarget.Topic(11), SnackbarController()).loaded()

        assertIs<TopicUiState.Ready>(state)
        val path = assertIs<TopicLevel.Path>(state.level)
        assertEquals("Szóképek", path.title)
        assertEquals(2, path.steps.size)
    }

    @Test
    fun aLockedStepSaysItIsLocked() = runBlocking<Unit> {
        val snackbar = SnackbarController()
        val viewModel = TopicViewModel(course(), TopicTarget.Topic(11), snackbar)

        viewModel.onLockedStepClicked()

        assertIs<com.madovygabor.maturimate.core.presentation.utils.UiText.StringRes>(withTimeout(5_000) { snackbar.events.first() }.message)
    }

    @Test
    fun anUnknownTopicFails() = runBlocking<Unit> {
        assertIs<TopicUiState.Failed>(TopicViewModel(course(), TopicTarget.Topic(999), SnackbarController()).loaded())
    }
}

/** A stand-in that rejects every call; a test overrides the one call it needs. */
private inline fun <reified T : Any> unsupported(): T =
    java.lang.reflect.Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        throw UnsupportedOperationException(method.name)
    } as T
