package com.madovygabor.maturimate.features.profileDetails.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingStep
import com.madovygabor.maturimate.features.learning.domain.LearningError
import com.madovygabor.maturimate.features.learning.domain.ProgressRepository
import com.madovygabor.maturimate.features.userProfile.domain.DataError
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import com.madovygabor.maturimate.testutil.TEST_TIMEOUT
import com.madovygabor.maturimate.testutil.awaitFirst
import com.madovygabor.maturimate.testutil.awaitWithin
import java.lang.reflect.Proxy
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.error_io
import maturimate.shared.generated.resources.settings_reset_done

@OptIn(ExperimentalTestApi::class)
class SettingsProfileTest : SettingsTestBase() {

    @Test
    fun aNewRoleAsksOnlyForWhatIsMissing() = runBlocking {
        val repository = repository()
        repository.saveRole(UserRole.TEACHER)
        val viewModel = settings(repository)
        viewModel.loaded()

        assertEquals(listOf(OnboardingStep.DATE, OnboardingStep.GOAL), viewModel.missingAfterRoleChange(UserRole.STUDENT))
        assertEquals(listOf(OnboardingStep.GOAL), viewModel.missingAfterRoleChange(UserRole.LEARNER))
        assertEquals(emptyList(), viewModel.missingAfterRoleChange(UserRole.TEACHER))
    }

    @Test
    fun theSubjectsAreAskedAgainOnlyAfterTheLanguageIsSaved() = runBlocking {
        val real = student(repository())
        val snackbar = SnackbarController()
        val saved = viewModel(repository = real, snackbar = snackbar)
        saved.loaded()
        val changed = CompletableDeferred<Unit>()
        saved.setSchoolLanguage(TeachingLanguage.SK) { changed.complete(Unit) }
        changed.awaitWithin()
        assertEquals(TeachingLanguage.SK, real.profile.first()!!.teachingLanguage)

        // a save that fails: no callback, and the user is told
        val repo = student(repository())
        val failing = object : UserProfileRepository by repo {
            override suspend fun saveSchoolLanguage(language: TeachingLanguage): OperationResult<Unit, DataError> =
                OperationResult.Error(DataError.IO_ERROR)
        }
        val broken = viewModel(repository = failing, snackbar = snackbar)
        broken.loaded()
        var asked = false
        broken.setSchoolLanguage(TeachingLanguage.SK) { asked = true }
        assertEquals(said(Res.string.error_io), snackbar.next())
        assertFalse(asked)
    }

    @Test
    fun aFailedLanguageOrReminderSaveIsSaidInASnackbar() = runBlocking {
        val real = student(repository())
        real.saveReminder(false, LocalTime(18, 0))
        val failing = object : UserProfileRepository by real {
            override suspend fun saveUiLanguage(language: TeachingLanguage): OperationResult<Unit, DataError> = OperationResult.Error(DataError.IO_ERROR)
            override suspend fun saveReminder(enabled: Boolean, time: LocalTime?): OperationResult<Unit, DataError> = OperationResult.Error(DataError.STORAGE_FULL)
        }
        val snackbar = SnackbarController()
        val viewModel = viewModel(repository = failing, snackbar = snackbar)
        viewModel.loaded()

        viewModel.setAppLanguage(TeachingLanguage.SK)
        assertEquals(said(Res.string.error_io), snackbar.next())
        viewModel.state.awaitFirst { it?.profile?.reminderTime != null }
        assertTrue(viewModel.setReminder(true))
        assertEquals(said(Res.string.error_io), snackbar.next())
    }

    @Test
    fun aFailedResetKeepsTheDialogAndASuccessSaysSo() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val snackbar = SnackbarController()
        var result: OperationResult<Unit, LearningError> = OperationResult.Error(LearningError.NOT_FOUND)
        val progress = Proxy.newProxyInstance(ProgressRepository::class.java.classLoader, arrayOf(ProgressRepository::class.java)) { _, _, _ -> result } as ProgressRepository
        setContent { AppTheme { SettingsScreen(viewModel = viewModel(progress = progress, snackbar = snackbar)) } }

        waitForText("Haladás újrakezdése")
        onNodeWithText("Haladás újrakezdése").performClick()
        onNodeWithText("Újrakezdés").performClick()
        waitForText("Valami nem sikerült. Próbáld újra.")
        onNodeWithText("Újrakezded a haladást?").assertExists()
        assertTrue(runBlocking { snackbar.isQuiet() })

        // the same dialog, tried again: it closes and a snackbar says it
        result = OperationResult.Success(Unit)
        onNodeWithText("Újrakezdés").performClick()
        waitUntil(timeoutMillis = TEST_TIMEOUT.inWholeMilliseconds) { onAllNodesWithText("Újrakezded a haladást?").fetchSemanticsNodes().isEmpty() }
        assertEquals(said(Res.string.settings_reset_done), runBlocking { snackbar.next() })
    }

    @Test
    fun anOpenDialogLivesInTheViewModelAndSoSurvivesANewScreen() {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val viewModel = viewModel()
        runDesktopComposeUiTest(412, 2400) {
            setContent { AppTheme { SettingsScreen(viewModel = viewModel) } }
            waitForText("Név")
            viewModel.openDialog(SettingsDialog.NAME)
            waitForText("A neved")
        }
        // a new screen (a rotation builds one) shows the dialog that was open
        runDesktopComposeUiTest(412, 2400) {
            setContent { AppTheme { SettingsScreen(viewModel = viewModel) } }
            waitForText("A neved")
        }
    }
}
