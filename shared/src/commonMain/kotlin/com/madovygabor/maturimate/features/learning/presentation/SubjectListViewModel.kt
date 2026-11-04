package com.madovygabor.maturimate.features.learning.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.core.presentation.utils.readOrNull
import com.madovygabor.maturimate.features.learning.domain.LearningCourse
import com.madovygabor.maturimate.features.learning.domain.model.SubjectProgress
import com.madovygabor.maturimate.features.userProfile.domain.CatalogSubject
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** A subject of the user. [progress] is null while the installed content has no such subject ("Hamarosan"). */
data class SubjectCard(val subject: CatalogSubject, val progress: SubjectProgress?)

sealed interface SubjectListUiState {
    data object Loading : SubjectListUiState
    data class Ready(val cards: List<SubjectCard>) : SubjectListUiState
    data object Failed : SubjectListUiState
}

/** The subjects tab: the user's subjects, each with the progress in the installed content. */
class SubjectListViewModel(
    private val profileRepository: UserProfileRepository,
    private val course: LearningCourse,
) : ViewModel() {

    private val _state = MutableStateFlow<SubjectListUiState>(SubjectListUiState.Loading)
    val state: StateFlow<SubjectListUiState> = _state.asStateFlow()

    /** Reads again, so progress made on another screen shows when the user comes back. */
    fun refresh() {
        viewModelScope.launch {
            val profile = readOrNull { profileRepository.profile.first() }
            val subjects = profile?.subjects.orEmpty()
                .mapNotNull { CatalogSubject.byId(it.subjectId) }
                .sortedBy { it.ordinal }
                .distinct()
            _state.value = when (val installed = course.subjects()) {
                is OperationResult.Success -> SubjectListUiState.Ready(
                    subjects.map { subject -> SubjectCard(subject, installed.data.firstOrNull { it.subjectId == subject.id }) }
                )
                // keep what is shown when a later read fails
                is OperationResult.Error ->
                    if (_state.value is SubjectListUiState.Ready) _state.value else SubjectListUiState.Failed
            }
        }
    }
}
