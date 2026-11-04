package com.madovygabor.maturimate.features.profileDetails.presentation

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingStep
import com.madovygabor.maturimate.features.auth.domain.AccountUser
import com.madovygabor.maturimate.features.userProfile.data.UserProfileRepositoryImpl
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import com.madovygabor.maturimate.features.userProfile.domain.UserSubject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.settings_off
import org.jetbrains.compose.resources.getString
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SettingsTest {

    private val defaultLocale: Locale = Locale.getDefault()
    private val today = LocalDate(2026, 10, 31)

    @AfterTest
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private fun repository(): UserProfileRepositoryImpl {
        val database = UserDatabase(
            JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) }
        )
        return UserProfileRepositoryImpl(database, LocalUserProvider(database), Dispatchers.Unconfined)
    }

    private suspend fun student(repository: UserProfileRepositoryImpl) = repository.apply {
        saveTeachingLanguage(TeachingLanguage.HU)
        saveRole(UserRole.STUDENT)
        saveSubjects(listOf(UserSubject(1, true), UserSubject(5, true), UserSubject(6, true)))
        saveGraduationDate(LocalDate(2027, 3, 15))
        saveDailyGoal(10)
    }

    private suspend fun SettingsViewModel.loaded() = withTimeout(5_000) { state.first { it?.profile?.role != null } }!!

    @Test
    fun studentSeesEveryAnswerAndTheDaysLeft() = runBlocking {
        val state = SettingsViewModel(student(repository()), today).loaded()

        assertEquals(135, state.daysLeft)
        assertEquals(
            listOf(OnboardingStep.ROLE, OnboardingStep.SUBJECTS, OnboardingStep.DATE, OnboardingStep.GOAL),
            state.learningSteps,
        )
    }

    @Test
    fun teacherHasNoExamDateNorGoal() = runBlocking {
        val repository = repository()
        repository.saveRole(UserRole.TEACHER)
        val state = SettingsViewModel(repository, today).loaded()

        assertEquals(null, state.daysLeft)
        assertEquals(listOf(OnboardingStep.ROLE, OnboardingStep.SUBJECTS), state.learningSteps)
    }

    @Test
    fun aNewRoleAsksOnlyForWhatIsMissing() = runBlocking {
        val repository = repository()
        repository.saveRole(UserRole.TEACHER)
        val viewModel = SettingsViewModel(repository, today)
        viewModel.loaded()

        assertEquals(listOf(OnboardingStep.DATE, OnboardingStep.GOAL), viewModel.missingAfterRoleChange(UserRole.STUDENT))
        assertEquals(listOf(OnboardingStep.GOAL), viewModel.missingAfterRoleChange(UserRole.LEARNER))
        assertEquals(emptyList(), viewModel.missingAfterRoleChange(UserRole.TEACHER))
    }

    @Test
    fun theTwoLanguagesAreSetApart() = runBlocking {
        val repository = student(repository())
        val viewModel = SettingsViewModel(repository, today)
        viewModel.loaded()

        viewModel.setAppLanguage(TeachingLanguage.SK)
        withTimeout(5_000) { viewModel.state.first { it?.profile?.uiLanguage == TeachingLanguage.SK } }
        assertEquals(TeachingLanguage.HU, repository.profile.first()!!.teachingLanguage)

        var askedForSubjects = false
        viewModel.setSchoolLanguage(TeachingLanguage.SK) { askedForSubjects = true }
        withTimeout(5_000) { viewModel.state.first { it?.profile?.teachingLanguage == TeachingLanguage.SK } }
        assertTrue(askedForSubjects)
        // the same language again asks nothing
        askedForSubjects = false
        viewModel.setSchoolLanguage(TeachingLanguage.SK) { askedForSubjects = true }
        assertFalse(askedForSubjects)
    }

    @Test
    fun theReminderSwitchKeepsTheTimeAndAsksForOneWhenThereIsNone() = runBlocking {
        val repository = student(repository())
        val viewModel = SettingsViewModel(repository, today)
        viewModel.loaded()
        // no time saved yet: the screen opens the reminder question
        assertFalse(viewModel.setReminder(true))

        repository.saveReminder(false, LocalTime(18, 0))
        withTimeout(5_000) { viewModel.state.first { it?.profile?.reminderTime != null } }
        assertTrue(viewModel.setReminder(true))
        withTimeout(5_000) { viewModel.state.first { it?.profile?.reminderEnabled == true } }
        assertEquals(LocalTime(18, 0), repository.profile.first()!!.reminderTime)
    }

    @Test
    fun aGuestGetsTheAccountRowsAndLaterStepsAreComingSoon() = runDesktopComposeUiTest(412, 2000) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val state = runBlocking { SettingsViewModel(student(repository()), today).loaded() }
        var signUp = false
        setContent { AppTheme { SettingsContent(state, SettingsActions(onSignUp = { signUp = true })) } }

        onNodeWithText("Fiók létrehozása").performClick()
        assertTrue(signUp)
        onNodeWithText("Szinkronizálás").assertExists()
        onNodeWithText("Az iskolád tanítási nyelve").assertExists()
        onNodeWithText("Az app nyelve").assertExists()
        // sign-out and deletion only with an account
        onAllNodesWithText("Kijelentkezés").assertCountEquals(0)
    }

    @Test
    fun anAccountGetsTheRedRowsAndAGoogleAccountHasNoPassword() = runDesktopComposeUiTest(412, 2400) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val loaded = runBlocking { SettingsViewModel(student(repository()), today).loaded() }
        var account by mutableStateOf(AccountUser("id", "anna@example.com", "Anna", "Kovács"))
        var reset = false
        setContent {
            AppTheme { SettingsContent(loaded.copy(account = account), SettingsActions(onReset = { done -> reset = true; done(true) })) }
        }

        onNodeWithText("Jelszó megváltoztatása").assertExists()
        onNodeWithText("Kijelentkezés").assertExists()
        // the reset asks first, then runs
        onNodeWithText("Haladás újrakezdése").performClick()
        onNodeWithText("Újrakezded a haladást?").assertExists()
        onNodeWithText("Újrakezdés").performClick()
        assertTrue(reset)

        account = account.copy(hasPassword = false)
        onAllNodesWithText("Jelszó megváltoztatása").assertCountEquals(0)
    }

    @Test
    fun theNewPasswordIsTypedTwiceAndSavedOnlyWhenBothAgree() = runDesktopComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        var saved: Pair<String, String>? = null
        setContent { AppTheme { PasswordDialog(busy = false, error = null, onSave = { old, new -> saved = old to new }, onDismiss = {}) } }

        onNodeWithText("Régi jelszó").performTextInput("Old12345")
        onNodeWithText("Új jelszó").performTextInput("New12345")
        onNodeWithText("Új jelszó újra").performTextInput("New12399")
        onNodeWithText("A két új jelszó nem egyezik.").assertExists()
        onNodeWithText("Mentés").assertIsNotEnabled()

        onNodeWithText("A két új jelszó nem egyezik.").assertExists()
        onNodeWithText("New12399").performTextClearance()
        onNodeWithText("Új jelszó újra").performTextInput("New12345")
        onNodeWithText("Mentés").assertIsEnabled().performClick()
        assertEquals("Old12345" to "New12345", saved)
    }

    /** Pictures of the settings, both themes and languages, into build/screenshots/settings. */
    @Test
    fun rendersAtEverySizeAndTheme() {
        val state = runBlocking { SettingsViewModel(student(repository()), today).loaded() }
        val sizes = mapOf("phone" to (412 to 915), "desktop" to (1280 to 800))
        val dir = File("build/screenshots/settings").apply { mkdirs() }
        for (language in listOf("hu", "sk")) {
            Locale.setDefault(Locale.forLanguageTag(language))
            for ((name, size) in sizes) for (dark in listOf(false, true)) {
                runDesktopComposeUiTest(size.first, size.second) {
                    setContent { AppTheme(darkTheme = dark) { SettingsContent(state, SettingsActions()) } }
                    onNodeWithText(runBlocking { getString(Res.string.settings_off) }).assertExists()
                    ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(dir, "settings-$language-$name-${if (dark) "dark" else "light"}.png"))
                }
            }
        }
    }
}
