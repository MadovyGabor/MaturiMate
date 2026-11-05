package com.madovygabor.maturimate.features.profileDetails.presentation

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import com.madovygabor.maturimate.features.learning.domain.ActivityKind
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.features.learning.domain.RecentActivity
import com.madovygabor.maturimate.features.learning.domain.StudyTotals
import com.madovygabor.maturimate.features.learning.domain.model.LessonTestCount
import com.madovygabor.maturimate.features.learning.domain.model.PartProgress
import com.madovygabor.maturimate.features.learning.domain.model.StepCount
import com.madovygabor.maturimate.features.learning.domain.model.SubjectBreakdown
import com.madovygabor.maturimate.features.learning.domain.model.Topic
import com.madovygabor.maturimate.features.profileDetails.domain.History
import com.madovygabor.maturimate.features.profileDetails.domain.HistoryGroup
import com.madovygabor.maturimate.features.profileDetails.domain.HistorySection
import com.madovygabor.maturimate.features.profileDetails.domain.ProfileSubject
import com.madovygabor.maturimate.features.profileDetails.domain.ProfileSummary
import com.madovygabor.maturimate.features.userProfile.domain.CatalogSubject
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class, ExperimentalTime::class)
class ProfileOverviewScreenTest {

    private val defaultLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private val today = LocalDate(2026, 11, 4)
    private val hungarian = SubjectBreakdown(
        subjectId = 1,
        count = LessonTestCount(StepCount(8, 16), StepCount(2, 5)),
        parts = listOf(
            PartProgress(Topic(10, 1, null, "Nyelvtan", null, 1), LessonTestCount(StepCount(7, 12), StepCount(2, 4))),
            PartProgress(Topic(20, 1, null, "Irodalom", null, 2), LessonTestCount(StepCount(1, 4), StepCount(0, 1))),
        ),
    )
    private val test = RecentActivity(51, "Mini teszt", "Szóképek", kind = ActivityKind.TEST, at = Instant.parse("2026-11-04T08:00:00Z"), passed = false, earnedPoints = 3, maxPoints = 5)
    private val lesson = RecentActivity(50, "Mi a szókép?", "Szóképek", kind = ActivityKind.LESSON, at = Instant.parse("2026-11-03T08:00:00Z"), passed = null)
    private val practice = RecentActivity(
        null, "", "", kind = ActivityKind.PRACTICE, at = Instant.parse("2026-11-04T09:00:00Z"), passed = null,
        earnedPoints = 8, maxPoints = 10, subjectIds = listOf(1),
    )

    private fun summary(firstName: String? = "Gábor", recent: List<RecentActivity> = listOf(test, lesson)) = ProfileSummary(
        firstName = firstName,
        streak = 5,
        totals = StudyTotals(secondsStudied = 12 * 3600 + 40 * 60, tasksAnswered = 214),
        subjects = listOf(ProfileSubject(CatalogSubject.HUNGARIAN, hungarian), ProfileSubject(CatalogSubject.MATHEMATICS, null)),
        recent = recent,
        today = today,
    )

    @Test
    fun theProfileShowsTheNameTheNumbersAndTheProgress() = runDesktopComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        setContent { AppTheme { ProfileOverviewContent(ProfileOverviewUiState.Ready(summary()), ProfileActions()) } }

