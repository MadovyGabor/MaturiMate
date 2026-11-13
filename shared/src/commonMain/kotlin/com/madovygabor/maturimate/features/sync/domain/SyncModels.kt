package com.madovygabor.maturimate.features.sync.domain

import com.madovygabor.maturimate.core.domain.error.Error
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * The shapes of `sync_user_data` (supabase/migrations/20261118120000_sync_functions.sql, plan-12 section 3.1).
 * Times are epoch milliseconds, dates are YYYY-MM-DD. No time zone, and nothing of the account itself (e-mail, password):
 * those never travel through the sync.
 */

@Serializable
data class SyncProfile(
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("teaching_language") val teachingLanguage: String? = null,
    @SerialName("graduation_date") val graduationDate: String? = null,
    @SerialName("onboarding_completed_at") val onboardingCompletedAt: Long? = null,
    @SerialName("progress_reset_at") val progressResetAt: Long? = null,
    @SerialName("role_id") val roleId: Long? = null,
    @SerialName("updated_at") val updatedAt: Long,
)

@Serializable
data class SyncSettings(
    @SerialName("ui_language") val uiLanguage: String,
    @SerialName("daily_goal_minutes") val dailyGoalMinutes: Long? = null,
    @SerialName("reminder_enabled") val reminderEnabled: Boolean = false,
    @SerialName("reminder_time") val reminderTime: String? = null,
    @SerialName("updated_at") val updatedAt: Long,
)

@Serializable
data class SyncSubject(
    @SerialName("subject_id") val subjectId: Long,
    val source: String,
    val level: String? = null,
    @SerialName("added_at") val addedAt: Long,
    @SerialName("removed_at") val removedAt: Long? = null,
    @SerialName("updated_at") val updatedAt: Long,
)

@Serializable
data class SyncAnswer(
    @SerialName("task_id") val taskId: Long,
    @SerialName("is_correct") val isCorrect: Boolean,
    @SerialName("earned_points") val earnedPoints: Long,
    @SerialName("max_points") val maxPoints: Long,
    @SerialName("answered_at") val answeredAt: Long,
    @SerialName("given_answer") val givenAnswer: String? = null,
)

@Serializable
data class SyncAttempt(
    @SerialName("attempt_id") val attemptId: String,
    @SerialName("attempt_type") val attemptType: String,
    @SerialName("topic_id") val topicId: Long? = null,
    @SerialName("learning_unit_id") val learningUnitId: Long? = null,
    val status: String,
    @SerialName("started_at") val startedAt: Long,
    @SerialName("finished_at") val finishedAt: Long? = null,
    @SerialName("earned_points") val earnedPoints: Long? = null,
    @SerialName("max_points") val maxPoints: Long? = null,
    val answers: List<SyncAnswer> = emptyList(),
)

@Serializable
data class SyncUnitProgress(
    @SerialName("learning_unit_id") val learningUnitId: Long,
    val status: String,
    @SerialName("started_at") val startedAt: Long,
    @SerialName("completed_at") val completedAt: Long? = null,
    @SerialName("updated_at") val updatedAt: Long,
)

@Serializable
data class SyncStreak(
    @SerialName("current_streak") val currentStreak: Long,
    @SerialName("longest_streak") val longestStreak: Long,
    @SerialName("last_activity_date") val lastActivityDate: String? = null,
    @SerialName("updated_at") val updatedAt: Long,
)

@Serializable
data class SyncActivity(
    @SerialName("activity_date") val activityDate: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("seconds_studied") val secondsStudied: Long,
    @SerialName("tasks_answered") val tasksAnswered: Long,
    @SerialName("updated_at") val updatedAt: Long,
)

/** What changed on the phone, to be sent. */
@Serializable
data class SyncChanges(
    val profile: SyncProfile? = null,
    val settings: SyncSettings? = null,
    val subjects: List<SyncSubject> = emptyList(),
    val attempts: List<SyncAttempt> = emptyList(),
    @SerialName("unit_progress") val unitProgress: List<SyncUnitProgress> = emptyList(),
    val streak: SyncStreak? = null,
    val activity: List<SyncActivity> = emptyList(),
) {
    val isEmpty: Boolean
        get() = profile == null && settings == null && subjects.isEmpty() && attempts.isEmpty() &&
            unitProgress.isEmpty() && streak == null && activity.isEmpty()
}

@Serializable
data class SyncReject(val table: String, val key: String? = null, val reason: String)

@Serializable
data class SyncExamDate(
    @SerialName("exam_date_id") val examDateId: Long,
    @SerialName("exam_year") val examYear: Long,
    @SerialName("subject_id") val subjectId: Long? = null,
    @SerialName("exam_on") val examOn: String,
    @SerialName("published_at") val publishedAt: Long,
    val note: String? = null,
)

@Serializable
data class ContentRelease(
    @SerialName("subject_id") val subjectId: Long,
    @SerialName("schema_version") val schemaVersion: Long,
    @SerialName("content_version") val contentVersion: Long,
    @SerialName("size_bytes") val sizeBytes: Long,
    val sha256: String,
)

/** What the server returned: everything of the account that changed since the cursor, and the lists that come every time. */
@Serializable
data class SyncResponse(
    @SerialName("server_time") val serverTime: Long,
    val cursor: String,
    val more: Boolean = false,
    val rejected: List<SyncReject> = emptyList(),
    val ignored: Int = 0,
    val profile: SyncProfile? = null,
    val settings: SyncSettings? = null,
    val roles: List<Long> = emptyList(),
    val subjects: List<SyncSubject> = emptyList(),
    val attempts: List<SyncAttempt> = emptyList(),
    @SerialName("unit_progress") val unitProgress: List<SyncUnitProgress> = emptyList(),
    val streak: SyncStreak? = null,
    val activity: List<SyncActivity> = emptyList(),
    @SerialName("exam_dates") val examDates: List<SyncExamDate> = emptyList(),
    @SerialName("content_releases") val contentReleases: List<ContentRelease> = emptyList(),
)

enum class SyncError : Error {
    NETWORK,
    NOT_SIGNED_IN,
    /** The call carried too much; the next one is smaller. */
    TOO_LARGE,
    /** The cursor is older than a year: pull everything again. */
    CURSOR_TOO_OLD,
    SERVER,
    UNKNOWN,
}
