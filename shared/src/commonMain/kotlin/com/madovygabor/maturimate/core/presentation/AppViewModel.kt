package com.madovygabor.maturimate.core.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madovygabor.maturimate.core.presentation.utils.READ_RETRY_DELAYS_MILLIS
import com.madovygabor.maturimate.core.presentation.utils.retryReads
import com.madovygabor.maturimate.features.auth.domain.SessionRevoker
import com.madovygabor.maturimate.features.content.domain.ContentSwapGate
import com.madovygabor.maturimate.features.sync.domain.SyncTriggers
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the whole app depends on: the language of its texts and where it starts. */
data class AppState(
    /** Null until the user picks a teaching language: the phone's language is used. */
    val language: TeachingLanguage?,
    val onboardingCompleted: Boolean,
)

/**
 * Follows the saved profile, so a new language or a finished onboarding takes effect at once. It also passes on what the
 * whole app does: the sync when the app comes to the front, a sign-out the server has not heard of yet, and whether a
 * lesson is open (the content file is not swapped under it).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModel(
    repository: UserProfileRepository,
    retryDelaysMillis: List<Long> = READ_RETRY_DELAYS_MILLIS,
    private val syncTriggers: SyncTriggers? = null,
    private val sessionRevoker: SessionRevoker? = null,
    private val contentSwapGate: ContentSwapGate? = null,
) : ViewModel() {

    init {
        // one time zone for the whole app: the device's, kept in the settings
        viewModelScope.launch { repository.refreshTimeZone() }
    }

    private val reload = MutableStateFlow(0)
    private val _failed = MutableStateFlow(false)

    /** True when the profile could not be read even after the retries; [retry] tries again. */
    val failed: StateFlow<Boolean> = _failed.asStateFlow()

    /**
     * Null while the profile is being read for the first time. A read that keeps failing leaves
     * the last state as it was, so a user who finished the onboarding is never sent back to it.
     */
    val state: StateFlow<AppState?> = reload
        .flatMapLatest {
            repository.profile
                .map { profile ->
                    AppState(
                        language = profile?.uiLanguage?.takeIf { profile.teachingLanguage != null },
                        onboardingCompleted = profile?.isOnboardingCompleted == true,
                    )
                }
                .retryReads(retryDelaysMillis)
                .onEach { _failed.value = false }
                .catch { _failed.value = true }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun retry() {
        _failed.value = false
        reload.update { it + 1 }
    }

    /** The app opened or came back to the front. */
    fun onForeground() {
        // the sync runs at most every five minutes from here
        syncTriggers?.onForeground()
        // a sign-out made without a network is told to the server now, if the network is back
        sessionRevoker?.let { viewModelScope.launch { runCatching { it.revokePending() } } }
    }

    /** A lesson, a test or a practice round opened (true) or the last one closed (false). */
    fun onReadingChanged(reading: Boolean) {
        contentSwapGate?.let { viewModelScope.launch { it.setReading(reading) } }
    }
}
