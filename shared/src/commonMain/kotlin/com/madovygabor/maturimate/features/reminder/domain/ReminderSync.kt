package com.madovygabor.maturimate.features.reminder.domain

import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import kotlinx.coroutines.flow.distinctUntilChanged
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
}
