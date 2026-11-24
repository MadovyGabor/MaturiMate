package com.madovygabor.maturimate.features.reminder.domain

import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Keeps the planned reminder in line with the profile: planned while the reminder is on and has a time, removed
 * otherwise. [run] never returns; start it once, when the app starts, so a change made in the onboarding or in
 * the settings takes effect at once.
 */
class ReminderSync(
    private val profile: UserProfileRepository,
    private val scheduler: ReminderScheduler,
) {
    suspend fun run() {
        profile.profile
            .map { if (it?.reminderEnabled == true) it.reminderTime else null }
            .distinctUntilChanged()
            .collect { time -> if (time == null) scheduler.cancel() else scheduler.schedule(time) }
    }

    /** Plans the reminder once from the profile as it is now (after a restart of the phone), and returns. */
    suspend fun planNow() {
        val profile = profile.profile.first()
        val time = if (profile?.reminderEnabled == true) profile.reminderTime else null
        if (time == null) scheduler.cancel() else scheduler.schedule(time)
    }
}
