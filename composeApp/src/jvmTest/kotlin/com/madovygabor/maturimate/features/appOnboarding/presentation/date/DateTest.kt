package com.madovygabor.maturimate.features.appOnboarding.presentation.date

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
import com.madovygabor.maturimate.features.userProfile.domain.ExamDates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.LocalDate
import maturimate.composeapp.generated.resources.Res
import maturimate.composeapp.generated.resources.date_other
import maturimate.composeapp.generated.resources.common_cancel
import maturimate.composeapp.generated.resources.date_title
import maturimate.composeapp.generated.resources.date_written_days
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
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
class DateTest {

    private val defaultLocale: Locale = Locale.getDefault()
    private val today = LocalDate(2026, 10, 31)

    @AfterTest
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private fun text(key: StringResource) = runBlocking { getString(key) }

    private fun repository(): UserProfileRepositoryImpl {
        val database = UserDatabase(
            JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) }
        )
        return UserProfileRepositoryImpl(database, LocalUserProvider(database), Dispatchers.Unconfined)
    }

    @Test
    fun writtenExamsStartOnTheSecondMondayOfMarch() {
        // 1 March 2027 is a Monday, so the second Monday is the 8th
        assertEquals(LocalDate(2027, 3, 8), ExamDates.secondWeekWeekday(2027))
        // 1 March 2026 is a Sunday: Mondays on the 2nd and the 9th
        assertEquals(LocalDate(2026, 3, 9), ExamDates.secondWeekWeekday(2026))
        assertEquals(LocalDate(2028, 3, 13), ExamDates.secondWeekWeekday(2028))
        assertEquals(LocalDate(2029, 3, 12), ExamDates.secondWeekWeekday(2029))
    }

    @Test
    fun nextTwoSpringExamsFollowTheDate() {
        assertEquals(listOf(LocalDate(2027, 3, 8), LocalDate(2028, 3, 13)), ExamDates.nextSpringExams(today))
        assertEquals(listOf(LocalDate(2027, 3, 8), LocalDate(2028, 3, 13)), ExamDates.nextSpringExams(LocalDate(2027, 3, 7)))
        // on the exam day the next spring is offered
        assertEquals(LocalDate(2028, 3, 13), ExamDates.nextSpringExams(LocalDate(2027, 3, 8)).first())
        assertTrue(ExamDates.isValid(LocalDate(2029, 10, 31), today))
        assertFalse(ExamDates.isValid(LocalDate(2029, 11, 1), today))
        assertFalse(ExamDates.isValid(LocalDate(2026, 10, 30), today))
    }

    @Test
    fun nextSpringExamIsPreselected() = runBlocking {
        val viewModel = DateViewModel(repository(), SnackbarController(), today)

        val state = withTimeout(5_000) { viewModel.state.first { it.selected != null } }

        assertEquals(LocalDate(2027, 3, 8), state.selected)
    }

    @Test
    fun savedDateIsKeptAndAPassedOneReplaced() = runBlocking {
        val repository = repository()
        repository.saveGraduationDate(LocalDate(2027, 5, 20))
        val kept = DateViewModel(repository, SnackbarController(), today)
        assertEquals(LocalDate(2027, 5, 20), withTimeout(5_000) { kept.state.first { it.selected != null } }.selected)

        repository.saveGraduationDate(LocalDate(2026, 3, 15))
        val replaced = DateViewModel(repository, SnackbarController(), today)
        assertEquals(LocalDate(2027, 3, 8), withTimeout(5_000) { replaced.state.first { it.selected != null } }.selected)
    }

    @Test
    fun slovakDaysUseTheirPluralForms() = runBlocking {
        Locale.setDefault(Locale.forLanguageTag("sk"))
        assertTrue(getPluralString(Res.plurals.date_written_days, 1, "x", 1).endsWith("o 1 deň"))
        assertTrue(getPluralString(Res.plurals.date_written_days, 3, "x", 3).endsWith("o 3 dni"))
        assertTrue(getPluralString(Res.plurals.date_written_days, 135, "x", 135).endsWith("o 135 dní"))
    }

    @Test
    fun otherDateOpensTheCalendar() = runComposeUiTest {
        val suggestions = ExamDates.nextSpringExams(today)
        setContent {
            AppTheme(darkTheme = false) { DateContent(QuestionUiState(suggestions.first()), today, suggestions, {}, {}, {}) }
        }

        onNodeWithText(text(Res.string.date_other)).performClick()

        onNodeWithText(text(Res.string.common_cancel)).assertExists()
    }

    /** Pictures at phone, tablet and desktop size, both themes and languages, into build/screenshots/date. */
    @Test
    fun rendersAtEverySizeAndTheme() {
        val sizes = mapOf("phone" to (412 to 915), "tablet" to (800 to 1100), "desktop" to (1280 to 800))
        val dir = File("build/screenshots/date").apply { mkdirs() }
        val suggestions = ExamDates.nextSpringExams(today)
        for (language in listOf("hu", "sk")) {
            Locale.setDefault(Locale.forLanguageTag(language))
            for ((name, size) in sizes) for (dark in listOf(false, true)) {
                runDesktopComposeUiTest(size.first, size.second) {
                    setContent {
                        AppTheme(darkTheme = dark) {
                            DateContent(QuestionUiState(LocalDate(2027, 5, 20)), today, suggestions, {}, {}, {})
                        }
                    }
                    onNodeWithText(text(Res.string.date_title)).assertExists()
                    val file = File(dir, "date-$language-$name-${if (dark) "dark" else "light"}.png")
                    ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", file)
                }
            }
        }
    }
}
