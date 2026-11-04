package com.madovygabor.maturimate.features.learning.presentation

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
class HomeViewModel(private val overview: HomeOverview) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

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
