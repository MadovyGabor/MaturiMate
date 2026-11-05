package com.madovygabor.maturimate.features.learning.presentation

import com.madovygabor.maturimate.features.learning.domain.ActivityKind
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.features.learning.domain.DailyGoalProgress
import com.madovygabor.maturimate.features.learning.domain.HomeSummary
import com.madovygabor.maturimate.features.learning.domain.MottoKind
import com.madovygabor.maturimate.features.learning.domain.RecentActivity
import com.madovygabor.maturimate.features.learning.domain.WeekDay
import com.madovygabor.maturimate.features.learning.domain.WeekDayState
import com.madovygabor.maturimate.features.learning.domain.model.ContinueTarget
import com.madovygabor.maturimate.features.learning.domain.model.LearningUnit
import com.madovygabor.maturimate.features.learning.domain.model.StepKind
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class, ExperimentalTime::class)
class HomeScreenTest {

    private val defaultLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private val monday = LocalDate(2026, 11, 2)
    private val target = ContinueTarget(
        subjectId = 1,
        topicIds = listOf(10, 11),
        topicTitle = "Szóképek",
        unit = LearningUnit.Summary(50, 11, "Hasonlat és metafora", emptyList(), 3, 10, 2),
        kind = StepKind.LESSON,
    )

    private fun summary(
        seconds: Int = 360,
        streak: Int = 5,
        next: ContinueTarget? = target,
        recent: List<RecentActivity> = emptyList(),
        motto: MottoKind = MottoKind.STREAK,
    ) = HomeSummary(
        firstName = "Gábor",
        streak = streak,
        goal = DailyGoalProgress(monday.plus(DatePeriod(days = 2)), seconds, 600),
        week = (0..6).map { WeekDay(monday.plus(DatePeriod(days = it)), if (it < 2) WeekDayState.DONE else WeekDayState.LATER) },
        daysToExam = 128,
        next = next,
        recent = recent,
        motto = motto,
    )

    @Test
    fun theHomeScreenGreetsByNameAndShowsTheFactsAndTheGoal() = runDesktopComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        setContent { AppTheme { HomeContent(HomeUiState.Ready(summary()), onContinue = {}, onRetry = {}) } }

        onNodeWithText("Szia, Gábor!").assertIsDisplayed()
        onNodeWithText("5 napos sorozat").assertIsDisplayed()
        onNodeWithText("Érettségiig 128 nap").assertIsDisplayed()
        // 6 of 10 minutes, and the streak is in danger until the goal is reached
        onNodeWithText("Még 4 perc a mai célig").assertIsDisplayed()
        onNodeWithText("A sorozatod ma éjfélig él. 6 perc megvan a 10-ből.").assertIsDisplayed()
        onNodeWithText("Hasonlat és metafora").assertIsDisplayed()
    }

    @Test
    fun continueLeadsToTheNextStep() = runDesktopComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        var opened: ContinueTarget? = null
        setContent { AppTheme { HomeContent(HomeUiState.Ready(summary()), onContinue = { opened = it }, onRetry = {}) } }

        onNodeWithText("Folytatás").performClick()
        assertEquals(target, opened)
    }

    @Test
    fun aReachedGoalSaysSo() = runDesktopComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val recent = listOf(RecentActivity(50, "Mi a szókép?", "Szóképek", kind = ActivityKind.LESSON, at = Instant.parse("2026-11-04T08:00:00Z"), passed = null))
        setContent { AppTheme { HomeContent(HomeUiState.Ready(summary(seconds = 720, motto = MottoKind.REACHED, recent = recent)), onContinue = {}, onRetry = {}) } }

        onNodeWithText("A mai cél megvan").assertIsDisplayed()
        // the mistake card is no longer "Hamarosan": it shows only with mistakes (see the mistake card tests)
        onAllNodesWithText("Hamarosan").assertCountEquals(0)
    }

    @Test
    fun aNewStudentHasNothingToRepeatSoThereIsNoSuggestion() = runDesktopComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        setContent { AppTheme { HomeContent(HomeUiState.Ready(summary(seconds = 0, streak = 0, motto = MottoKind.FRESH)), onContinue = {}, onRetry = {}) } }

        onNodeWithText("Az első lecke csak pár perc. Kezdjük!").assertIsDisplayed()
        onAllNodesWithText("Ismételd át a hibáidat").assertCountEquals(0)
    }

    @Test
    fun withNothingLeftTheCardSaysEverythingIsDone() = runDesktopComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        setContent { AppTheme { HomeContent(HomeUiState.Ready(summary(next = null)), onContinue = {}, onRetry = {}) } }

        onNodeWithText("Mindent befejeztél").assertIsDisplayed()
    }

    @Test
    fun lastActivitiesShowTheirTopicAndResult() = runDesktopComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val recent = listOf(
            RecentActivity(51, "Mini teszt", "Szóképek", kind = ActivityKind.TEST, at = Instant.parse("2026-11-04T08:00:00Z"), passed = true),
        )
        setContent { AppTheme { HomeContent(HomeUiState.Ready(summary(recent = recent)), onContinue = {}, onRetry = {}) } }

        onNodeWithText("Mini teszt").assertIsDisplayed()
        onNodeWithText("Átment").assertIsDisplayed()
    }

    @Test
    fun theMistakeCardShowsTheMistakesAndOpensThePracticeTab() = runDesktopComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        var opened = 0
        setContent { AppTheme { HomeContent(HomeUiState.Ready(summary().copy(mistakes = 4)), onContinue = {}, onRetry = {}, onOpenPractice = { opened++ }) } }

        onNodeWithText("4 hibás feladat").assertIsDisplayed()
        onNodeWithText("Ismételd át a hibáidat").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun withoutMistakesThereIsNoMistakeCard() = runDesktopComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        setContent { AppTheme { HomeContent(HomeUiState.Ready(summary().copy(mistakes = 0)), onContinue = {}, onRetry = {}) } }

        onAllNodesWithText("Ismételd át a hibáidat").assertCountEquals(0)
    }

    @Test
    fun aPracticeRoundAmongTheLastActivitiesHasNoPassing() = runDesktopComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val recent = listOf(
            RecentActivity(
                null, "", "", kind = ActivityKind.PRACTICE, at = Instant.parse("2026-11-04T08:00:00Z"), passed = null,
                earnedPoints = 4, maxPoints = 5, subjectIds = listOf(1, 4),
            ),
        )
        setContent { AppTheme { HomeContent(HomeUiState.Ready(summary(recent = recent)), onContinue = {}, onRetry = {}) } }

        onNodeWithText("Szabad gyakorlás").assertIsDisplayed()
        onNodeWithText("2 tantárgy", substring = true).assertIsDisplayed()
        onNodeWithText("4 / 5").assertIsDisplayed()
        onAllNodesWithText("Átment").assertCountEquals(0)
    }
}
