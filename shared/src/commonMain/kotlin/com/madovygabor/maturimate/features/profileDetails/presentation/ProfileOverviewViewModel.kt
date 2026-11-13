package com.madovygabor.maturimate.features.profileDetails.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.profileDetails.domain.ActivityHistory
import com.madovygabor.maturimate.features.profileDetails.domain.History
import com.madovygabor.maturimate.features.profileDetails.domain.ProfileOverview
import com.madovygabor.maturimate.features.profileDetails.domain.ProfileSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import com.madovygabor.maturimate.features.sync.domain.SyncEngine
import com.madovygabor.maturimate.features.sync.domain.SyncState
import kotlin.time.Clock
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime

sealed interface ProfileOverviewUiState {
    data object Loading : ProfileOverviewUiState
    data object Failed : ProfileOverviewUiState

    /** [open] holds the subjects whose parts are shown. */
    @OptIn(ExperimentalTime::class)
    data class Ready(val summary: ProfileSummary, val open: Set<Long> = emptySet()) : ProfileOverviewUiState
}

/** The profile tab: the facts about the student. It reads again whenever the screen comes back. */
class ProfileOverviewViewModel(
    private val overview: ProfileOverview,
    syncEngine: SyncEngine? = null,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) : ViewModel() {

    /** The quiet cloud-off sign: the progress waits for more than a day, or the last upload failed for a reason other than "no network". */
    val syncBadge: StateFlow<Boolean> = (syncEngine?.state ?: flowOf(SyncState())).map { it.showBadge(now()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _state = MutableStateFlow<ProfileOverviewUiState>(ProfileOverviewUiState.Loading)
    val state: StateFlow<ProfileOverviewUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val shown = _state.value
            _state.value = when (val result = overview.load()) {
                // the subjects that were open stay open
                is OperationResult.Success -> ProfileOverviewUiState.Ready(result.data, (shown as? ProfileOverviewUiState.Ready)?.open.orEmpty())
                // keep what is shown when a later read fails
                is OperationResult.Error -> if (shown is ProfileOverviewUiState.Ready) shown else ProfileOverviewUiState.Failed
            }
        }
    }

    /** Opens or closes the parts of a subject. */
    fun toggle(subjectId: Long) = _state.update { state ->
        if (state !is ProfileOverviewUiState.Ready) state
        else state.copy(open = if (subjectId in state.open) state.open - subjectId else state.open + subjectId)
    }
}

/** Which activities the full history shows; [PRACTICE] holds the free practice and the mistake practice. */
enum class HistoryFilter { ALL, LESSONS, TESTS, PRACTICE }

sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    data object Failed : HistoryUiState

    @OptIn(ExperimentalTime::class)
    data class Ready(val history: History, val filter: HistoryFilter = HistoryFilter.ALL) : HistoryUiState
}

/** The full history of the profile, under day headings, with a filter for lessons, tests and practice. */
class HistoryViewModel(private val history: ActivityHistory) : ViewModel() {

    private val _state = MutableStateFlow<HistoryUiState>(HistoryUiState.Loading)
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val shown = _state.value
            _state.value = when (val result = history.load()) {
                is OperationResult.Success -> HistoryUiState.Ready(result.data, (shown as? HistoryUiState.Ready)?.filter ?: HistoryFilter.ALL)
                is OperationResult.Error -> if (shown is HistoryUiState.Ready) shown else HistoryUiState.Failed
            }
        }
    }

    fun select(filter: HistoryFilter) = _state.update { if (it is HistoryUiState.Ready) it.copy(filter = filter) else it }
}
