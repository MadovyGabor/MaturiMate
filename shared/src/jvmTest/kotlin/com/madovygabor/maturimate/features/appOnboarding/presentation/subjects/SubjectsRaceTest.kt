package com.madovygabor.maturimate.features.appOnboarding.presentation.subjects

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.features.profileDetails.presentation.SettingsTestBase
import com.madovygabor.maturimate.features.userProfile.domain.CatalogSubject
import com.madovygabor.maturimate.features.userProfile.domain.DataError
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import com.madovygabor.maturimate.features.userProfile.domain.UserSubject
import com.madovygabor.maturimate.testutil.awaitFirst
import com.madovygabor.maturimate.testutil.awaitWithin
import com.madovygabor.maturimate.testutil.flushMain
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The subjects question against a slow repository: what the user answers or saves while the profile is still read. */
class SubjectsRaceTest : SettingsTestBase() {

    @Test
    fun anEnglishLevelChosenWhileTheProfileLoadsIsNotOverwrittenByTheSavedOne() = runBlocking {
        val real = repository()
        real.saveTeachingLanguage(TeachingLanguage.HU)
        real.saveRole(UserRole.LEARNER)
        // the saved English subject is B2
        real.saveSubjects(listOf(UserSubject(6, compulsory = false)))
        val release = CompletableDeferred<Unit>()
        val slow = object : UserProfileRepository by real {
            override val profile: Flow<UserProfile?> = flow {
                release.await()
                emitAll(real.profile)
            }
        }
        val viewModel = SubjectsViewModel(slow, SnackbarController())

        // the list is still empty, the user already answers the level
        viewModel.onLevelSelected(LevelChoice.B1)
        release.complete(Unit)
        val loaded = viewModel.state.awaitFirst { it.items.isNotEmpty() }

        assertEquals(LevelChoice.B1, loaded.level)
    }

    @Test
    fun theSubjectsAreReadOnlyAfterTheNewSchoolLanguageIsSaved() = runBlocking {
        val real = student(repository())
        val releaseSave = CompletableDeferred<Unit>()
        val slow = object : UserProfileRepository by real {
            override suspend fun saveSchoolLanguage(language: TeachingLanguage): OperationResult<Unit, DataError> {
                releaseSave.await()
                return real.saveSchoolLanguage(language)
            }
        }
        val settings = settings(slow)
        settings.loaded()
        val subjects = CompletableDeferred<SubjectsViewModel>()

        // the screen opens the subjects question from this callback
        settings.setSchoolLanguage(TeachingLanguage.SK) { subjects.complete(SubjectsViewModel(slow, SnackbarController())) }
        flushMain()
        assertFalse(subjects.isCompleted, "the subjects were asked for before the language was saved")
        releaseSave.complete(Unit)

        val state = subjects.awaitWithin().state.awaitFirst { it.items.isNotEmpty() }
        assertEquals(listOf(CatalogSubject.SLOVAK, CatalogSubject.ENGLISH_B1, CatalogSubject.MATHEMATICS), state.items.map { it.subject })
        assertTrue(state.items.none { it.subject == CatalogSubject.HUNGARIAN })
    }
}
