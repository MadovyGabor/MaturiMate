package com.madovygabor.maturimate.features.learning.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madovygabor.maturimate.features.learning.domain.Answer
import com.madovygabor.maturimate.features.learning.domain.AnswerChecker
import com.madovygabor.maturimate.features.learning.domain.AnswerHint
import com.madovygabor.maturimate.features.learning.domain.AnswerResult
import com.madovygabor.maturimate.features.learning.domain.ContentRepository
import com.madovygabor.maturimate.features.learning.domain.RoundKind
import com.madovygabor.maturimate.features.learning.domain.RoundSource
import com.madovygabor.maturimate.features.learning.domain.RoundStart
import com.madovygabor.maturimate.features.learning.domain.RoundEntry
import com.madovygabor.maturimate.features.learning.domain.TestRecorder
import com.madovygabor.maturimate.features.learning.domain.UnitRoundSource
import com.madovygabor.maturimate.features.learning.domain.model.AnswerMode
import com.madovygabor.maturimate.features.learning.domain.model.Task
import com.madovygabor.maturimate.features.learning.domain.model.TaskFormat
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random
import kotlin.time.ExperimentalTime

sealed interface TestUiState {
    data object Loading : TestUiState
    data object Failed : TestUiState

    /** The test has no task. */
    data object Empty : TestUiState

    /**
     * One task of the round. The answer is kept per slot (a gap, a pair, a position): [choices] holds the chosen
     * option label of each slot, [texts] the written text. A multiple-choice or true-or-false task and a short
     * answer use slot 1. [activeSlot] is the pair of a matching task that gets the next answer. [result] is null
     * until the answer is checked; [entries] are the tasks of the round answered so far.
     */
    data class Ready(
        val title: String,
        val tasks: List<Task>,
        val index: Int,
        val choices: Map<Int, String> = emptyMap(),
        val texts: Map<Int, String> = emptyMap(),
        val activeSlot: Int? = null,
        val hintShown: Boolean = false,
        val result: AnswerResult? = null,
        val entries: List<RoundEntry> = emptyList(),
    ) : TestUiState {
        val task: Task get() = tasks[index]
        val isLast: Boolean get() = index == tasks.lastIndex
        val checked: Boolean get() = result != null
        val slots: List<Int> get() = AnswerChecker.slots(task)

        /** The chosen option of a multiple-choice or true-or-false task. */
        val selected: String? get() = choices[1]

        /** The written answer of a short answer. */
        val typed: String get() = texts[1].orEmpty()

        /** The answer can be checked once every slot is filled. */
        val canCheck: Boolean
            get() = !checked && slots.isNotEmpty() && slots.all {
                if (task.format.answerMode == AnswerMode.CHOICE) it in choices else texts[it].orEmpty().isNotBlank()
            }

        /** The first letter and the length of the answer, for a short answer. */
        val hint: AnswerHint? get() = if (task.format == TaskFormat.SHORT_ANSWER) AnswerChecker.hint(task) else null

        /** The option the student chose, for the "why not this" of a wrong answer. */
        val chosenOption get() = task.options.firstOrNull { it.label == selected }

        val answer: Answer
            get() = if (task.format.answerMode == AnswerMode.CHOICE) Answer.Choice(choices) else Answer.Typed(texts)
    }

    /** The end of a round: how every task went. [kind] decides what the result says and offers. */
    data class Result(val title: String, val entries: List<RoundEntry>, val kind: RoundKind = RoundKind.TEST) : TestUiState {
        val correctCount: Int get() = entries.count { it.correct }
        val total: Int get() = entries.size
        val earnedPoints: Int get() = entries.sumOf { it.earnedPoints }
        val maxPoints: Int get() = entries.sumOf { it.maxPoints }

        /** The round is passed: the test is done and the next step of the path opens. Practice has no passing. */
        val passed: Boolean get() = TestRecorder.passed(entries)
    }
}

/**
 * Runs a round: choose or write, check, see the feedback, go on, and at the end the result. The same for a test of
 * the path and for practice; the [source] decides which tasks come, where the answers go and how the round ends.
 *
 * * A test ([UnitRoundSource]): the required tasks of the unit and random ones to fill the round; at the end the
 *   unit is done when the round is passed. Leaving in the middle ends the attempt as abandoned.
 * * Practice: random tasks of the chosen topics; no unit is ever done. Ending it in the middle after some answers
 *   finishes the round with those answers and shows their result (practice has no fixed end, decided 2026-11-05).
 * * Saving runs in the background and does not wait for the screen; what was answered always stays recorded.
 */
