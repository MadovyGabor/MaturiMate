package com.madovygabor.maturimate.features.learning.presentation

import com.madovygabor.maturimate.features.userProfile.domain.ExamDateRepository
import com.madovygabor.maturimate.features.userProfile.domain.OfficialExamDate
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import kotlinx.coroutines.flow.drop
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.learning.domain.HomeOverview
import com.madovygabor.maturimate.features.learning.domain.HomeSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Failed : HomeUiState
    @OptIn(ExperimentalTime::class)
    data class Ready(val summary: HomeSummary) : HomeUiState
}

/** The home tab: what to do today. It reads again whenever the screen comes back. */
class HomeViewModel(
    private val overview: HomeOverview,
    private val examDates: ExamDateRepository? = null,
    private val profile: UserProfileRepository? = null,
) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        // an official date that the sync brings changes the countdown at once, without a restart
        examDates?.let { repository ->
            viewModelScope.launch { repository.dates.drop(1).collect { refresh() } }
        }
    }

    /** "Frissítés": the official date replaces the one picked by hand. */
    fun acceptOfficialDate(date: OfficialExamDate) {
        viewModelScope.launch {
            profile?.saveGraduationDate(date.date)
            examDates?.markAnswered(date)
            refresh()
        }
    }

    /** "Nem, marad": the own date stays, and this publication does not ask again. */
    fun keepOwnDate(date: OfficialExamDate) {
        viewModelScope.launch {
            examDates?.markAnswered(date)
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = when (val result = overview.load()) {
                is OperationResult.Success -> HomeUiState.Ready(result.data)
                // keep what is shown when a later read fails
                is OperationResult.Error -> if (_state.value is HomeUiState.Ready) _state.value else HomeUiState.Failed
            }
        }
    }
}
