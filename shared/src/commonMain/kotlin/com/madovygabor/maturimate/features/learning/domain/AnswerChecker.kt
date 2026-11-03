package com.madovygabor.maturimate.features.learning.domain

import com.madovygabor.maturimate.features.learning.domain.model.AnswerMode
import com.madovygabor.maturimate.features.learning.domain.model.Task

/** What the student answered, per answer slot (a slot is a key of the task: a gap, a pair, a position). */
sealed interface Answer {
    /** The label of the chosen `ANSWER` option for each slot; for the choice formats. */
    data class Choice(val labels: Map<Int, String>) : Answer

    /** The typed text for each slot; for the typed formats. */
    data class Typed(val texts: Map<Int, String>) : Answer
}

/** Whether the answer is right: [slots] says it for every slot, [correct] is true when all of them are. */
data class AnswerResult(val slots: Map<Int, Boolean>) {
    val correct: Boolean get() = slots.isNotEmpty() && slots.values.all { it }
}

/** The first letter and the length of the answer, for the hint of a typed task. */
data class AnswerHint(val firstLetter: Char, val length: Int)

/**
 * Decides whether an answer is right. It works for every task format:
 *
 * * Choice formats (multiple choice, true or false, matching, ordering, gaps from a list): the slot is
 *   right when the chosen option is the one of the key.
 * * Typed formats (short answer, typed gaps): the slot is right when the text equals an accepted answer.
 *   Spaces at the ends and doubled spaces do not count, and so does the case (unless the accepted answer is
 *   case sensitive); the accents do count, because a missing accent is a mistake in a language task.
 *
 * An answer of the wrong kind, or a slot left empty, is wrong.
 */
object AnswerChecker {

    fun check(task: Task, answer: Answer): AnswerResult = when (task.format.answerMode) {
        AnswerMode.CHOICE -> {
            val labels = (answer as? Answer.Choice)?.labels.orEmpty()
            AnswerResult(task.key.associate { key -> key.slot to (labels[key.slot] == key.optionLabel) })
        }
        AnswerMode.TEXT -> {
            val texts = (answer as? Answer.Typed)?.texts.orEmpty()
            val slots = task.acceptedAnswers.map { it.slot }.distinct().sorted()
            AnswerResult(slots.associateWith { slot ->
                val typed = normalize(texts[slot].orEmpty())
                typed.isNotEmpty() && task.acceptedAnswers.any {
                    it.slot == slot && typed.equals(normalize(it.text), ignoreCase = !it.caseSensitive)
                }
            })
        }
    }

    /**
     * The slots of a task, in order: the keys of a choice task, the gaps that have an accepted answer for a typed
     * one. A task is answered when every slot is.
     */
    fun slots(task: Task): List<Int> = when (task.format.answerMode) {
        AnswerMode.CHOICE -> task.key.map { it.slot }.distinct().sorted()
        AnswerMode.TEXT -> task.acceptedAnswers.map { it.slot }.distinct().sorted()
    }

    /** The first letter and the length of the first accepted answer of [slot]; null when there is none. */
    fun hint(task: Task, slot: Int = 1): AnswerHint? =
        task.acceptedAnswers.firstOrNull { it.slot == slot }?.text?.let { normalize(it) }
            ?.takeIf { it.isNotEmpty() }
            ?.let { AnswerHint(it.first(), it.length) }

    private fun normalize(text: String): String = text.trim().replace(Regex("""\s+"""), " ")
}
