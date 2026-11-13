package com.madovygabor.maturimate.features.sync

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.sync.domain.PendingChanges
import com.madovygabor.maturimate.features.sync.domain.StoredSyncState
import com.madovygabor.maturimate.features.sync.domain.SyncChanges
import com.madovygabor.maturimate.features.sync.domain.SyncEngine
import com.madovygabor.maturimate.features.sync.domain.SyncError
import com.madovygabor.maturimate.features.sync.domain.SyncReason
import com.madovygabor.maturimate.features.sync.domain.SyncRepository
import com.madovygabor.maturimate.features.sync.domain.SyncResponse
import com.madovygabor.maturimate.features.sync.domain.SyncSession
import com.madovygabor.maturimate.features.sync.domain.SyncStore
import com.madovygabor.maturimate.features.sync.domain.SyncTriggers
import com.madovygabor.maturimate.features.userProfile.domain.DataError
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import com.madovygabor.maturimate.features.userProfile.domain.UserSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Instant

class SyncTriggersTest {

    private val reasons = CopyOnWriteArrayList<String>()

    private val repository = object : SyncRepository {
        override suspend fun sync(cursor: String?, deviceId: String, guestMerge: Boolean, changes: SyncChanges): OperationResult<SyncResponse, SyncError> {
            reasons += "call"
            return OperationResult.Success(SyncResponse(serverTime = 0, cursor = "c"))
        }
    }
    private val store = object : SyncStore {
        var state = StoredSyncState("u", null, 1, null, fullPushPending = false, guestMergePending = false, rejectedCount = 0)
        override suspend fun state() = state
        override suspend fun pending(since: Long?, full: Boolean, maxRounds: Int) = PendingChanges(SyncChanges(), null, false)
        override suspend fun apply(response: SyncResponse, guestMerge: Boolean, pushedUpTo: Long?, now: Long) {}
        override suspend fun markPage(pushedUpTo: Long?) {}
        override suspend fun markFailed(error: SyncError) {}
        override suspend fun requestFullPush() {}
        override suspend fun pendingSummary() = 0 to (null as Long?)
        override suspend fun clear() {}
        override fun changes(): Flow<Unit> = emptyFlow()
    }
    private val session = object : SyncSession {
        override suspend fun isSignedIn() = true
        override suspend fun deviceId() = "dev"
    }
    private val engine = SyncEngine(repository, store, session, now = { 0 })

    private fun profile(goal: Int, zone: String = "Europe/Bratislava") = UserProfile(
        teachingLanguage = TeachingLanguage.HU, role = UserRole.STUDENT, graduationDate = null, uiLanguage = TeachingLanguage.HU,
        dailyGoalMinutes = goal, timezone = zone, onboardingCompletedAt = null,
    )

    private class FakeProfiles(val flow: MutableStateFlow<UserProfile?>) : UserProfileRepository {
        override val profile: Flow<UserProfile?> = flow
        private fun unused(): Nothing = error("not used")
        override suspend fun saveTeachingLanguage(language: TeachingLanguage) = unused()
        override suspend fun saveSchoolLanguage(language: TeachingLanguage) = unused()
        override suspend fun saveUiLanguage(language: TeachingLanguage) = unused()
        override suspend fun saveRole(role: UserRole) = unused()
        override suspend fun saveSubjects(subjects: List<UserSubject>) = unused()
        override suspend fun saveGraduationDate(date: LocalDate) = unused()
        override suspend fun saveReminder(enabled: Boolean, time: LocalTime?) = unused()
        override suspend fun saveDailyGoal(minutes: Int) = unused()
        override suspend fun saveNames(firstName: String, lastName: String) = unused()
        override suspend fun completeOnboarding(at: Instant) = unused()
    }

    private suspend fun settle() = delay(150)

    @Test
    fun theForegroundRunHasAGapButTheEndOfARoundDoesNot() = runBlocking {
        var clock = 0L
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val triggers = SyncTriggers(engine, FakeProfiles(MutableStateFlow(null)), scope, now = { clock })

        triggers.onForeground()
        settle()
        assertEquals(1, reasons.size)
        // within five minutes nothing runs
        clock = 4 * 60_000L
        triggers.onForeground()
        settle()
        assertEquals(1, reasons.size)
        // the end of a round runs at once
        triggers.onRoundEnd()
        settle()
        assertEquals(2, reasons.size)
        clock = 11 * 60_000L
        triggers.onForeground()
        settle()
        assertEquals(3, reasons.size)
    }

    @Test
    fun aSettingsChangeSyncsOnceAfterTheDelayAndTheZoneAndTheFirstValueDoNot() = runBlocking {
        val profiles = MutableStateFlow<UserProfile?>(profile(10))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val triggers = SyncTriggers(engine, FakeProfiles(profiles), scope, now = { 0 }, settingsDelay = 60.milliseconds)
        val job = scope.launch { triggers.run() }
        settle()
        assertEquals(0, reasons.size)

        // only the time zone changed: not a synced field
        profiles.value = profile(10, zone = "Europe/London")
        delay(250)
        assertEquals(0, reasons.size)

        // two quick changes make one run
        profiles.value = profile(15)
        delay(20)
        profiles.value = profile(20)
        delay(300)
        assertEquals(1, reasons.size)
        job.cancel()
    }

    @Test
    fun aRunThatFoundNobodySignedInDoesNotStartTheGap() = runBlocking {
        var signedIn = false
        val waiting = object : SyncSession {
            override suspend fun isSignedIn() = signedIn
            override suspend fun deviceId() = "dev"
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val triggers = SyncTriggers(SyncEngine(repository, store, waiting, now = { 0 }), FakeProfiles(MutableStateFlow(null)), scope, now = { 1_000 })

        // a cold start: the saved session is not there yet
        triggers.onForeground()
        settle()
        assertEquals(0, reasons.size)
        // a moment later it is: the next foreground runs without waiting five minutes
        signedIn = true
        triggers.onForeground()
        settle()
        assertEquals(1, reasons.size)
    }

    @Test
    fun aChangeTheSyncBroughtDoesNotSyncAgain() = runBlocking {
        val profiles = MutableStateFlow<UserProfile?>(profile(10))
        // the server's answer changes the daily goal on this phone
        val pulling = object : SyncRepository {
            override suspend fun sync(cursor: String?, deviceId: String, guestMerge: Boolean, changes: SyncChanges): OperationResult<SyncResponse, SyncError> {
                reasons += "call"
                profiles.value = profile(30)
                return OperationResult.Success(SyncResponse(serverTime = 0, cursor = "c"))
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val triggers = SyncTriggers(SyncEngine(pulling, store, session, now = { 0 }), FakeProfiles(profiles), scope, now = { 0 }, settingsDelay = 60.milliseconds)
        val job = scope.launch { triggers.run() }
        settle()

        triggers.onManual()
        delay(300)
        assertEquals(1, reasons.size)
        // a change of the user's own still syncs
        profiles.value = profile(45)
        delay(300)
        assertEquals(2, reasons.size)
        job.cancel()
    }

    @Test
    fun theAfterSyncHookGetsTheReleases() = runBlocking {
        var seen = 0
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val triggers = SyncTriggers(engine, FakeProfiles(MutableStateFlow(null)), scope, now = { 0 }, afterSync = { seen++ })

        triggers.onManual()
        settle()

        assertEquals(1, seen)
        assertEquals(SyncReason.MANUAL, SyncReason.MANUAL)
    }
}
