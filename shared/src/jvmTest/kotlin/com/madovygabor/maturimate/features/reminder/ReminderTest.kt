package com.madovygabor.maturimate.features.reminder

import com.madovygabor.maturimate.features.reminder.domain.ReminderScheduler
import com.madovygabor.maturimate.features.reminder.domain.ReminderSync
import com.madovygabor.maturimate.features.reminder.domain.nextReminder
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
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
}

/** A stand-in that rejects every call; a test overrides the one call it needs. */
private inline fun <reified T : Any> unsupported(): T =
    java.lang.reflect.Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        throw UnsupportedOperationException(method.name)
    } as T
