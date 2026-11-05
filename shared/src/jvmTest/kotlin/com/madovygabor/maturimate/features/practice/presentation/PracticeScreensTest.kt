package com.madovygabor.maturimate.features.practice.presentation

import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.uiComponents.AppCheckRow
import com.madovygabor.maturimate.core.presentation.uiComponents.CheckRowStyle
import com.madovygabor.maturimate.features.learning.domain.model.Subject
import com.madovygabor.maturimate.features.learning.domain.model.Topic
import com.madovygabor.maturimate.features.learning.domain.model.Track
import com.madovygabor.maturimate.features.practice.domain.PracticeNode
import com.madovygabor.maturimate.features.practice.domain.PracticeSubject
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.picker_custom
import maturimate.shared.generated.resources.picker_custom_invalid
import maturimate.shared.generated.resources.picker_custom_label
import maturimate.shared.generated.resources.picker_not_finished
import maturimate.shared.generated.resources.picker_pick_hint
import maturimate.shared.generated.resources.practice_free_none
import maturimate.shared.generated.resources.practice_free_start
import maturimate.shared.generated.resources.practice_free_text
import maturimate.shared.generated.resources.practice_soon_text
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** The practice tab, the picker and its tick-box rows, drawn from hand-built states. */
@OptIn(ExperimentalTestApi::class)
class PracticeScreensTest {

    private val defaultLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private fun text(key: StringResource) = kotlinx.coroutines.runBlocking { getString(key) }

    private fun topic(id: Long, title: String, parent: Long? = null) = Topic(id, if (parent == null) 1 else null, parent, title, null, 1)

    private fun leaf(id: Long, title: String, finished: Boolean, tasks: Int) =
        PracticeNode(topic(id, title, 10), emptyList(), finished, if (finished) tasks else 0, if (finished && tasks > 0) listOf(id) else emptyList())

    /** "Nyelvtan" with "Szóképek" (finished, 3 tasks) and "Mondattan" (not finished). */
    private val grammar = PracticeNode(
        topic(10, "Nyelvtan"),
        listOf(leaf(11, "Szóképek", finished = true, tasks = 3), leaf(12, "Mondattan", finished = false, tasks = 0)),
        finished = false, taskCount = 3, leafIds = listOf(11),
    )
    private val subjects = listOf(PracticeSubject(Subject(1, "HU_MJL", "Magyar nyelv és irodalom", "Maďarčina", Track.HU, null), listOf(grammar)))

    private class Calls {
        val toggled = mutableListOf<List<Long>>()
        val foldedSubjects = mutableListOf<Long>()
        val lengths = mutableListOf<RoundLength>()
        var started = 0
        var back = 0
    }

    private fun ComposeUiTest.showPicker(state: PickerUiState, calls: Calls = Calls()) = setContent {
        AppTheme {
            PickerContent(
                state,
                PickerActions(
                    onBack = { calls.back++ },
                    onToggle = { calls.toggled += it },
                    onFoldSubject = { calls.foldedSubjects += it },
                    onLength = { calls.lengths += it },
                    onStart = { calls.started++ },
                ),
            )
        }
    }

    @Test
    fun theTabOpensThePickerWhenThereIsSomethingToPractice() = runDesktopComposeUiTest(412, 915) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        var opened = 0
        setContent { AppTheme { PracticeContent(PracticeUiState.Ready(canPractice = true), onOpenPicker = { opened++ }, onRetry = {}) } }

