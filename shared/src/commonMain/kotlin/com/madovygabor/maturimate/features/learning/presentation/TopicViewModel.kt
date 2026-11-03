package com.madovygabor.maturimate.features.learning.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.learning.domain.LearningCourse
import com.madovygabor.maturimate.features.learning.domain.model.TopicLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What a level of the topic tree is read for: the parts of a subject, or one topic. */
sealed interface TopicTarget {
    data class SubjectParts(val subjectId: Long) : TopicTarget
    data class Topic(val topicId: Long) : TopicTarget
}

sealed interface TopicUiState {
    data object Loading : TopicUiState
    data class Ready(val level: TopicLevel) : TopicUiState
    data object Failed : TopicUiState
}

/** The parts of a subject, the topics below a topic, or the path of a topic. */
class TopicViewModel(
    private val course: LearningCourse,
    private val target: TopicTarget,
) : ViewModel() {

    private val _state = MutableStateFlow<TopicUiState>(TopicUiState.Loading)
    val state: StateFlow<TopicUiState> = _state.asStateFlow()

    /** Reads again, so progress made on another screen shows when the user comes back. */
    fun refresh() {
        viewModelScope.launch {
            val result = when (target) {
                is TopicTarget.SubjectParts -> course.parts(target.subjectId)
                is TopicTarget.Topic -> course.topic(target.topicId)
            }
            _state.value = when (result) {
                is OperationResult.Success -> TopicUiState.Ready(result.data)
                // keep what is shown when a later read fails
                is OperationResult.Error -> if (_state.value is TopicUiState.Ready) _state.value else TopicUiState.Failed
            }
        }
    }
}
