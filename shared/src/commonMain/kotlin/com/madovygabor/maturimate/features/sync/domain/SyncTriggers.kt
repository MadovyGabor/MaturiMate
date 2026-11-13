package com.madovygabor.maturimate.features.sync.domain

import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * When the sync runs: at opening and when the app comes to the front (at most every [minGap]), at the end of every round
 * and lesson, three seconds after the settings changed, by hand, and before a sign-out (the engine does that one). No
 * background jobs: the app is open whenever a run starts. [run] never returns; start it once with the application.
 *
 * The methods the screens call do not wait: the run goes on in [scope].
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SyncTriggers(
    private val engine: SyncEngine,
    private val profile: UserProfileRepository,
    private val scope: CoroutineScope,
    private val now: () -> Long,
    private val minGap: Duration = 5.minutes,
    private val settingsDelay: Duration = 3.seconds,
    /** Called with the content releases of the server after a run that went through (the content download). */
    private val afterSync: suspend (List<ContentRelease>) -> Unit = {},
) {
    /** When the last opening or foreground run started; a run that found nobody signed in does not count. */
    @Volatile
    private var lastStart: Long? = null

    /** The synced fields as the last finished run left them: a change the sync itself brought is not the user's. */
    @Volatile
    private var afterLastSync: List<Any?>? = null

    /** Syncs when the settings of the profile change. */
    suspend fun run() {
        profile.profile
            .map { it?.synced() }
            .distinctUntilChanged()
            // the value at the start is not a change
            .drop(1)
            .debounce(settingsDelay)
            .collect { if (it != afterLastSync) start(SyncReason.SETTINGS) }
    }

    /** The app is open or came back to the front. */
    fun onForeground() {
        val last = lastStart
        if (last != null && now() - last < minGap.inWholeMilliseconds) return
        start(if (last == null) SyncReason.OPENING else SyncReason.FOREGROUND)
    }

    /** A round ended (always, no gap). */
    fun onRoundEnd() = start(SyncReason.ROUND_END)

    fun onLessonDone() = start(SyncReason.LESSON_END)

    fun onManual() = start(SyncReason.MANUAL)

    private fun start(reason: SyncReason) {
        val previous = lastStart
        val started = now()
        lastStart = started
        scope.launch {
            // the state shows the round that waits right away, before the upload
            engine.refresh()
            val outcome = engine.sync(reason)
            if (outcome is SyncOutcome.Done) {
                afterLastSync = profile.profile.first()?.synced()
                afterSync(outcome.contentReleases)
            }
            // nobody was signed in (yet): the next time the app comes to the front tries again at once
            if (outcome is SyncOutcome.NotApplicable && lastStart == started) lastStart = previous
        }
    }

    /** What the settings sync follows: everything that travels in the profile and the settings, without the time zone. */
    private fun UserProfile.synced() = listOf(
        teachingLanguage, role, graduationDate, uiLanguage, dailyGoalMinutes, onboardingCompletedAt, subjects,
        reminderEnabled, reminderTime, firstName, lastName,
    )
}
