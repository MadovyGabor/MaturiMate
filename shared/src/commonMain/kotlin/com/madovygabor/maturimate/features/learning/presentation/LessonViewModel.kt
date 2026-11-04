package com.madovygabor.maturimate.features.learning.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.core.presentation.utils.UiText
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.core.presentation.utils.wordCount
import com.madovygabor.maturimate.features.learning.domain.ContentRepository
import com.madovygabor.maturimate.features.learning.domain.ProgressRepository
import com.madovygabor.maturimate.features.learning.domain.StudyClock
import com.madovygabor.maturimate.features.learning.domain.StudyTracker
import com.madovygabor.maturimate.features.learning.domain.model.LearningUnit
import com.madovygabor.maturimate.features.learning.domain.model.LessonPage
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgress
import com.madovygabor.maturimate.features.learning.domain.model.UnitProgressStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.learning_save_error
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** The pages a student reads in a minute; the reading time of a page is its words divided by this. */
private const val WORDS_PER_MINUTE = 200

sealed interface LessonUiState {
    data object Loading : LessonUiState
    data object Failed : LessonUiState

    /** [index] is the page being read, from 0. */
    data class Ready(
        val title: String,
        val pages: List<LessonPage>,
        val index: Int,
        /** The test right after this lesson in its topic; the lesson goes on to it. */
        val nextTest: Long? = null,
    ) : LessonUiState {
        val page: LessonPage get() = pages[index]
        val isFirst: Boolean get() = index == 0
        val isLast: Boolean get() = index == pages.lastIndex

        /** About how long the page takes to read, at least a minute. */
        val minutes: Int
            get() {
                val words = page.blocks.sumOf { wordCount(it.text) }
                return maxOf(1, (words + WORDS_PER_MINUTE - 1) / WORDS_PER_MINUTE)
            }
    }
}

/**
 * Reads a lesson page by page. Finishing the last page saves the lesson as done (`COMPLETED`), keeping the
 * time it was first started when there was one, and then tells the screen to close.
 */
@OptIn(ExperimentalTime::class)
class LessonViewModel(
    private val unitId: Long,
    private val content: ContentRepository,
    private val progress: ProgressRepository,
    private val snackbarController: SnackbarController,
    private val tracker: StudyTracker? = null,
    private val now: () -> Instant = { Clock.System.now() },
) : ViewModel() {

    private val _state = MutableStateFlow<LessonUiState>(LessonUiState.Loading)
    val state: StateFlow<LessonUiState> = _state.asStateFlow()

    private val clock = StudyClock(now = now)

    private val _finished = Channel<Long?>(Channel.BUFFERED)

    /** Emits once when the lesson is finished and saved: the id of the test that follows it, or null. */
    val finished: Flow<Long?> = _finished.receiveAsFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = when (val result = content.unit(unitId)) {
                is OperationResult.Success -> {
                    val unit = result.data
                    if (unit is LearningUnit.Summary && unit.pages.isNotEmpty()) {
                        LessonUiState.Ready(unit.title, unit.pages, index = 0, nextTest = nextTestAfter(unit))
                    } else {
                        LessonUiState.Failed
                    }
                }
                is OperationResult.Error -> LessonUiState.Failed
            }
        }
    }

    /** The test that comes right after the lesson in its topic, if the next step is one. */
    private suspend fun nextTestAfter(unit: LearningUnit): Long? =
        (content.units(unit.topicId) as? OperationResult.Success)?.data
            ?.filter { it.sortOrder > unit.sortOrder }?.minByOrNull { it.sortOrder }
            ?.takeIf { it is LearningUnit.Practice }?.id

    fun next() = move { it.index + 1 }

    fun previous() = move { it.index - 1 }

    /** From the table of contents. */
    fun goTo(index: Int) = move { index }

    private fun move(target: (LessonUiState.Ready) -> Int) {
        val current = _state.value as? LessonUiState.Ready ?: return
        val index = target(current)
        if (index in current.pages.indices) {
            _state.value = current.copy(index = index)
            recordTime()
        }
    }

    /** The time since the last move goes to the daily goal. */
    private fun recordTime() {
        val seconds = clock.lap()
        val tracker = tracker ?: return
        viewModelScope.launch { tracker.record(seconds) }
    }

    /** Saves the lesson as done; the screen closes when [finished] emits. */
    fun finish() {
        if (_state.value !is LessonUiState.Ready) return
        val seconds = clock.lap()
        viewModelScope.launch {
            tracker?.record(seconds)
            val time = now()
            val earlier = (progress.unitProgress() as? OperationResult.Success)?.data?.firstOrNull { it.learningUnitId == unitId }
            val saved = progress.saveUnitProgress(
                UnitProgress(unitId, UnitProgressStatus.COMPLETED, startedAt = earlier?.startedAt ?: time, completedAt = time)
            )
            when (saved) {
                is OperationResult.Success -> _finished.send((_state.value as? LessonUiState.Ready)?.nextTest)
                is OperationResult.Error -> snackbarController.showSnackbar(UiText.StringRes(Res.string.learning_save_error))
            }
        }
    }
}
