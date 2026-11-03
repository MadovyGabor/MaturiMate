package com.madovygabor.maturimate.features.learning.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.learning.domain.Answer
import com.madovygabor.maturimate.features.learning.domain.AnswerChecker
import com.madovygabor.maturimate.features.learning.domain.AnswerHint
import com.madovygabor.maturimate.features.learning.domain.AnswerResult
import com.madovygabor.maturimate.features.learning.domain.ContentRepository
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

/** The task formats that have a screen so far; a test leaves the others out until they do (step 7.4). */
val SUPPORTED_FORMATS = setOf(TaskFormat.MC_SINGLE, TaskFormat.TRUE_FALSE, TaskFormat.SHORT_ANSWER)

sealed interface TestUiState {
    data object Loading : TestUiState
    data object Failed : TestUiState

    /** The test has no task the app can show yet. */
    data object Empty : TestUiState

    /**
     * One task of the round. [selected] is the chosen option label and [typed] the written answer; [result] is
     * null until the answer is checked.
     */
    data class Ready(
        val title: String,
        val tasks: List<Task>,
        val index: Int,
        val selected: String? = null,
        val typed: String = "",
        val hintShown: Boolean = false,
        val result: AnswerResult? = null,
    ) : TestUiState {
        val task: Task get() = tasks[index]
        val isLast: Boolean get() = index == tasks.lastIndex
        val checked: Boolean get() = result != null

        /** The answer can be checked once something is chosen or written. */
        val canCheck: Boolean
            get() = !checked && when (task.format) {
                TaskFormat.SHORT_ANSWER -> typed.isNotBlank()
                else -> selected != null
            }

        /** The first letter and the length of the answer, for a short answer. */
        val hint: AnswerHint? get() = if (task.format == TaskFormat.SHORT_ANSWER) AnswerChecker.hint(task) else null

        /** The option the student chose, for the "why not this" of a wrong answer. */
        val chosenOption get() = task.options.firstOrNull { it.label == selected }

        val answer: Answer
            get() = if (task.format == TaskFormat.SHORT_ANSWER) Answer.Typed(mapOf(1 to typed)) else Answer.Choice(selected?.let { mapOf(1 to it) }.orEmpty())
    }
}

/**
 * Runs the tasks of a test unit one after the other: choose or write, check, see the feedback, go on. A round is
 * the first `roundSize` active core tasks of the unit whose format has a screen. The result of the round and the
 * progress are not kept yet (step 7.5); after the last task the screen closes.
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
            if (task.format in SUPPORTED_FORMATS) tasks += task
        }
        return if (tasks.isEmpty()) TestUiState.Empty else TestUiState.Ready(unit.title, tasks, index = 0)
    }

    fun select(label: String) = update { if (!it.checked) it.copy(selected = label) else it }

    fun type(text: String) = update { if (!it.checked) it.copy(typed = text) else it }

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
            _state.value = state.copy(index = state.index + 1, selected = null, typed = "", hintShown = false, result = null)
        }
    }

    private fun update(change: (TestUiState.Ready) -> TestUiState.Ready) {
        val state = _state.value as? TestUiState.Ready ?: return
        _state.value = change(state)
    }
}
