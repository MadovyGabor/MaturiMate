package com.madovygabor.maturimate.features.learning.presentation

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.features.learning.domain.AnswerChecker
import com.madovygabor.maturimate.features.learning.domain.model.AcceptedAnswer
import com.madovygabor.maturimate.features.learning.domain.model.KeySlot
import com.madovygabor.maturimate.features.learning.domain.model.OptionRole
import com.madovygabor.maturimate.features.learning.domain.model.Task
import com.madovygabor.maturimate.features.learning.domain.model.TaskFormat
import com.madovygabor.maturimate.features.learning.domain.model.TaskOption
import kotlinx.coroutines.runBlocking
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.common_next
import maturimate.shared.generated.resources.test_check
import maturimate.shared.generated.resources.test_correct
import maturimate.shared.generated.resources.test_empty
import maturimate.shared.generated.resources.test_finish
import maturimate.shared.generated.resources.test_hint_request
import maturimate.shared.generated.resources.test_right_answer
import maturimate.shared.generated.resources.test_why_not
import maturimate.shared.generated.resources.test_wrong
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class TestScreenTest {

    private val defaultLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private fun text(key: StringResource) = runBlocking { getString(key) }

    private fun choiceTask(id: Long, format: TaskFormat, question: String, options: List<Pair<String, String>>, right: String, why: Map<String, String> = emptyMap(), explanation: String? = null) =
        Task(
            id, 1, format, question, explanation, 1, null, true,
            options = options.map { (label, text) -> TaskOption(label, text, OptionRole.ANSWER, why[label]) },
            key = listOf(KeySlot(1, right, null)), acceptedAnswers = emptyList(), attachments = emptyList(),
        )

    private val multipleChoice = choiceTask(
        1, TaskFormat.MC_SINGLE, "Melyik szókép azonosít két dolgot?",
        listOf("A" to "hasonlat", "B" to "metafora", "C" to "megszemélyesítés"), right = "B",
        why = mapOf("A" to "A hasonlat összehasonlít."), explanation = "A metafora azonosít.",
    )
    private val trueFalse = choiceTask(2, TaskFormat.TRUE_FALSE, "A hasonlatban szerepel a *mint*.", listOf("I" to "igaz", "H" to "hamis"), right = "I")
    private val shortAnswer = Task(
        3, 1, TaskFormat.SHORT_ANSWER, "Hogy hívjuk?", "Megszemélyesítés.", 1, 1, true,
        options = emptyList(), key = emptyList(), acceptedAnswers = listOf(AcceptedAnswer(1, "megszemélyesítés", false)), attachments = emptyList(),
    )

    private fun ready(index: Int, selected: String? = null, typed: String = "", hintShown: Boolean = false, check: Boolean = false): TestUiState.Ready {
        val tasks = listOf(multipleChoice, trueFalse, shortAnswer)
        val state = TestUiState.Ready("Mini teszt", tasks, index, selected, typed, hintShown)
        return if (check) state.copy(result = AnswerChecker.check(state.task, state.answer)) else state
    }

    private class Calls {
        val selected = mutableListOf<String>()
        var typed = ""
        var hint = 0
        var check = 0
        var next = 0
    }

    private fun ComposeUiTest.show(state: TestUiState, calls: Calls = Calls()) = setContent {
        AppTheme {
            TestContent(
                state = state,
                onClose = {},
                onSelect = { calls.selected += it },
                onType = { calls.typed = it },
                onHint = { calls.hint++ },
                onCheck = { calls.check++ },
                onNext = { calls.next++ },
                onRetry = {},
            )
        }
    }

    @Test
    fun checkNeedsAChoiceAndThenChecks() = runDesktopComposeUiTest(412, 915) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val calls = Calls()
        show(ready(0), calls)

        onNodeWithText("metafora").assertIsDisplayed()
        onNodeWithText(text(Res.string.test_check)).assertIsNotEnabled()
        onNodeWithText("metafora").performClick()
        assertEquals(listOf("B"), calls.selected)
    }

    @Test
    fun chosenOptionEnablesTheCheckButton() = runDesktopComposeUiTest(412, 915) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val calls = Calls()
        show(ready(0, selected = "B"), calls)

        onNodeWithText(text(Res.string.test_check)).assertIsEnabled().performClick()
        assertEquals(1, calls.check)
    }

    @Test
    fun wrongAnswerShowsTheRightAnswerTheWhyAndTheExplanation() = runDesktopComposeUiTest(412, 915) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val calls = Calls()
        show(ready(0, selected = "A", check = true), calls)

        onNodeWithText(text(Res.string.test_wrong)).assertIsDisplayed()
        onNodeWithText(text(Res.string.test_right_answer), substring = true).assertIsDisplayed()
        onNodeWithText(text(Res.string.test_why_not), substring = true).assertIsDisplayed()
        onNodeWithText("A hasonlat összehasonlít.", substring = true).assertIsDisplayed()
        onNodeWithText("A metafora azonosít.", substring = true).assertIsDisplayed()

        onNodeWithText(text(Res.string.common_next)).performClick()
        assertEquals(1, calls.next)
    }

    @Test
    fun rightAnswerShowsOnlyTheExplanation() = runDesktopComposeUiTest(412, 915) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        show(ready(0, selected = "B", check = true))

        onNodeWithText(text(Res.string.test_correct)).assertIsDisplayed()
        onAllNodesWithText(text(Res.string.test_right_answer), substring = true).assertCountEquals(0)
        onNodeWithText("A metafora azonosít.", substring = true).assertIsDisplayed()
    }

    @Test
    fun trueOrFalseHasTwoBigAnswers() = runDesktopComposeUiTest(412, 915) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val calls = Calls()
        show(ready(1), calls)

        onNodeWithText("Igaz").assertIsDisplayed()
        onNodeWithText("Hamis").performClick()
        assertEquals(listOf("H"), calls.selected)
    }

    @Test
    fun shortAnswerOffersAHintAndTakesText() = runDesktopComposeUiTest(412, 915) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val calls = Calls()
        show(ready(2), calls)

        onNodeWithText(text(Res.string.test_hint_request)).performClick()
        assertEquals(1, calls.hint)
    }

    @Test
    fun shownHintGivesTheFirstLetterAndTheLength() = runDesktopComposeUiTest(412, 915) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        show(ready(2, hintShown = true))

        onNodeWithText("Első betű: M · 16 betű").assertIsDisplayed()
    }

    @Test
    fun lastTaskEndsWithFinish() = runDesktopComposeUiTest(412, 915) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        show(ready(2, typed = "Megszemélyesítés", check = true))

        onNodeWithText(text(Res.string.test_finish)).assertIsDisplayed()
    }

    @Test
    fun aTestWithoutShowableTasksSaysSo() = runDesktopComposeUiTest(412, 915) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        show(TestUiState.Empty)

        onNodeWithText(text(Res.string.test_empty)).assertIsDisplayed()
    }
}
