package com.madovygabor.maturimate.features.learning.domain

import com.madovygabor.maturimate.features.learning.domain.model.AcceptedAnswer
import com.madovygabor.maturimate.features.learning.domain.model.KeySlot
import com.madovygabor.maturimate.features.learning.domain.model.OptionRole
import com.madovygabor.maturimate.features.learning.domain.model.Task
import com.madovygabor.maturimate.features.learning.domain.model.TaskFormat
import com.madovygabor.maturimate.features.learning.domain.model.TaskOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnswerCheckerTest {

    private fun task(
        format: TaskFormat,
        key: List<KeySlot> = emptyList(),
        accepted: List<AcceptedAnswer> = emptyList(),
    ) = Task(
        id = 1, topicId = 1, format = format, questionText = "?", explanation = null, maxPoints = 1, answerWordLimit = null,
        isActive = true, options = listOf(TaskOption("A", "a", OptionRole.ANSWER)), key = key, acceptedAnswers = accepted, attachments = emptyList(),
    )

    private fun choice(vararg pairs: Pair<Int, String>) = Answer.Choice(mapOf(*pairs))
    private fun typed(vararg pairs: Pair<Int, String>) = Answer.Typed(mapOf(*pairs))

    @Test
    fun multipleChoiceIsRightWhenTheKeyOptionIsChosen() {
        val task = task(TaskFormat.MC_SINGLE, key = listOf(KeySlot(1, "B", null)))
        assertTrue(AnswerChecker.check(task, choice(1 to "B")).correct)
        assertFalse(AnswerChecker.check(task, choice(1 to "A")).correct)
    }

    @Test
    fun trueOrFalseWorksLikeMultipleChoice() {
        val task = task(TaskFormat.TRUE_FALSE, key = listOf(KeySlot(1, "H", null)))
        assertTrue(AnswerChecker.check(task, choice(1 to "H")).correct)
        assertFalse(AnswerChecker.check(task, choice(1 to "I")).correct)
    }

    @Test
    fun matchingNeedsEveryPairAndTellsWhichPairIsWrong() {
        val task = task(TaskFormat.MATCHING, key = listOf(KeySlot(1, "A", "1"), KeySlot(2, "B", "2")))

        assertTrue(AnswerChecker.check(task, choice(1 to "A", 2 to "B")).correct)
        val result = AnswerChecker.check(task, choice(1 to "A", 2 to "A"))
        assertFalse(result.correct)
        assertEquals(mapOf(1 to true, 2 to false), result.slots)
    }

    @Test
    fun orderingNeedsTheRightOptionAtEveryPosition() {
        val task = task(TaskFormat.ORDERING, key = listOf(KeySlot(1, "B", null), KeySlot(2, "D", null), KeySlot(3, "A", null)))

        assertTrue(AnswerChecker.check(task, choice(1 to "B", 2 to "D", 3 to "A")).correct)
        assertFalse(AnswerChecker.check(task, choice(1 to "B", 2 to "A", 3 to "D")).correct)
    }

    @Test
    fun gapsFromAListNeedEveryGapFilledRight() {
        val task = task(TaskFormat.GAP_CHOICE, key = listOf(KeySlot(1, "A", null), KeySlot(2, "B", null)))

        assertTrue(AnswerChecker.check(task, choice(1 to "A", 2 to "B")).correct)
        assertFalse(AnswerChecker.check(task, choice(1 to "A")).correct)
    }

    @Test
    fun aMissingChoiceOrTheWrongKindOfAnswerIsWrong() {
        val task = task(TaskFormat.MC_SINGLE, key = listOf(KeySlot(1, "B", null)))
        assertFalse(AnswerChecker.check(task, choice()).correct)
        assertFalse(AnswerChecker.check(task, typed(1 to "B")).correct)
    }

    private val answers = listOf(AcceptedAnswer(1, "megszemélyesítés", false), AcceptedAnswer(1, "perszonifikáció", false))

    @Test
    fun shortAnswerAcceptsAnyOfTheAcceptedAnswers() {
        val task = task(TaskFormat.SHORT_ANSWER, accepted = answers)
        assertTrue(AnswerChecker.check(task, typed(1 to "megszemélyesítés")).correct)
        assertTrue(AnswerChecker.check(task, typed(1 to "perszonifikáció")).correct)
        assertFalse(AnswerChecker.check(task, typed(1 to "metafora")).correct)
    }

    @Test
    fun caseAndSpacesAroundAndBetweenWordsDoNotCount() {
        val task = task(TaskFormat.SHORT_ANSWER, accepted = listOf(AcceptedAnswer(1, "összehasonlítja őket", false)))
        assertTrue(AnswerChecker.check(task, typed(1 to "  Összehasonlítja   ŐKET ")).correct)
    }

    @Test
    fun aMissingAccentIsAMistake() {
        val task = task(TaskFormat.SHORT_ANSWER, accepted = answers)
        assertFalse(AnswerChecker.check(task, typed(1 to "megszemelyesites")).correct)
    }

    @Test
    fun aCaseSensitiveAnswerNeedsTheSameCase() {
        val task = task(TaskFormat.SHORT_ANSWER, accepted = listOf(AcceptedAnswer(1, "Lucifer", caseSensitive = true)))
        assertTrue(AnswerChecker.check(task, typed(1 to "Lucifer")).correct)
        assertFalse(AnswerChecker.check(task, typed(1 to "lucifer")).correct)
    }

    @Test
    fun typedGapsAreCheckedOneByOne() {
        val task = task(
            TaskFormat.GAP_FILL,
            accepted = listOf(AcceptedAnswer(1, "azonosít", false), AcceptedAnswer(2, "összehasonlít", false), AcceptedAnswer(2, "összehasonlítja őket", false)),
        )
        val result = AnswerChecker.check(task, typed(1 to "azonosít", 2 to "összehasonlítja őket"))
        assertTrue(result.correct)

        val partly = AnswerChecker.check(task, typed(1 to "azonosít", 2 to ""))
        assertFalse(partly.correct)
        assertEquals(mapOf(1 to true, 2 to false), partly.slots)
    }

    @Test
    fun anEmptyAnswerIsNeverRight() {
        assertFalse(AnswerChecker.check(task(TaskFormat.SHORT_ANSWER, accepted = answers), typed(1 to "   ")).correct)
    }

    @Test
    fun hintGivesTheFirstLetterAndTheLength() {
        val hint = AnswerChecker.hint(task(TaskFormat.SHORT_ANSWER, accepted = answers))
        assertEquals(AnswerHint('m', 16), hint)
        assertEquals(null, AnswerChecker.hint(task(TaskFormat.SHORT_ANSWER, accepted = answers), slot = 2))
    }
}
