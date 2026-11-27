package com.madovygabor.maturimate.features.profileDetails.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import com.madovygabor.maturimate.features.userProfile.domain.UserSubject
import com.madovygabor.maturimate.testutil.TEST_TIMEOUT
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.settings_app_language_saved
import maturimate.shared.generated.resources.settings_date_saved
import maturimate.shared.generated.resources.settings_goal_saved
import maturimate.shared.generated.resources.settings_name_done
import maturimate.shared.generated.resources.settings_subjects_saved

@OptIn(ExperimentalTestApi::class)
class SettingsEditPagesTest : SettingsTestBase() {

    @Test
    fun roleEditPageEnablesSaveOnlyOnChangeAndShowsSnackbar() = runDesktopComposeUiTest(412, 1600) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val repo = runBlocking { student(repository()) }
        val snackbar = SnackbarController()
        val vm = viewModel(repository = repo, snackbar = snackbar)
        setContent { AppTheme { SettingsScreen(viewModel = vm) } }

        waitForText("Szerepkör")
        onNodeWithText("Szerepkör").performClick()
        waitForText("Szerepkör módosítása")
        onAllNodesWithText("Mégse").assertCountEquals(0)
        onNodeWithText("Mentés").assertIsNotEnabled()

        onNodeWithText("Csak tanulni szeretnék").performClick()
        onNodeWithText("Mentés").assertIsEnabled().performClick()

        waitForText("Tantárgyak módosítása")
        assertEquals(UserRole.LEARNER, runBlocking { repo.profile.first()!!.role })
    }

    @Test
    fun subjectsEditPageEnablesSaveOnlyWhenSelectionChanges() = runDesktopComposeUiTest(412, 1600) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val repo = runBlocking { student(repository()) }
        val snackbar = SnackbarController()
        val vm = viewModel(repository = repo, snackbar = snackbar)
        setContent { AppTheme { SettingsScreen(viewModel = vm) } }

        waitForText("Tantárgyak")
        onNodeWithText("Tantárgyak").performClick()
        waitForText("Tantárgyak módosítása")
        onNodeWithText("Mentés").assertIsNotEnabled()

        onNodeWithText("Matematika").performClick()
        onNodeWithText("Mentés").assertIsEnabled().performClick()

        waitUntil(timeoutMillis = TEST_TIMEOUT.inWholeMilliseconds) {
            runBlocking { repo.profile.first()!!.subjects.any { it.subjectId == 4L } }
        }
        assertEquals(said(Res.string.settings_subjects_saved), runBlocking { snackbar.next() })
    }

    @Test
    fun goalEditPageSavesNewGoalAndShowsSnackbar() = runDesktopComposeUiTest(412, 1600) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val repo = runBlocking { student(repository()) }
        val snackbar = SnackbarController()
        val vm = viewModel(repository = repo, snackbar = snackbar)
        setContent { AppTheme { SettingsScreen(viewModel = vm) } }

        waitForText("Napi cél")
        onNodeWithText("Napi cél").performClick()
        waitForText("Napi cél módosítása")
        onAllNodesWithText("Mégse").assertCountEquals(0)
        onNodeWithText("Mentés").assertIsNotEnabled()

        onNodeWithText("15 perc").performClick()
        onNodeWithText("Mentés").assertIsEnabled().performClick()

        waitUntil(timeoutMillis = TEST_TIMEOUT.inWholeMilliseconds) {
            runBlocking { repo.profile.first()!!.dailyGoalMinutes == 15 }
        }
        assertEquals(said(Res.string.settings_goal_saved), runBlocking { snackbar.next() })
    }

    @Test
    fun nameDialogSavesChangedNameAndShowsSnackbar() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val account = FakeAccount()
        val snackbar = SnackbarController()
        val repo = runBlocking {
            student(repository()).apply { saveNames("Anna", "Kovács") }
        }
        val vm = viewModel(account = account, repository = repo, snackbar = snackbar)
        setContent { AppTheme { SettingsScreen(viewModel = vm) } }

        waitForText("Név")
        onNodeWithText("Név").performClick()
        waitForText("Név módosítása")
        onNodeWithText("Mentés").assertIsNotEnabled()

        onNodeWithText("Anna").performTextClearance()
        onNodeWithText("Keresztnév").performTextInput("Eszter")
        onNodeWithText("Mentés").assertIsEnabled().performClick()

        assertEquals(said(Res.string.settings_name_done), runBlocking { snackbar.next() })
        waitUntil(timeoutMillis = TEST_TIMEOUT.inWholeMilliseconds) {
            onAllNodesWithText("Név módosítása").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun dateAndGoalViewModelSavesEmitSuccessSnackbars() = runBlocking {
        val repo = student(repository())
        val snackbar = SnackbarController()
        val vm = viewModel(repository = repo, snackbar = snackbar)
        vm.loaded()

        var savedDate = false
        vm.saveGraduationDate(LocalDate(2028, 5, 4)) { savedDate = true }
        assertEquals(said(Res.string.settings_date_saved), snackbar.next())
        assertEquals(true, savedDate)
        assertEquals(LocalDate(2028, 5, 4), repo.profile.first()!!.graduationDate)

        val (items, level) = buildSubjectItems(runBlocking { repo.profile.first()!! })
        assertEquals(listOf(1L, 5L, 6L), toUserSubjects(items, level).map { it.subjectId })
    }

    @Test
    fun roleAndSchoolLanguageSavesEmitTheirOwnSnackbarsAndLanguageIsGuarded() = runBlocking {
        val repo = student(repository())
        val snackbar = SnackbarController()
        val vm = viewModel(repository = repo, snackbar = snackbar)
        vm.loaded()

        vm.saveRole(UserRole.TEACHER) {}
        kotlin.test.assertTrue(snackbar.isQuiet())
        assertEquals(UserRole.TEACHER, repo.profile.first()!!.role)

        vm.setSchoolLanguage(TeachingLanguage.SK) {}
        kotlin.test.assertTrue(snackbar.isQuiet())
        assertEquals(TeachingLanguage.SK, repo.profile.first()!!.teachingLanguage)

        vm.setAppLanguage(TeachingLanguage.SK)
        assertEquals(said(Res.string.settings_app_language_saved), snackbar.next())
        assertEquals(TeachingLanguage.SK, repo.profile.first()!!.uiLanguage)
    }

    @Test
    fun subjectsPagePreservesUnsavedSelectionAcrossUnrelatedProfileUpdates() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val repo = runBlocking { student(repository()) }
        var saved = false
        val profileState = androidx.compose.runtime.mutableStateOf(runBlocking { repo.profile.first()!! })
        setContent {
            AppTheme {
                SettingsSubjectsPage(
                    profile = profileState.value,
                    afterChange = false,
                    busy = false,
                    onBack = {},
                    onSave = { saved = true },
                )
            }
        }

        waitForText("Matematika")
        onNodeWithText("Mentés").assertIsNotEnabled()
        onNodeWithText("Matematika").performClick()
        onNodeWithText("Mentés").assertIsEnabled()

        // Unrelated profile update (e.g. daily goal change) must not wipe the user's unsaved selection.
        profileState.value = profileState.value.copy(dailyGoalMinutes = 20)
        onNodeWithText("Mentés").assertIsEnabled().performClick()
        assertEquals(true, saved)
    }
}
