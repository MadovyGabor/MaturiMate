package com.madovygabor.maturimate.features.profileDetails.presentation

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingStep
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
import maturimate.shared.generated.resources.profile_reminder_off
import org.jetbrains.compose.resources.getString
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ProfileTest {

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

    private suspend fun ProfileViewModel.loaded() = withTimeout(5_000) { state.first { it?.profile?.role != null } }!!

    @Test
    fun studentSeesEveryAnswerAndTheDaysLeft() = runBlocking {
        val state = ProfileViewModel(student(repository()), today).loaded()

        assertEquals(135, state.daysLeft)
        assertEquals(
            listOf(OnboardingStep.LANGUAGE, OnboardingStep.ROLE, OnboardingStep.SUBJECTS, OnboardingStep.DATE, OnboardingStep.GOAL, OnboardingStep.REMINDER),
            state.steps,
        )
    }

    @Test
    fun teacherHasNoExamDateNorGoal() = runBlocking {
        val repository = repository()
        repository.saveRole(UserRole.TEACHER)
        val state = ProfileViewModel(repository, today).loaded()

        assertEquals(null, state.daysLeft)
        assertEquals(listOf(OnboardingStep.LANGUAGE, OnboardingStep.ROLE, OnboardingStep.SUBJECTS, OnboardingStep.REMINDER), state.steps)
    }

    @Test
    fun aNewRoleAsksOnlyForWhatIsMissing() = runBlocking {
        val repository = repository()
        repository.saveRole(UserRole.TEACHER)
        val viewModel = ProfileViewModel(repository, today)
        viewModel.loaded()

        assertEquals(listOf(OnboardingStep.DATE, OnboardingStep.GOAL), viewModel.missingAfterRoleChange(UserRole.STUDENT))
        assertEquals(listOf(OnboardingStep.GOAL), viewModel.missingAfterRoleChange(UserRole.LEARNER))
        assertEquals(emptyList(), viewModel.missingAfterRoleChange(UserRole.TEACHER))
    }

    /** Pictures of the profile, both themes and languages, into build/screenshots/profile. */
    @Test
    fun rendersAtEverySizeAndTheme() {
        val state = runBlocking { ProfileViewModel(student(repository()), today).loaded() }
        val sizes = mapOf("phone" to (412 to 915), "desktop" to (1280 to 800))
        val dir = File("build/screenshots/profile").apply { mkdirs() }
        for (language in listOf("hu", "sk")) {
            Locale.setDefault(Locale.forLanguageTag(language))
            for ((name, size) in sizes) for (dark in listOf(false, true)) {
                runDesktopComposeUiTest(size.first, size.second) {
                    setContent { AppTheme(darkTheme = dark) { ProfileContent(state, onEdit = {}) } }
                    onNodeWithText(runBlocking { getString(Res.string.profile_reminder_off) }).assertExists()
                    ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(dir, "profile-$language-$name-${if (dark) "dark" else "light"}.png"))
                }
            }
        }
    }
}
