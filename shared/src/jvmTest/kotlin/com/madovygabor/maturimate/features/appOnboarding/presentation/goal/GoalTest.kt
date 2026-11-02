package com.madovygabor.maturimate.features.appOnboarding.presentation.goal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.appOnboarding.presentation.QuestionUiState
import com.madovygabor.maturimate.features.userProfile.data.UserProfileRepositoryImpl
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.goal_minutes
import maturimate.shared.generated.resources.goal_title
import maturimate.shared.generated.resources.goal_year_hint
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class GoalTest {

    private val defaultLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private fun repository(): UserProfileRepositoryImpl {
        val database = UserDatabase(
            JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) }
        )
        return UserProfileRepositoryImpl(database, LocalUserProvider(database), Dispatchers.Unconfined)
    }

    @Test
    fun tenMinutesArePreselectedAndASavedGoalIsKept() = runBlocking {
        val repository = repository()
        val fresh = GoalViewModel(repository, SnackbarController())
        assertEquals(10, withTimeout(5_000) { fresh.state.first { it.selected != null } }.selected)

        repository.saveDailyGoal(20)
        val saved = GoalViewModel(repository, SnackbarController())
        assertEquals(20, withTimeout(5_000) { saved.state.first { it.selected != null } }.selected)
    }

    @Test
    fun yearlyHoursAreRounded() {
        assertEquals(30, hoursPerYear(5))
        assertEquals(61, hoursPerYear(10))
        assertEquals(91, hoursPerYear(15))
        assertEquals(122, hoursPerYear(20))
    }

    @Test
    fun slovakTextsUseTheirPluralForms() = runBlocking {
        Locale.setDefault(Locale.forLanguageTag("sk"))
        assertEquals("5 minút", getPluralString(Res.plurals.goal_minutes, 5, 5))
        assertTrue(getPluralString(Res.plurals.goal_year_hint, 122, 20, 122).contains("približne 122 hodín"))
        assertTrue(getPluralString(Res.plurals.goal_year_hint, 3, 1, 3).contains("približne 3 hodiny"))
    }

    @Test
    fun pickingAGoalShowsWhatItMeansInAYear() = runComposeUiTest {
        var state by mutableStateOf(QuestionUiState(10))
        setContent { AppTheme(darkTheme = false) { GoalContent(state, UserRole.LEARNER, { state = state.copy(selected = it) }, {}, {}) } }

        onNodeWithText(runBlocking { getPluralString(Res.plurals.goal_minutes, 20, 20) }).performClick()

        assertEquals(20, state.selected)
        onNodeWithText(runBlocking { getPluralString(Res.plurals.goal_year_hint, 122, 20, 122) }).assertExists()
    }

    /** Pictures at phone, tablet and desktop size, both themes and languages, into build/screenshots/goal. */
    @Test
    fun rendersAtEverySizeAndTheme() {
        val sizes = mapOf("phone" to (412 to 915), "tablet" to (800 to 1100), "desktop" to (1280 to 800))
        val dir = File("build/screenshots/goal").apply { mkdirs() }
        for (language in listOf("hu", "sk")) {
            Locale.setDefault(Locale.forLanguageTag(language))
            for ((name, size) in sizes) for (dark in listOf(false, true)) {
                runDesktopComposeUiTest(size.first, size.second) {
                    setContent { AppTheme(darkTheme = dark) { GoalContent(QuestionUiState(10), UserRole.STUDENT, {}, {}, {}) } }
                    onNodeWithText(runBlocking { getString(Res.string.goal_title) }).assertExists()
                    ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(dir, "goal-$language-$name-${if (dark) "dark" else "light"}.png"))
                }
            }
        }
    }
}
