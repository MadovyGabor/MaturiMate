package com.madovygabor.maturimate.features.practice.presentation

import androidx.compose.ui.state.ToggleableState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.practice.domain.PracticeCatalog
import com.madovygabor.maturimate.features.practice.domain.PracticeNode
import com.madovygabor.maturimate.features.practice.domain.PracticeSubject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.madovygabor.maturimate.features.practice.domain.PracticeMode
import com.madovygabor.maturimate.features.practice.domain.ScoreSummary
import com.madovygabor.maturimate.features.practice.domain.TopicScores

sealed interface PracticeUiState {
    data object Loading : PracticeUiState
    data object Failed : PracticeUiState

    /**
     * [canPractice]: at least one finished topic has tasks, so the free practice can start. [mistakes]: how many
     * tasks were answered wrong last (the mistake practice).
     */
    data class Ready(val canPractice: Boolean, val mistakes: Int = 0, val scores: ScoreSummary = ScoreSummary.EMPTY) : PracticeUiState
}

/**
 * The practice tab: whether the free practice can start, the number of mistakes, the weak and strong topics. It reads
 * again whenever the tab is shown, so a topic finished a minute ago counts.
 */
class PracticeViewModel(private val catalog: PracticeCatalog, private val topicScores: TopicScores? = null) : ViewModel() {

    private val _state = MutableStateFlow<PracticeUiState>(PracticeUiState.Loading)
    val state: StateFlow<PracticeUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val shown = _state.value
            val mistakes = (catalog.allMistakes() as? OperationResult.Success)?.data?.size ?: 0
            val scores = (topicScores?.load() as? OperationResult.Success)?.data ?: ScoreSummary.EMPTY
            _state.value = when (val result = catalog.subjects()) {
                is OperationResult.Success -> PracticeUiState.Ready(result.data.any { subject -> subject.parts.any { it.leafIds.isNotEmpty() } }, mistakes, scores)
                // keep what is shown when a later read fails
                is OperationResult.Error -> if (shown is PracticeUiState.Ready) shown else PracticeUiState.Failed
            }
        }
    }
}

/** The lengths of a round the picker offers; [CUSTOM] takes the number the student types. */
enum class RoundLength(val count: Int?) { TEN(10), TWENTY(20), FIFTY(50), CUSTOM(null) }

/** What starts a round: the finished topics chosen and the number of questions. */
data class PracticeStart(val topicIds: List<Long>, val count: Int)

sealed interface PickerUiState {
    data object Loading : PickerUiState
    data object Failed : PickerUiState

    /**
     * The picker. [selected] are the ids of the chosen finished topics; [openSubjects] the subject cards that are
     * open (all closed at first), [closedGroups] the groups (big parts) that are closed (all open at first).
     * [poolSize] is the exact number of different tasks of the selection.
     */
    data class Ready(
        val subjects: List<PracticeSubject>,
        /** The picker of the mistake practice: the topics with mistakes, counted in mistakes. */
        val mistakes: Boolean = false,
        val selected: Set<Long> = emptySet(),
        val openSubjects: Set<Long> = emptySet(),
        val closedGroups: Set<Long> = emptySet(),
        val length: RoundLength = RoundLength.TEN,
        val customText: String = "",
        val poolSize: Int = 0,
    ) : PickerUiState {

        /** The typed number when it is a whole number from 1 to [MAX_CUSTOM]. */
        val customCount: Int? get() = customText.toIntOrNull()?.takeIf { it in 1..MAX_CUSTOM }

        /** The custom field holds something that is not a usable number. */
        val customInvalid: Boolean get() = length == RoundLength.CUSTOM && customCount == null

        /** The length the student asked for (the chip, or the typed number); null when the typed number is not usable. */
        val wanted: Int? get() = if (length == RoundLength.CUSTOM) customCount else length.count

        /** The number of questions the round will have: the chosen length, at most the tasks of the selection. */
        val questions: Int get() = minOf(wanted ?: 0, poolSize)

        val canStart: Boolean get() = selected.isNotEmpty() && questions > 0

        /** The tick box of a subject or a group: on when all its topics are chosen, partly when some are. */
        fun stateOf(leafIds: List<Long>): ToggleableState = when {
            leafIds.isEmpty() -> ToggleableState.Off
            leafIds.all { it in selected } -> ToggleableState.On
            leafIds.any { it in selected } -> ToggleableState.Indeterminate
            else -> ToggleableState.Off
        }

        /** How many of [leafIds] are chosen, for the "x kiválasztva" tag. */
        fun chosen(leafIds: List<Long>): Int = leafIds.count { it in selected }

        /** The start of the round, when it can start. */
        fun start(): PracticeStart? = if (canStart) PracticeStart(selected.toList(), questions) else null
    }