        onNodeWithText("Gábor").assertIsDisplayed()
        onNodeWithContentDescription("5 napos sorozat").assertIsDisplayed()
        onNodeWithContentDescription("12 óra 40 perc tanulás").assertIsDisplayed()
        onNodeWithContentDescription("214 megoldott feladat").assertIsDisplayed()
        // 10 of 21 lessons and tests
        onNodeWithText("48%").assertIsDisplayed()
        onNodeWithText("Leckék 8 / 16").assertIsDisplayed()
        // no content for Mathematics yet
        onNodeWithText("Hamarosan").assertIsDisplayed()
        // the parts are closed until the card is tapped
        onAllNodesWithText("Nyelvtan").assertCountEquals(0)
    }

    @Test
    fun anOpenSubjectShowsItsParts() = runDesktopComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        var toggled: Long? = null
        setContent { AppTheme { ProfileOverviewContent(ProfileOverviewUiState.Ready(summary(), open = setOf(1)), ProfileActions(onToggle = { toggled = it })) } }

        onNodeWithText("Nyelvtan").assertIsDisplayed()
        onNodeWithText("Irodalom").assertIsDisplayed()
        onNodeWithText("Leckék 7 / 12").assertIsDisplayed()
        onNodeWithText("Magyar nyelv és irodalom").performClick()
        assertEquals(1L, toggled)
    }

    @Test
    fun theActivitiesHaveAKindAndAResultWithThePoints() = runDesktopComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        var history = false
        setContent { AppTheme { ProfileOverviewContent(ProfileOverviewUiState.Ready(summary()), ProfileActions(onHistory = { history = true })) } }

        onNodeWithText("Teszt").assertIsDisplayed()
        onNodeWithText("Újra · 3 / 5").assertIsDisplayed()
        onNodeWithText("Lecke").assertIsDisplayed()
        onNodeWithText("Kész").assertIsDisplayed()
        onNodeWithText("Összes megtekintése").performClick()
        assertTrue(history)
    }

    @Test
    fun aGuestIsCalledGuestAndANewStudentHasNoHistoryLink() = runDesktopComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        setContent { AppTheme { ProfileOverviewContent(ProfileOverviewUiState.Ready(summary(firstName = null, recent = emptyList())), ProfileActions()) } }

        onNodeWithText("Vendég").assertIsDisplayed()
        onNodeWithText("Itt jelennek meg a befejezett leckéid és teszteid. Kezdj egy leckével!").assertIsDisplayed()
        onAllNodesWithText("Összes megtekintése").assertCountEquals(0)
    }

    @Test
    fun aGuestIsAskedToCreateAnAccountOrSignIn() = runDesktopComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        var signUp = false
        var signIn = false
        setContent {
            AppTheme { ProfileOverviewContent(ProfileOverviewUiState.Ready(summary(firstName = null)), ProfileActions(onSignUp = { signUp = true }, onSignIn = { signIn = true })) }
        }

        onNodeWithText("Hozz létre fiókot").assertIsDisplayed()
        onNodeWithText("Fiók létrehozása").performClick()
        onNodeWithText("Már van fiókom").performClick()
        assertTrue(signUp && signIn)
    }

    @Test
    fun withAnAccountThereIsNoCallToSignUp() = runDesktopComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        setContent { AppTheme { ProfileOverviewContent(ProfileOverviewUiState.Ready(summary()), ProfileActions()) } }

        onAllNodesWithText("Hozz létre fiókot").assertCountEquals(0)
    }

    @Test
    fun theGearOpensTheSettings() = runDesktopComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        var settings = false
        setContent { AppTheme { ProfileOverviewContent(ProfileOverviewUiState.Ready(summary()), ProfileActions(onSettings = { settings = true })) } }

        onNodeWithContentDescription("Beállítások").performClick()
        assertTrue(settings)
    }

    @Test
    fun theFullHistoryHasDayHeadingsAndAFilter() = runDesktopComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val history = History(
            listOf(
                HistorySection(HistoryGroup.Today, listOf(test)),
                HistorySection(HistoryGroup.DaysAgo(3), listOf(lesson)),
                HistorySection(HistoryGroup.WeeksAgo(1), listOf(lesson.copy(unitId = 52, title = "A szófajok"))),
                HistorySection(HistoryGroup.InMonth(2026, Month.OCTOBER), listOf(lesson.copy(unitId = 53, title = "Halmazok"))),
                HistorySection(HistoryGroup.InMonth(2025, Month.DECEMBER), listOf(lesson.copy(unitId = 54, title = "Egyenletek"))),
            ),
            today,
        )
        var filter = HistoryFilter.ALL
        setContent { AppTheme { HistoryContent(HistoryUiState.Ready(history), onBack = {}, onFilter = { filter = it }, onRetry = {}) } }

        onNodeWithText("Ma").assertIsDisplayed()
        onNodeWithText("3 napja").assertIsDisplayed()
        onNodeWithText("1 hete").assertIsDisplayed()
        onNodeWithText("Október").assertIsDisplayed()
        onNodeWithText("2025. december").assertExists()
        onNodeWithText("Tesztek").performClick()
        assertEquals(HistoryFilter.TESTS, filter)
    }

    @Test
    fun aPracticeRoundShowsItsNameSubjectKindAndResult() = runDesktopComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val history = History(listOf(HistorySection(HistoryGroup.Today, listOf(practice, test))), today)
        setContent { AppTheme { HistoryContent(HistoryUiState.Ready(history), onBack = {}, onFilter = {}, onRetry = {}) } }

        onNodeWithText("Szabad gyakorlás").assertIsDisplayed()
        onNodeWithText("Magyar nyelv és irodalom").assertIsDisplayed()
        // the kind tag of the card and the filter chip
        onAllNodesWithText("Gyakorlás").assertCountEquals(2)
        onNodeWithText("8 / 10").assertIsDisplayed()
    }

    @Test
    fun thePracticeFilterLeavesOnlyPracticeAndTheTestsFilterNone() = runDesktopComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val history = History(listOf(HistorySection(HistoryGroup.Today, listOf(practice, test, lesson))), today)
        var filter by mutableStateOf(HistoryFilter.PRACTICE)
        setContent { AppTheme { HistoryContent(HistoryUiState.Ready(history, filter), onBack = {}, onFilter = { filter = it }, onRetry = {}) } }

        onNodeWithText("Szabad gyakorlás").assertIsDisplayed()
        onAllNodesWithText("Mini teszt").assertCountEquals(0)
        onAllNodesWithText("Mi a szókép?").assertCountEquals(0)

        filter = HistoryFilter.TESTS
        waitForIdle()
        onAllNodesWithText("Szabad gyakorlás").assertCountEquals(0)
        onNodeWithText("Mini teszt").assertIsDisplayed()
    }

    @Test
    fun aPracticeRoundOnTheProfileHasItsSubjectAndDay() = runDesktopComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        setContent { AppTheme { ProfileOverviewContent(ProfileOverviewUiState.Ready(summary(recent = listOf(practice))), ProfileActions()) } }

        onNodeWithText("Szabad gyakorlás").assertIsDisplayed()
        onNodeWithText("Magyar nyelv és irodalom · Ma").assertIsDisplayed()
    }

    @Test
    fun theTestsFilterLeavesOnlyTheTests() = runDesktopComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val history = History(listOf(HistorySection(HistoryGroup.Today, listOf(test, lesson))), today)
        setContent { AppTheme { HistoryContent(HistoryUiState.Ready(history, HistoryFilter.TESTS), onBack = {}, onFilter = {}, onRetry = {}) } }

        onNodeWithText("Mini teszt").assertIsDisplayed()
        onAllNodesWithText("Mi a szókép?").assertCountEquals(0)
    }
}
