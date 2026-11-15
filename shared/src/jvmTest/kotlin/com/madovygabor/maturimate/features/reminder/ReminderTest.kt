package com.madovygabor.maturimate.features.reminder

import com.madovygabor.maturimate.features.reminder.domain.ReminderScheduler
import com.madovygabor.maturimate.features.reminder.domain.ReminderSync
import com.madovygabor.maturimate.features.reminder.domain.nextReminder
import com.madovygabor.maturimate.features.reminder.domain.replansReminder
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import com.madovygabor.maturimate.core.presentation.NotificationPermissionResult
import com.madovygabor.maturimate.core.presentation.notificationPermissionResult
import com.madovygabor.maturimate.features.reminder.domain.ReminderKind
import com.madovygabor.maturimate.features.reminder.domain.handleReminderAlarm
import com.madovygabor.maturimate.features.reminder.domain.publicReminderMessage
import com.madovygabor.maturimate.features.reminder.domain.reminderLanguageTag
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class, DelicateCoroutinesApi::class)
class ReminderTest {

    private val bratislava = TimeZone.of("Europe/Bratislava")

    @Test
    fun theReminderIsTodayWhenItsTimeIsStillAhead() {
        val now = Instant.parse("2026-11-04T10:00:00Z") // 11:00 in Bratislava
        assertEquals(Instant.parse("2026-11-04T18:30:00Z"), nextReminder(LocalTime(19, 30), now, bratislava))
    }

    @Test
    fun theReminderIsTomorrowWhenItsTimeHasPassedOrIsNow() {
        val now = Instant.parse("2026-11-04T18:30:00Z") // 19:30 in Bratislava
        assertEquals(Instant.parse("2026-11-05T18:30:00Z"), nextReminder(LocalTime(19, 30), now, bratislava))
        assertEquals(Instant.parse("2026-11-05T07:00:00Z"), nextReminder(LocalTime(8, 0), now, bratislava))
    }

    private class FakeScheduler : ReminderScheduler {
        val calls = mutableListOf<String>()
        override fun schedule(time: LocalTime) { calls += "schedule $time" }
        override fun cancel() { calls += "cancel" }
    }

    private fun profile(enabled: Boolean, time: LocalTime?) = UserProfile(
        teachingLanguage = TeachingLanguage.HU, role = null, graduationDate = null, uiLanguage = TeachingLanguage.HU,
        dailyGoalMinutes = 10, timezone = "UTC", onboardingCompletedAt = null,
        reminderEnabled = enabled, reminderTime = time,
    )

    @Test
    fun theScheduleFollowsTheProfile() = runBlocking {
        val profile = MutableStateFlow<UserProfile?>(null)
        val repository = object : UserProfileRepository by unsupported() {
            override val profile = profile
        }
        val scheduler = FakeScheduler()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { ReminderSync(repository, scheduler).run() }

        profile.value = profile(true, LocalTime(19, 30))
        kotlinx.coroutines.yield()
        profile.value = profile(true, LocalTime(19, 30)).copy(dailyGoalMinutes = 20) // not a reminder change
        kotlinx.coroutines.yield()
        profile.value = profile(true, LocalTime(8, 0))
        kotlinx.coroutines.yield()
        profile.value = profile(false, LocalTime(8, 0))
        kotlinx.coroutines.yield()
        job.cancel()

        assertEquals(listOf("cancel", "schedule 19:30", "schedule 08:00", "cancel"), scheduler.calls)
    }
    @Test
    fun planNowPlansOnceFromTheCurrentProfileAndReturns() = runBlocking {
        val on = object : UserProfileRepository by unsupported() {
            override val profile = MutableStateFlow<UserProfile?>(profile(true, LocalTime(19, 30)))
        }
        val off = object : UserProfileRepository by unsupported() {
            override val profile = MutableStateFlow<UserProfile?>(profile(false, LocalTime(19, 30)))
        }
        val none = object : UserProfileRepository by unsupported() {
            override val profile = MutableStateFlow<UserProfile?>(null)
        }
        val scheduler = FakeScheduler()
        ReminderSync(on, scheduler).planNow()
        ReminderSync(off, scheduler).planNow()
        ReminderSync(none, scheduler).planNow()
        assertEquals(listOf("schedule 19:30", "cancel", "cancel"), scheduler.calls)
    }

    @Test
    fun onlyABootOrAnUpdateReplansTheReminder() {
        assertEquals(true, replansReminder("android.intent.action.BOOT_COMPLETED"))
        assertEquals(true, replansReminder("android.intent.action.MY_PACKAGE_REPLACED"))
        assertEquals(false, replansReminder("android.intent.action.MAIN"))
        assertEquals(false, replansReminder("com.other.app.BOOT_COMPLETED"))
        assertEquals(false, replansReminder(null))
    }