        onNodeWithText(text(Res.string.practice_free_text)).assertIsDisplayed()
        onNodeWithText(text(Res.string.practice_soon_text)).assertIsDisplayed()
        onNodeWithText(text(Res.string.practice_free_start)).assertIsEnabled().performClick()
        assertEquals(1, opened)
    }

    @Test
    fun theMistakeCardStartsARoundOrOpensThePicker() = runDesktopComposeUiTest(412, 1200) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        var all = 0
        var picker = 0
        setContent {
            AppTheme {
                PracticeContent(
                    PracticeUiState.Ready(canPractice = true, mistakes = 14), onOpenPicker = {}, onRetry = {},
                    onOpenMistakePicker = { picker++ }, onStartAllMistakes = { all++ },
                )
            }
        }

        onNodeWithText("Összesen 14 feladatot rontottál el. Gyakorold át őket.").assertIsDisplayed()
        onNodeWithText("Összes hiba (14)").assertIsEnabled().performClick()
        onNodeWithText("Tantárgy vagy téma").performClick()
        assertEquals(1, all)
        assertEquals(1, picker)
    }

    @Test
    fun withoutMistakesTheMistakeCardIsOff() = runDesktopComposeUiTest(412, 1200) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        setContent { AppTheme { PracticeContent(PracticeUiState.Ready(canPractice = true, mistakes = 0), onOpenPicker = {}, onRetry = {}) } }

        onNodeWithText("Itt jelennek meg a feladatok, amiket elrontottál. Egyelőre egy sincs.").assertIsDisplayed()
        onNodeWithText("Összes hiba (0)").assertIsNotEnabled()
        onNodeWithText("Tantárgy vagy téma").assertIsNotEnabled()
    }

    @Test
    fun thePickerInMistakeModeCountsMistakes() = runDesktopComposeUiTest(412, 915) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        // "Szóképek" with 3 mistakes, "Mondattan" with none
        val mistakeGrammar = grammar.copy(children = listOf(
            PracticeNode(topic(11, "Szóképek", 10), emptyList(), true, 3, listOf(11)),
            PracticeNode(topic(12, "Mondattan", 10), emptyList(), true, 0, emptyList()),
        ))
        val tree = listOf(subjects.single().copy(parts = listOf(mistakeGrammar)))
        showPicker(PickerUiState.Ready(tree, mistakes = true, openSubjects = setOf(1)))

        onNodeWithText("Mit gyakoroljunk a hibákból?").assertIsDisplayed()
        // the subject (all its mistakes) and the topic both say it
        onAllNodesWithText("3 hibás feladat").assertCountEquals(2)
        onNodeWithText("Nincs hiba").assertIsDisplayed()
    }

    @Test
    fun slovakMistakesUseTheirPluralForms() = runDesktopComposeUiTest(412, 1200) {
        Locale.setDefault(Locale.forLanguageTag("sk"))
        setContent { AppTheme { PracticeContent(PracticeUiState.Ready(canPractice = true, mistakes = 3), onOpenPicker = {}, onRetry = {}) } }

        onNodeWithText("Spolu si pokazil 3 úlohy. Precvič si ich.").assertIsDisplayed()
    }

    @Test
    fun theTabSaysToFinishATopicFirst() = runDesktopComposeUiTest(412, 915) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        setContent { AppTheme { PracticeContent(PracticeUiState.Ready(canPractice = false), onOpenPicker = {}, onRetry = {}) } }

        onNodeWithText(text(Res.string.practice_free_none)).assertIsDisplayed()
        onNodeWithText(text(Res.string.practice_free_start)).assertIsNotEnabled()
    }

    @Test
    fun aClosedSubjectShowsItsCountButNotItsTopics() = runDesktopComposeUiTest(412, 915) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val calls = Calls()
        showPicker(PickerUiState.Ready(subjects), calls)

        onNodeWithText("1 / 2 téma kész").assertIsDisplayed()
        onNodeWithText("Szóképek").assertDoesNotExist()
        // a tap on the name opens the card
        onNodeWithText("Magyar nyelv és irodalom").performClick()
        assertEquals(listOf(1L), calls.foldedSubjects)
        onNodeWithText(text(Res.string.picker_pick_hint)).assertIsDisplayed()
    }

    @Test
    fun anOpenSubjectShowsItsPartsAndTopicsAndTicksThem() = runDesktopComposeUiTest(412, 915) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val calls = Calls()
        showPicker(PickerUiState.Ready(subjects, openSubjects = setOf(1)), calls)

        onNodeWithText("NYELVTAN").assertIsDisplayed()
        onNodeWithText("3 feladat").assertIsDisplayed()
        onNodeWithText(text(Res.string.picker_not_finished)).assertIsDisplayed()
        onNodeWithText("Szóképek").performClick()
        // an unfinished topic cannot be ticked
        onNodeWithText("Mondattan").performClick()
        // the tick of the part chooses its finished topics
        onNodeWithContentDescription("Nyelvtan kiválasztása").performClick()
        assertEquals(listOf(listOf(11L), listOf(11L)), calls.toggled)
    }

    @Test
    fun theFooterCountsTheChoiceAndStartsTheRound() = runDesktopComposeUiTest(412, 915) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        val calls = Calls()
        showPicker(PickerUiState.Ready(subjects, selected = setOf(11), poolSize = 3, length = RoundLength.TWENTY), calls)

        onNodeWithText("1 téma · 3 feladat").assertIsDisplayed()
        onNodeWithText("Indítás · 3 kérdés").assertIsEnabled().performClick()
        onNodeWithText("50").performClick()
        assertEquals(1, calls.started)
        assertEquals(listOf(RoundLength.FIFTY), calls.lengths)
    }

    @Test
    fun theCustomLengthHasItsFieldAndSaysWhenTheNumberIsWrong() = runDesktopComposeUiTest(412, 915) {
        Locale.setDefault(Locale.forLanguageTag("hu"))
        showPicker(PickerUiState.Ready(subjects, selected = setOf(11), poolSize = 3, length = RoundLength.CUSTOM, customText = "0"))

        onNodeWithText(text(Res.string.picker_custom)).assertIsDisplayed()
        onNodeWithText(text(Res.string.picker_custom_label)).assertIsDisplayed()
        onNodeWithText(text(Res.string.picker_custom_invalid)).assertIsDisplayed()
        onNodeWithText(text(Res.string.practice_free_start)).assertIsNotEnabled()
    }

    @Test
    fun slovakCountsUseTheirPluralForms() = runDesktopComposeUiTest(412, 915) {
        Locale.setDefault(Locale.forLanguageTag("sk"))
        showPicker(PickerUiState.Ready(subjects, openSubjects = setOf(1), selected = setOf(11), poolSize = 3))

        onNodeWithText("3 úlohy").assertIsDisplayed()
        onNodeWithText("1 téma · 3 úlohy").assertIsDisplayed()
        onNodeWithText("Začať · 3 otázky").assertIsDisplayed()
    }

    @Test
    fun aGroupRowTicksWithItsBoxAndFoldsWithTheRest() = runDesktopComposeUiTest(412, 915) {
        var ticked = 0
        var folded = 0
        setContent {
            AppTheme {
                AppCheckRow(
                    title = "Nyelvtan", state = ToggleableState.Indeterminate, onToggle = { ticked++ }, checkLabel = "Nyelvtan kiválasztása",
                    style = CheckRowStyle.GROUP, expanded = true, onExpand = { folded++ }, expandLabel = "Lenyitás", collapseLabel = "Összecsukás",
                )
            }
        }

        onNodeWithContentDescription("Nyelvtan kiválasztása").performClick()
        onNodeWithText("NYELVTAN").performClick()
        assertEquals(1, ticked)
        assertEquals(1, folded)
    }

    @Test
    fun aDisabledItemCannotBeTicked() = runDesktopComposeUiTest(412, 915) {
        var ticked = 0
        setContent {
            AppTheme {
                AppCheckRow(title = "Mondattan", state = ToggleableState.Off, onToggle = { ticked++ }, checkLabel = "Mondattan kiválasztása", subtitle = "Még nincs kész", enabled = false)
            }
        }

        onNodeWithContentDescription("Mondattan kiválasztása").assertIsNotEnabled().performClick()
        assertEquals(0, ticked)
    }
}
