package com.madovygabor.maturimate.features.learning.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.learning.domain.Answer
import com.madovygabor.maturimate.features.learning.domain.AnswerChecker
import com.madovygabor.maturimate.features.learning.domain.AnswerHint
import com.madovygabor.maturimate.features.learning.domain.AnswerResult
import com.madovygabor.maturimate.features.learning.domain.ContentRepository
import com.madovygabor.maturimate.features.learning.domain.model.AnswerMode
import com.madovygabor.maturimate.features.learning.domain.model.LearningUnit
import com.madovygabor.maturimate.features.learning.domain.model.Task
import com.madovygabor.maturimate.features.learning.domain.model.TaskFormat
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

sealed interface TestUiState {
    data object Loading : TestUiState
    data object Failed : TestUiState

    /** The test has no task. */
    data object Empty : TestUiState

    /**
     * One task of the round. The answer is kept per slot (a gap, a pair, a position): [choices] holds the chosen
     * option label of each slot, [texts] the written text. A multiple-choice or true-or-false task and a short
     * answer use slot 1. [activeSlot] is the pair of a matching task that gets the next answer. [result] is null
     * until the answer is checked.
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
}

/**
 * Runs the tasks of a test unit one after the other: choose or write, check, see the feedback, go on. A round is
 * the first `roundSize` active core tasks of the unit. The result of the round and the progress are not kept yet
 * (step 7.5); after the last task the screen closes.
 */
class TestSessionViewModel(
    private val unitId: Long,
    private val content: ContentRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<TestUiState>(TestUiState.Loading)
    val state: StateFlow<TestUiState> = _state.asStateFlow()

    private val _finished = Channel<Unit>(Channel.BUFFERED)

    /** Emits once after the last task. */
    val finished: Flow<Unit> = _finished.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = TestUiState.Loading
            _state.value = read()
        }
    }

    private suspend fun read(): TestUiState {
        val unit = (content.unit(unitId) as? OperationResult.Success)?.data as? LearningUnit.Practice ?: return TestUiState.Failed
        val unitTasks = (content.unitTasks(unitId) as? OperationResult.Success)?.data ?: return TestUiState.Failed
        val tasks = mutableListOf<Task>()
        for (entry in unitTasks.filter { it.isCore && it.isActive }) {
            if (tasks.size == unit.roundSize) break
            val task = (content.task(entry.taskId) as? OperationResult.Success)?.data ?: return TestUiState.Failed
            tasks += task
        }
        return if (tasks.isEmpty()) TestUiState.Empty else fresh(TestUiState.Ready(unit.title, tasks, index = 0))
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

    /** Checks the answer; it is the same for every format (`AnswerChecker`). */
    fun check() = update { if (it.canCheck) it.copy(result = AnswerChecker.check(it.task, it.answer)) else it }

    /** The next task, or the end of the round after the last one. */
    fun next() {
        val state = _state.value as? TestUiState.Ready ?: return
        if (!state.checked) return
        if (state.isLast) {
            _finished.trySend(Unit)
        } else {
            _state.value = fresh(state.copy(index = state.index + 1, choices = emptyMap(), texts = emptyMap(), hintShown = false, result = null))
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
