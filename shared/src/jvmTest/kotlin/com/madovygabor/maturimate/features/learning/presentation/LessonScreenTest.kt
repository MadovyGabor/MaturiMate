package com.madovygabor.maturimate.features.learning.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.features.learning.domain.model.BlockType
import com.madovygabor.maturimate.features.learning.domain.model.LessonBlock
import com.madovygabor.maturimate.features.learning.domain.model.LessonPage
import kotlinx.coroutines.runBlocking
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.common_next
import maturimate.shared.generated.resources.lesson_box_example
import maturimate.shared.generated.resources.lesson_close
import maturimate.shared.generated.resources.lesson_contents
import maturimate.shared.generated.resources.lesson_finish
import org.jetbrains.compose.resources.getString
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class LessonScreenTest {

    private val defaultLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private val pages = listOf(
        LessonPage("Mi a szókép?", listOf(LessonBlock(BlockType.PARAGRAPH, "A **szókép** átvitt jelentés."))),
        LessonPage("Hasonlat", listOf(
            LessonBlock(BlockType.PARAGRAPH, "Két dolgot hasonlít össze."),
            LessonBlock(BlockType.EXAMPLE, "Olyan gyors, mint a villám."),
        )),
        LessonPage(null, listOf(LessonBlock(BlockType.TIP, "Keresd a *mint* szót."))),
    )

    private fun ready(index: Int) = LessonUiState.Ready("Szóképek", pages, index)

    private fun text(key: org.jetbrains.compose.resources.StringResource) = runBlocking { getString(key) }

    private class Calls {
        var next = 0
        var previous = 0
        var finish = 0
        var closed = 0
        var goTo: Int? = null
    }

    private fun androidx.compose.ui.test.ComposeUiTest.show(state: LessonUiState, calls: Calls) = setContent {
        AppTheme {
            LessonContent(
                state = state,
                onClose = { calls.closed++ },
                onNext = { calls.next++ },
                onPrevious = { calls.previous++ },
                onGoTo = { calls.goTo = it },
                onFinish = { calls.finish++ },
                onRetry = {},
            )
        }
    }

    @Test
    fun pageShowsItsTitleTextAndBoxAndPagesForward() = runDesktopComposeUiTest(412, 915) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val calls = Calls()
        show(ready(1), calls)

        onNodeWithText("Hasonlat").assertIsDisplayed()
        onNodeWithText("Két dolgot hasonlít össze.").assertIsDisplayed()
        onNodeWithText("Olyan gyors, mint a villám.").assertIsDisplayed()
        onNodeWithText(text(Res.string.lesson_box_example).uppercase()).assertIsDisplayed()

        onNodeWithText(text(Res.string.common_next)).performClick()
        assertEquals(1, calls.next)
    }

    @Test
    fun lastPageFinishesTheLesson() = runDesktopComposeUiTest(412, 915) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val calls = Calls()
        show(ready(2), calls)

        onNodeWithText(text(Res.string.lesson_finish)).performClick()
        assertEquals(1, calls.finish)
        assertEquals(0, calls.next)
    }

    @Test
    fun contentsSheetJumpsToAPage() = runDesktopComposeUiTest(412, 915) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val calls = Calls()
        show(ready(0), calls)

        onNodeWithContentDescription(text(Res.string.lesson_contents)).performClick()
        waitForIdle()
        onNodeWithText("Hasonlat").performClick()

        assertEquals(1, calls.goTo)
    }

    @Test
    fun wideWindowAlwaysShowsTheContentsAtTheSide() = runDesktopComposeUiTest(1200, 800) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val calls = Calls()
        show(ready(0), calls)

        // no button needed: every page title is already in the list at the side
        onNodeWithText("Hasonlat").assertIsDisplayed()
        // the first page is the page being read and an entry of the list
        onAllNodesWithText("Mi a szókép?").assertCountEquals(2)
        onNodeWithText("Hasonlat").performClick()
        assertEquals(1, calls.goTo)
    }

    @Test
    fun failedLoadOffersRetryAndClose() = runDesktopComposeUiTest(412, 915) {
        val calls = Calls()
        show(LessonUiState.Failed, calls)

        onNodeWithContentDescription(text(Res.string.lesson_close)).performClick()
        assertEquals(1, calls.closed)
    }
}