@OptIn(ExperimentalTime::class)
class TestSessionViewModel(
    private val source: RoundSource,
    private val random: Random = Random.Default,
) : ViewModel() {

    /** A round of the test unit [unitId]. */
    constructor(unitId: Long, content: ContentRepository, recorder: TestRecorder, random: Random = Random.Default) :
        this(UnitRoundSource(unitId, content, recorder), random)

    private val _state = MutableStateFlow<TestUiState>(TestUiState.Loading)
    val state: StateFlow<TestUiState> = _state.asStateFlow()

    private var attemptId: Long? = null

    /** The last background save; the next one and the end of the round wait for it, so the order is kept. */
    private var saving: Job? = null

    init {
        load()
    }

    /** Starts a round (also again after the result). */
    fun load() {
        viewModelScope.launch {
            _state.value = TestUiState.Loading
            _state.value = read()
        }
    }

    private suspend fun read(): TestUiState = when (val start = source.start(random)) {
        RoundStart.Failed -> TestUiState.Failed
        RoundStart.Empty -> TestUiState.Empty
        is RoundStart.Ready -> {
            attemptId = start.attemptId
            saving = null
            fresh(TestUiState.Ready(start.title, start.tasks, index = 0))
        }
    }

    /** A multiple-choice or true-or-false answer. */
    fun select(label: String) = choose(1, label)

    /** The written answer of a short answer. */
    fun type(text: String) = typeIn(1, text)

    /** The option [label] for [slot]. */
    fun choose(slot: Int, label: String) = update { if (!it.checked) it.copy(choices = it.choices + (slot to label)) else it }

    /** The text written in the gap [slot]. */
    fun typeIn(slot: Int, text: String) = update { if (!it.checked) it.copy(texts = it.texts + (slot to text)) else it }

    /** Empties a gap filled from a list. */
    fun clear(slot: Int) = update { if (!it.checked) it.copy(choices = it.choices - slot) else it }

    /** Makes a pair of a matching task the one that gets the next answer. */
    fun activate(slot: Int) = update { if (!it.checked && it.task.format == TaskFormat.MATCHING) it.copy(activeSlot = slot) else it }

    /**
     * Picks the option [label] where the format puts it: a matching task gives it to the active pair (and the next
     * pair without an answer becomes active), an ordering task puts it at the next position or, picked again, takes it
     * back and closes the gap in the order, a gap task fills the first empty gap.
     */
    fun pick(label: String) = update { state ->
        if (state.checked) return@update state
        when (state.task.format) {
            TaskFormat.MATCHING -> {
                val slot = state.activeSlot ?: return@update state
                val choices = state.choices + (slot to label)
                state.copy(choices = choices, activeSlot = state.slots.firstOrNull { it !in choices })
            }
            TaskFormat.ORDERING -> {
                val order = state.choices.entries.sortedBy { it.key }.map { it.value }
                val next = if (label in order) order - label else if (order.size < state.slots.size) order + label else order
                state.copy(choices = next.mapIndexed { index, value -> index + 1 to value }.toMap())
            }
            TaskFormat.GAP_CHOICE -> {
                if (label in state.choices.values) return@update state
                val slot = state.slots.firstOrNull { it !in state.choices } ?: return@update state
                state.copy(choices = state.choices + (slot to label))
            }
            else -> state.copy(choices = mapOf(1 to label))
        }
    }

    fun showHint() = update { if (!it.checked && it.hint != null) it.copy(hintShown = true) else it }

    /** Checks the answer, which is the same for every format (`AnswerChecker`), and records it. */
    fun check() = update { state ->
        if (!state.canCheck) return@update state
        val answer = state.answer
        val result = AnswerChecker.check(state.task, answer)
        val entry = RoundEntry(state.task, answer, result, TestRecorder.earnedPoints(state.task, result), state.task.maxPoints)
        val attempt = attemptId
        if (attempt != null) save { source.answer(attempt, entry.task, answer, result) }
        state.copy(result = result, entries = state.entries + entry)
    }

    /** The next task, or the result after the last one. */
    fun next() {
        val state = _state.value as? TestUiState.Ready ?: return
        if (!state.checked) return
        if (state.isLast) {
            val attempt = attemptId
            if (attempt != null) save { source.finish(attempt, state.entries) }
            _state.value = TestUiState.Result(state.title, state.entries, source.kind)
        } else {
            _state.value = fresh(state.copy(index = state.index + 1, choices = emptyMap(), texts = emptyMap(), hintShown = false, result = null))
        }
    }

    /** The student leaves the round in the middle: the attempt ends as abandoned. */
    fun leave() {
        val state = _state.value as? TestUiState.Ready ?: return
        val attempt = attemptId ?: return
        save { source.abandon(attempt, state.entries) }
    }

    /**
     * The close button. Returns whether the screen should close. A practice round with answers is not left but
     * finished with what was answered, and its result is shown; anything else is left (see [leave]) and closes.
     */
    fun close(): Boolean {
        val state = _state.value as? TestUiState.Ready
        val attempt = attemptId
        if (source.kind == RoundKind.PRACTICE && state != null && attempt != null && state.entries.isNotEmpty()) {
            save { source.finish(attempt, state.entries) }
            _state.value = TestUiState.Result(state.title, state.entries, source.kind)
            return false
        }
        leave()
        return true
    }

    /**
     * Saves in the background, after the earlier save. It keeps going when the screen is closed at once (the view
     * model is then cleared), so a finished or abandoned round is never lost.
     */
    private fun save(block: suspend () -> Unit) {
        val previous = saving
        saving = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                previous?.join()
                block()
            }
        }
    }

    /** A task starts with nothing answered; in a matching task the first pair is the active one. */
    private fun fresh(state: TestUiState.Ready) =
        state.copy(activeSlot = if (state.task.format == TaskFormat.MATCHING) state.slots.firstOrNull() else null)

    private fun update(change: (TestUiState.Ready) -> TestUiState.Ready) {
        val state = _state.value as? TestUiState.Ready ?: return
        _state.value = change(state)
    }
}
