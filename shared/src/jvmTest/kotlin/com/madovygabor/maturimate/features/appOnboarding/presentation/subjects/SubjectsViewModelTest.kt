package com.madovygabor.maturimate.features.appOnboarding.presentation.subjects

import com.madovygabor.maturimate.testutil.awaitFirst
import com.madovygabor.maturimate.testutil.awaitWithin
import com.madovygabor.maturimate.testutil.testDbDispatcher
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.userProfile.data.UserProfileRepositoryImpl
import com.madovygabor.maturimate.features.userProfile.domain.CatalogSubject
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import com.madovygabor.maturimate.features.userProfile.domain.UserSubject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubjectsViewModelTest {

    // the app's setup: one thread runs the database calls (AppModul); with Unconfined a ViewModel on the main thread and the
    // test could use the one connection at the same time
    private val dbDispatcher = testDbDispatcher()

    private fun repository(): UserProfileRepositoryImpl {
        val database = UserDatabase(
            JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) }
        )
        return UserProfileRepositoryImpl(database, LocalUserProvider(database), dbDispatcher)
    }

    private suspend fun SubjectsViewModel.loaded() = state.awaitFirst { it.items.isNotEmpty() }

    @Test
    fun studentStartsWithTheCompulsorySubjectsLocked() = runBlocking {
        val repository = repository()
        repository.saveTeachingLanguage(TeachingLanguage.HU)
        repository.saveRole(UserRole.STUDENT)

        val state = SubjectsViewModel(repository, SnackbarController()).loaded()

        assertEquals(listOf(CatalogSubject.HUNGARIAN, CatalogSubject.SLOVAK_HU, CatalogSubject.ENGLISH_B1, CatalogSubject.MATHEMATICS), state.items.map { it.subject })
        assertEquals(listOf(false, false, true, false), state.items.map { it.asksLevel })
        assertEquals(listOf(true, true, true, false), state.items.map { it.selected })
        assertEquals(listOf(true, true, true, false), state.items.map { it.locked })
        // English is chosen, so the level is needed first
        assertFalse(state.canContinue)
    }

    @Test
    fun slovakSchoolHasNoHungarianSubject() = runBlocking {
        val repository = repository()
        repository.saveTeachingLanguage(TeachingLanguage.SK)
        repository.saveRole(UserRole.STUDENT)

        val state = SubjectsViewModel(repository, SnackbarController()).loaded()

        assertEquals(listOf(CatalogSubject.SLOVAK, CatalogSubject.ENGLISH_B1, CatalogSubject.MATHEMATICS), state.items.map { it.subject })
    }

    @Test
    fun teacherGetsBothSchoolsAndBothEnglishLevels() = runBlocking {
        val repository = repository()
        repository.saveTeachingLanguage(TeachingLanguage.HU)
        repository.saveRole(UserRole.TEACHER)
        val viewModel = SubjectsViewModel(repository, SnackbarController())

        val loaded = viewModel.loaded()
        assertTrue(loaded.items.none { it.selected || it.locked || it.asksLevel })
        // the own school's subjects first, then the Slovak school's
        assertEquals(
            listOf(
                CatalogSubject.HUNGARIAN, CatalogSubject.SLOVAK_HU, CatalogSubject.ENGLISH_B1,
                CatalogSubject.ENGLISH_B2, CatalogSubject.MATHEMATICS, CatalogSubject.SLOVAK,
            ),
            loaded.items.map { it.subject },
        )

        viewModel.onToggled(CatalogSubject.ENGLISH_B1)
        viewModel.onToggled(CatalogSubject.ENGLISH_B2)
        viewModel.onToggled(CatalogSubject.SLOVAK)
        assertTrue(viewModel.state.value.canContinue)
        val done = CompletableDeferred<UserRole?>()
        viewModel.onNext { done.complete(it) }

        assertEquals(UserRole.TEACHER, done.awaitWithin())
        assertEquals(listOf(3L, 6L, 2L), repository.profile.first()!!.subjects.map { it.subjectId })
    }

    @Test
    fun learnerLevelPicksTheEnglishSubjectAndUnknownIsB1() = runBlocking {
        val repository = repository()
        repository.saveTeachingLanguage(TeachingLanguage.HU)
        repository.saveRole(UserRole.LEARNER)
        val viewModel = SubjectsViewModel(repository, SnackbarController())
        viewModel.loaded()

        viewModel.onToggled(CatalogSubject.ENGLISH_B1)
        viewModel.onToggled(CatalogSubject.MATHEMATICS)
        viewModel.onLevelSelected(LevelChoice.UNKNOWN)
        val done = CompletableDeferred<UserRole?>()
        viewModel.onNext { done.complete(it) }

        assertEquals(UserRole.LEARNER, done.awaitWithin())
        assertEquals(listOf(UserSubject(3, compulsory = false), UserSubject(4, compulsory = false)), repository.profile.first()!!.subjects)
    }

    @Test
    fun lockedSubjectCannotBeRemoved() = runBlocking {
        val repository = repository()
        repository.saveTeachingLanguage(TeachingLanguage.SK)
        repository.saveRole(UserRole.STUDENT)
        val viewModel = SubjectsViewModel(repository, SnackbarController())
        viewModel.loaded()

        viewModel.onToggled(CatalogSubject.SLOVAK)

        assertTrue(viewModel.state.value.items.first { it.subject == CatalogSubject.SLOVAK }.selected)
    }

    @Test
    fun savedSubjectsAndLevelAreShownAgain() = runBlocking {
        val repository = repository()
        repository.saveTeachingLanguage(TeachingLanguage.HU)
        repository.saveRole(UserRole.LEARNER)
        repository.saveSubjects(listOf(UserSubject(6, compulsory = false)))

        val state = SubjectsViewModel(repository, SnackbarController()).loaded()

        assertEquals(listOf(CatalogSubject.ENGLISH_B1), state.items.filter { it.selected }.map { it.subject })
        assertEquals(LevelChoice.B2, state.level)
        assertTrue(state.canContinue)
    }
}
