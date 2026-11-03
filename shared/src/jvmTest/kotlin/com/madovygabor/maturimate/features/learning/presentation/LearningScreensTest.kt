package com.madovygabor.maturimate.features.learning.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.features.learning.domain.model.LearningUnit
import com.madovygabor.maturimate.features.learning.domain.model.PathStep
import com.madovygabor.maturimate.features.learning.domain.model.PracticeKind
import com.madovygabor.maturimate.features.learning.domain.model.StepCount
import com.madovygabor.maturimate.features.learning.domain.model.StepKind
import com.madovygabor.maturimate.features.learning.domain.model.StepState
import com.madovygabor.maturimate.features.learning.domain.model.SubjectProgress
import com.madovygabor.maturimate.features.learning.domain.model.Topic
import com.madovygabor.maturimate.features.learning.domain.model.TopicLevel
import com.madovygabor.maturimate.features.learning.domain.model.TopicSummary
import com.madovygabor.maturimate.features.userProfile.domain.CatalogSubject
import kotlinx.coroutines.runBlocking
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.learning_load_error
import maturimate.shared.generated.resources.learning_step_done
import maturimate.shared.generated.resources.learning_step_next
import maturimate.shared.generated.resources.subjects_coming_soon
import org.jetbrains.compose.resources.getString
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class LearningScreensTest {

    private val defaultLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private fun lesson(id: Long, title: String) =
        LearningUnit.Summary(id, 11, title, emptyList(), estimatedMinutes = 3, xpReward = 10, sortOrder = id.toInt())

    private fun test(id: Long, title: String, kind: PracticeKind) =
        LearningUnit.Practice(id, 11, title, 2, kind, roundSize = 3, xpReward = 10, sortOrder = id.toInt())

    private val topic = Topic(11, null, 10, "Szóképek", null, 1)

    @Test
    fun pathShowsTheStepsWithTheirState() = runDesktopComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val path = TopicLevel.Path(
            title = "Szóképek",
            parentTitle = "Nyelvtan",
            steps = listOf(
                PathStep(lesson(1, "Mi a szókép?"), StepKind.LESSON, StepState.DONE),
                PathStep(test(2, "Mini teszt", PracticeKind.MINI), StepKind.MINI_TEST, StepState.CURRENT),
                PathStep(test(3, "Témazáró", PracticeKind.TOPIC), StepKind.TOPIC_TEST, StepState.TODO),
            ),
        )
        var clicked = 0
        setContent {
            AppTheme {
                TopicContent(
                    state = TopicUiState.Ready(path),
                    subjectName = "Magyar",
                    onBack = {},
                    onTopicClick = {},
                    onStepClick = { clicked++ },
                    onRetry = {},
                )
            }
        }

        onNodeWithText("Szóképek").assertIsEnabled()
        onNodeWithText("Nyelvtan").assertIsEnabled()
        // one step is done, one is next, one is still ahead
        runBlocking {
            onNodeWithText(getString(Res.string.learning_step_done)).assertIsEnabled()
            onNodeWithText(getString(Res.string.learning_step_next)).assertIsEnabled()
        }
        onNodeWithText("Mini teszt").performClick()
        assertEquals(1, clicked)
    }

    @Test
    fun branchListsTheTopicsWithoutTheFinalTest() = runDesktopComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val branch = TopicLevel.Branch(
            title = "Nyelvtan",
            parentTitle = null,
            topics = listOf(TopicSummary(Topic(11, null, 10, "Szóképek", "Hasonlat és metafora", 1), lessons = 2, steps = StepCount(1, 3))),
            finalTest = PathStep(test(9, "Összefoglaló", PracticeKind.FINAL), StepKind.FINAL_TEST, StepState.TODO),
        )
        var opened: Long? = null
        setContent {
            AppTheme {
                TopicContent(TopicUiState.Ready(branch), "Magyar", onBack = {}, onTopicClick = { opened = it.topic.id }, onStepClick = {}, onRetry = {})
            }
        }

        onNodeWithText("Összefoglaló").assertDoesNotExist()
        // the card tells what the topic is about, what is in it and how far the student has got
        onNodeWithText("Hasonlat és metafora").assertIsDisplayed()
        onNodeWithText("2 lecke · 1 teszt").assertIsDisplayed()
        onNodeWithText("1 / 3").assertIsDisplayed()
        onNodeWithText("Szóképek").performClick()
        assertEquals(11L, opened)
    }

    @Test
    fun subjectWithoutContentIsComingSoonAndCannotBeOpened() = runDesktopComposeUiTest {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val cards = listOf(
            SubjectCard(CatalogSubject.HUNGARIAN, SubjectProgress(1, "HU_MJL", parts = 2, steps = StepCount(1, 4))),
            SubjectCard(CatalogSubject.MATHEMATICS, null),
        )
        var opened: CatalogSubject? = null
        setContent {
            AppTheme { SubjectListContent(SubjectListUiState.Ready(cards), onSubjectClick = { opened = it }, onRetry = {}) }
        }

        runBlocking { onNodeWithText(getString(Res.string.subjects_coming_soon)).assertIsEnabled() }
        onNodeWithText("Magyar nyelv és irodalom").performClick()
        assertEquals(CatalogSubject.HUNGARIAN, opened)
        // a click on the subject without content opens nothing
        onNodeWithText("Matematika").performClick()
        assertEquals(CatalogSubject.HUNGARIAN, opened)
    }

    @Test
    fun failedReadOffersARetry() = runDesktopComposeUiTest {
        var retried = false
        setContent {
            AppTheme { SubjectListContent(SubjectListUiState.Failed, onSubjectClick = {}, onRetry = { retried = true }) }
        }

        runBlocking { onNodeWithText(getString(Res.string.learning_load_error)).assertIsEnabled() }
        onNode(hasText("Újra") or hasText("Skúsiť znova")).performClick()
        assertEquals(true, retried)
    }
}