    // ---- the alarm: the next day is planned whatever happens ----

    private val fallback = LocalTime(7, 5)

    @Test
    fun anAlarmShowsAndPlansTheNextDay() = runBlocking {
        val scheduler = FakeScheduler()
        var shown = 0
        handleReminderAlarm(scheduler, fallback, readTime = { LocalTime(19, 30) }, show = { shown++ })
        assertEquals(1, shown)
        assertEquals(listOf("schedule 19:30"), scheduler.calls)
    }

    @Test
    fun aReminderSwitchedOffShowsNothingAndPlansNothing() = runBlocking {
        val scheduler = FakeScheduler()
        var shown = 0
        handleReminderAlarm(scheduler, fallback, readTime = { null }, show = { shown++ })
        assertEquals(0, shown)
        assertEquals(emptyList(), scheduler.calls)
    }

    @Test
    fun aFailureWhileShowingStillPlansTheNextDay() = runBlocking {
        val scheduler = FakeScheduler()
        handleReminderAlarm(scheduler, fallback, readTime = { LocalTime(19, 30) }, show = { error("tracker read failed") })
        assertEquals(listOf("schedule 19:30"), scheduler.calls)
    }

    @Test
    fun aFailedReadPlansTheNextDayAtTheTimeItIsNow() = runBlocking {
        val scheduler = FakeScheduler()
        handleReminderAlarm(scheduler, fallback, readTime = { error("database failed") }, show = { })
        assertEquals(listOf("schedule 07:05"), scheduler.calls)
    }

    @Test
    fun aHangingReadIsCutAtTheTimeLimitAndStillPlans() = runBlocking {
        val scheduler = FakeScheduler()
        handleReminderAlarm(scheduler, fallback, timeoutMillis = 50, readTime = { awaitCancellation() }, show = { })
        assertEquals(listOf("schedule 07:05"), scheduler.calls)
    }

    @Test
    fun aHangingShowKeepsTheKnownTime() = runBlocking {
        val scheduler = FakeScheduler()
        handleReminderAlarm(scheduler, fallback, timeoutMillis = 50, readTime = { LocalTime(19, 30) }, show = { awaitCancellation() })
        assertEquals(listOf("schedule 19:30"), scheduler.calls)
    }

    @Test
    fun aCancellationIsPassedOnAfterPlanning() {
        val scheduler = FakeScheduler()
        val job = GlobalScope.launch(start = CoroutineStart.UNDISPATCHED) {
            handleReminderAlarm(scheduler, fallback, readTime = { LocalTime(19, 30) }, show = { awaitCancellation() })
        }
        job.cancel()
        runBlocking { job.join() }
        assertEquals(true, job.isCancelled)
        assertEquals(listOf("schedule 19:30"), scheduler.calls)
    }

    // ---- the lock screen and the language ----

    @Test
    fun theLockScreenVersionNeverMentionsTheStreakOrTheExam() {
        val public = publicReminderMessage()
        assertEquals(ReminderKind.PLAIN, public.kind)
        assertEquals(null, public.number)
    }

    @Test
    fun theNotificationFollowsTheAppLanguageNotThePhone() {
        // the per-app language wins, also with a region
        assertEquals("hu", reminderLanguageTag("hu-HU", TeachingLanguage.SK))
        assertEquals("sk", reminderLanguageTag("sk", TeachingLanguage.HU))
        // no per-app language, or one the app does not have: the profile's
        assertEquals("hu", reminderLanguageTag(null, TeachingLanguage.HU))
        assertEquals("sk", reminderLanguageTag("en-US", TeachingLanguage.SK))
        assertEquals("sk", reminderLanguageTag(null, TeachingLanguage.SK))
    }

    // ---- the permission ----

    @Test
    fun aRefusedPermissionIsDeniedOrBlockedForGood() {
        assertEquals(NotificationPermissionResult.GRANTED, notificationPermissionResult(granted = true, canAskAgain = false))
        assertEquals(NotificationPermissionResult.DENIED, notificationPermissionResult(granted = false, canAskAgain = true))
        assertEquals(NotificationPermissionResult.BLOCKED, notificationPermissionResult(granted = false, canAskAgain = false))
    }
}

/** A stand-in that rejects every call; a test overrides the one call it needs. */
private inline fun <reified T : Any> unsupported(): T =
    java.lang.reflect.Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        throw UnsupportedOperationException(method.name)
    } as T