    companion object {
        /** The longest round that can be typed in. */
        const val MAX_CUSTOM = 999

        /** The most tasks "Összes hiba" asks at once; the picker allows other lengths (decided 2026-11-05). */
        const val ALL_MISTAKES_ROUND = 20

        /** The most tasks the practice of a weak topic asks: its mistakes, filled up with its other tasks (decided 2026-11-05). */
        const val TOPIC_ROUND = 30
    }
}

/**
 * The practice picker: the subjects with their big parts and topics; the topics that can be practiced can be ticked
 * one by one or a whole part or subject at once; then the length of the round. In [PracticeMode.FREE] those are the
 * finished topics, in [PracticeMode.MISTAKES] the topics with a mistake. Every rule is here; the screen only draws.
 */
class PracticePickerViewModel(
    private val catalog: PracticeCatalog,
    private val mode: PracticeMode = PracticeMode.FREE,
) : ViewModel() {

    private val _state = MutableStateFlow<PickerUiState>(PickerUiState.Loading)
    val state: StateFlow<PickerUiState> = _state.asStateFlow()

    /** The count of the last selection; a newer selection cancels it, so an old count never wins. */
    private var counting: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val shown = _state.value as? PickerUiState.Ready
            when (val result = if (mode == PracticeMode.MISTAKES) catalog.mistakes() else catalog.subjects()) {
                is OperationResult.Success -> {
                    // a chosen topic that can no longer be chosen falls out; folds and length stay
                    val choosable = result.data.flatMap { subject -> subject.parts.flatMap { it.leafIds } }.toSet()
                    val ready = shown?.copy(subjects = result.data, selected = shown.selected intersect choosable)
                        ?: PickerUiState.Ready(result.data, mistakes = mode == PracticeMode.MISTAKES)
                    _state.value = ready
                    count(ready.selected)
                }
                is OperationResult.Error -> if (shown == null) _state.value = PickerUiState.Failed
            }
        }
    }

    /**
     * Ticks or clears the finished topics [leafIds] (one topic, or every finished topic of a part or a subject): when
     * all of them are chosen they are cleared, otherwise all are chosen.
     */
    fun toggle(leafIds: List<Long>) {
        if (leafIds.isEmpty()) return
        val ready = _state.value as? PickerUiState.Ready ?: return
        val selected = if (leafIds.all { it in ready.selected }) ready.selected - leafIds.toSet() else ready.selected + leafIds
        _state.value = ready.copy(selected = selected)
        count(selected)
    }

    /** Opens or closes a subject card. */
    fun foldSubject(subjectId: Long) = update { it.copy(openSubjects = it.openSubjects.flip(subjectId)) }

    /** Opens or closes a big part (or any topic with topics below it). */
    fun foldGroup(topicId: Long) = update { it.copy(closedGroups = it.closedGroups.flip(topicId)) }

    fun selectLength(length: RoundLength) = update {
        // the custom field starts with a suggestion, so the button works at once
        it.copy(length = length, customText = if (length == RoundLength.CUSTOM && it.customText.isEmpty()) DEFAULT_CUSTOM else it.customText)
    }

    /** The custom number: digits only, at most three. */
    fun typeCustom(text: String) = update { it.copy(customText = text.filter(Char::isDigit).take(3)) }

    private fun count(selected: Set<Long>) {
        counting?.cancel()
        if (selected.isEmpty()) {
            update { it.copy(poolSize = 0) }
            return
        }
        counting = viewModelScope.launch {
            val pool = if (mode == PracticeMode.MISTAKES) catalog.mistakePool(selected) else catalog.pool(selected)
            val size = (pool as? OperationResult.Success)?.data?.size ?: return@launch
            update { if (it.selected == selected) it.copy(poolSize = size) else it }
        }
    }

    private fun update(change: (PickerUiState.Ready) -> PickerUiState.Ready) =
        _state.update { if (it is PickerUiState.Ready) change(it) else it }

    private fun <T> Set<T>.flip(item: T) = if (item in this) this - item else this + item

    private companion object {
        const val DEFAULT_CUSTOM = "15"
    }
}

/** Every node of the tree below [nodes], parents before their children (for tests and for looking a node up). */
internal fun allNodes(nodes: List<PracticeNode>): List<PracticeNode> = nodes.flatMap { listOf(it) + allNodes(it.children) }
