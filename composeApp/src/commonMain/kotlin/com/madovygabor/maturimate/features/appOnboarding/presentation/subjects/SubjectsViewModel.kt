package com.madovygabor.maturimate.features.appOnboarding.presentation.subjects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.features.appOnboarding.presentation.profileSetup.error.toUiText
import com.madovygabor.maturimate.features.learning.domain.ContentRepository
import com.madovygabor.maturimate.features.userProfile.domain.CatalogSubject
import com.madovygabor.maturimate.features.userProfile.domain.SubjectLevel
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import com.madovygabor.maturimate.features.userProfile.domain.UserSubject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The English level answer; "I don't know yet" is saved as B1. */
enum class LevelChoice { B1, B2, UNKNOWN }

/** One row of the subject list. */
data class SubjectItem(
    val subject: CatalogSubject,
    val selected: Boolean,
    /** Compulsory for a student: preselected and cannot be removed. */
    val locked: Boolean,
    /** The installed content has this subject; "Hamarosan" otherwise. */
    val ready: Boolean,
)

data class SubjectsUiState(
    val role: UserRole? = null,
    val items: List<SubjectItem> = emptyList(),
    val level: LevelChoice? = null,
    val saving: Boolean = false,
) {
    private val selectedSubjects get() = items.filter { it.selected }
    val needsLevel: Boolean get() = selectedSubjects.any { it.subject.hasLevels }
    val canContinue: Boolean
        get() = items.isNotEmpty() && selectedSubjects.isNotEmpty() && (!needsLevel || level != null) && !saving
}

/**
 * The subjects question: compulsory subjects come from the teaching language and are locked for
 * students; English also asks for its level (docs/onboarding/README.md, section 3).
 */
class SubjectsViewModel(
    private val repository: UserProfileRepository,
    private val contentRepository: ContentRepository,
    private val snackbarController: SnackbarController,
) : ViewModel() {

    private val _state = MutableStateFlow(SubjectsUiState())
    val state: StateFlow<SubjectsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = runCatching { repository.profile.first() }.getOrNull()
            val readyCodes = when (val subjects = contentRepository.subjects()) {
                is OperationResult.Success -> subjects.data.map { it.code }.toSet()
                is OperationResult.Error -> emptySet()
            }
            val role = profile?.role
            val saved = profile?.subjects.orEmpty().associateBy { it.subjectId }
            val items = CatalogSubject.forLanguage(profile?.teachingLanguage).map { subject ->
                val locked = role == UserRole.STUDENT && subject.compulsory
                SubjectItem(
                    subject = subject,
                    // nothing saved yet: a student starts with the compulsory subjects
                    selected = locked || if (saved.isEmpty()) false else subject.id in saved,
                    locked = locked,
                    ready = subject.code in readyCodes,
                )
            }
            val savedLevel = saved.values.firstNotNullOfOrNull { it.level }
            _state.update {
                it.copy(
                    role = role,
                    items = items,
                    level = it.level ?: savedLevel?.let { level -> LevelChoice.valueOf(level.name) },
                )
            }
        }
    }

    fun onToggled(subject: CatalogSubject) {
        _state.update { state ->
            state.copy(items = state.items.map { item ->
                if (item.subject == subject && !item.locked) item.copy(selected = !item.selected) else item
            })
        }
    }

    fun onLevelSelected(level: LevelChoice) {
        _state.update { it.copy(level = level) }
    }

    /** Saves the chosen subjects, then calls [onSaved] with the user's role. */
    fun onNext(onSaved: (UserRole?) -> Unit) {
        val current = _state.value
        if (!current.canContinue) return
        _state.update { it.copy(saving = true) }
        val level = if (current.level == LevelChoice.B2) SubjectLevel.B2 else SubjectLevel.B1
        val subjects = current.items.filter { it.selected }.map { item ->
            UserSubject(
                subjectId = item.subject.id,
                compulsory = item.locked,
                level = if (item.subject.hasLevels) level else null,
            )
        }
        viewModelScope.launch {
            when (val result = repository.saveSubjects(subjects)) {
                is OperationResult.Success -> onSaved(current.role)
                is OperationResult.Error -> snackbarController.showSnackbar(message = result.error.toUiText())
            }
            _state.update { it.copy(saving = false) }
        }
    }
}
