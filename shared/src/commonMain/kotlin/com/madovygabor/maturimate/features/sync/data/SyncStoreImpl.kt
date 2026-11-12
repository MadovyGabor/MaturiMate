package com.madovygabor.maturimate.features.sync.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.database.user.Exam_date
import com.madovygabor.maturimate.database.user.Sync_state
import com.madovygabor.maturimate.database.user.User_role
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.sync.domain.PendingChanges
import com.madovygabor.maturimate.features.sync.domain.StoredSyncState
import com.madovygabor.maturimate.features.sync.domain.SyncActivity
import com.madovygabor.maturimate.features.sync.domain.SyncAnswer
import com.madovygabor.maturimate.features.sync.domain.SyncAttempt
import com.madovygabor.maturimate.features.sync.domain.SyncChanges
import com.madovygabor.maturimate.features.sync.domain.SyncError
import com.madovygabor.maturimate.features.sync.domain.SyncProfile
import com.madovygabor.maturimate.features.sync.domain.SyncResponse
import com.madovygabor.maturimate.features.sync.domain.SyncSettings
import com.madovygabor.maturimate.features.sync.domain.SyncStore
import com.madovygabor.maturimate.features.sync.domain.SyncStreak
import com.madovygabor.maturimate.features.sync.domain.SyncSubject
import com.madovygabor.maturimate.features.sync.domain.SyncUnitProgress
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * [SyncStore] over the user database. Every call runs on the dispatcher of the user database (one at a time), and the
 * answer of the server is applied in one transaction: a failing row rolls everything back, and the cursor moves in the
 * same transaction. The merge rules are the ones of the server (plan-12 section 4), written as `INSERT OR IGNORE` and a
 * conditional `UPDATE` (the SQLite of Android 8 has no UPSERT).
 */
class SyncStoreImpl(
    private val database: UserDatabase,
    private val localUser: LocalUserProvider,
    private val dispatcher: CoroutineDispatcher,
) : SyncStore {

    private val q get() = database.syncQueries
    private val profileQ get() = database.profileQueries
    private val activityQ get() = database.activityQueries
    private val progressQ get() = database.progressQueries
    private val examQ get() = database.examDateQueries

    override suspend fun state(): StoredSyncState = withContext(dispatcher) {
        val userId = localUser.userId()
        q.ensureSyncState(userId)
        val row = q.selectSyncState().executeAsOne()
        // the state belongs to one user: after another account signed in on this phone it starts again
        if (row.user_id != userId) {
            q.clearSyncState()
            q.ensureSyncState(userId)
            return@withContext q.selectSyncState().executeAsOne().toStored()
        }
        row.toStored()
    }

    override suspend fun pending(since: Long?, full: Boolean, maxRounds: Int): PendingChanges = withContext(dispatcher) {
        val userId = localUser.userId()
        val from = if (full) -1L else (since ?: -1L)
        var newest: Long? = null
        fun seen(time: Long?) {
            if (time != null && (newest == null || time > newest!!)) newest = time
        }

        val profile = q.selectChangedProfile(userId, from).executeAsOneOrNull()?.let {
            seen(it.updated_at)
            SyncProfile(
                firstName = it.first_name, lastName = it.last_name, teachingLanguage = it.teaching_language,
                graduationDate = it.graduation_date, onboardingCompletedAt = it.onboarding_completed_at,
                progressResetAt = it.progress_reset_at, roleId = q.selectOnboardingRoleId(userId).executeAsOne().MIN,
                updatedAt = it.updated_at,
            )
        }
        val settings = q.selectChangedSettings(userId, from).executeAsOneOrNull()?.let {
            seen(it.updated_at)
            SyncSettings(it.ui_language, it.daily_goal_minutes, it.reminder_enabled, it.reminder_time, it.updated_at)
        }
        val subjects = q.selectChangedSubjects(userId, from).executeAsList().map {
            seen(it.updated_at)
            SyncSubject(it.subject_id, it.source, it.level, it.added_at, it.removed_at, it.updated_at)
        }

        val rounds = q.selectFinishedAttemptsSince(userId, from, (maxRounds + 1).toLong()).executeAsList()
        val moreRounds = rounds.size > maxRounds
        val sent = rounds.take(maxRounds)
        val answers = if (sent.isEmpty()) emptyMap()
        else q.selectAnswersOfAttempts(sent.map { it.attempt_id }).executeAsList().groupBy { it.attempt_id }
        val attempts = sent.map { a ->
            SyncAttempt(
                attemptId = a.attempt_id, attemptType = a.attempt_type, topicId = a.topic_id, learningUnitId = a.learning_unit_id,
                status = a.status, startedAt = a.started_at, finishedAt = a.finished_at, earnedPoints = a.earned_points,
                maxPoints = a.max_points,
                answers = answers[a.attempt_id].orEmpty().map {
                    SyncAnswer(it.task_id, it.is_correct, it.earned_points, it.max_points, it.answered_at, it.given_answer)
                },
            )
        }
        val newestRound = sent.maxOfOrNull { it.finished_at ?: it.started_at }
        seen(newestRound)

        val units = q.selectChangedUnitProgress(userId, from).executeAsList().map {
            seen(it.updated_at)
            SyncUnitProgress(it.learning_unit_id, it.status, it.started_at, it.completed_at, it.updated_at)
        }
        val streak = q.selectChangedStreak(userId, from).executeAsOneOrNull()?.let {
            seen(it.updated_at)
            SyncStreak(it.current_streak, it.longest_streak, it.last_activity_date, it.updated_at)
        }
        val activity = q.selectChangedActivity(userId, localUser.deviceId(), from).executeAsList().map {
            seen(it.updated_at)
            SyncActivity(it.activity_date, it.device_id, it.seconds_studied, it.tasks_answered, it.updated_at)
        }

        PendingChanges(
            changes = SyncChanges(profile, settings, subjects, attempts, units, streak, activity),
            // when rounds wait, the mark stops at the newest round sent: the other tables are simply sent again
            pushedUpTo = if (moreRounds) newestRound else newest,
            moreRounds = moreRounds,
        )
    }

    override suspend fun apply(response: SyncResponse, guestMerge: Boolean, pushedUpTo: Long?, now: Long) {
        withContext(dispatcher) {
            val userId = localUser.userId()
            database.transaction {
                var answersChanged = false

                // 1. profile: a newer reset marker deletes the rounds and units before it, then the newer change wins
                response.profile?.let { remote ->
                    val local = q.selectChangedProfile(userId, -1L).executeAsOneOrNull()
                    // a phone without a profile row takes the server's as it is (a row made empty here would look newer)
                    q.applyProfileInsert(userId, remote.firstName.orEmpty(), remote.lastName.orEmpty(), remote.teachingLanguage,
                        remote.graduationDate, remote.onboardingCompletedAt, null, remote.updatedAt)
                    val remoteReset = remote.progressResetAt
                    val localReset = local?.progress_reset_at
                    if (remoteReset != null && (localReset == null || localReset < remoteReset)) {
                        q.deleteAnswersUpTo(userId, remoteReset)
                        q.deleteAttemptsUpTo(userId, remoteReset)
                        q.deleteUnitProgressUpTo(userId, remoteReset)
                        q.clearTaskProgress(userId)
                        answersChanged = true
                    }
                    q.applyProfile(
                        remote.firstName.orEmpty(), remote.lastName.orEmpty(), remote.teachingLanguage, remote.graduationDate,
                        remote.onboardingCompletedAt, remote.updatedAt, userId, guestMerge,
                    )
                    if (remoteReset != null) q.applyResetMarker(remoteReset, remote.updatedAt, userId)
                }
                // 2. settings (without the time zone)
                response.settings?.let { remote ->
                    q.applySettingsInsert(userId, remote.uiLanguage, remote.dailyGoalMinutes, remote.reminderEnabled, remote.reminderTime, remote.updatedAt)
                    q.applySettings(remote.uiLanguage, remote.dailyGoalMinutes, remote.reminderEnabled, remote.reminderTime, remote.updatedAt, userId, guestMerge)
                }
                // 3. roles: replaced by the server's list, when the profile came with it
                if (response.profile != null && response.roles.isNotEmpty()) {
                    profileQ.deleteRoles(userId)
                    response.roles.forEach { profileQ.insertRole(User_role(userId, it, now)) }
                }
                // 4. subjects
                response.subjects.forEach { s ->
                    q.applySubjectInsert(userId, s.subjectId, s.source, s.level, s.addedAt, s.removedAt, s.updatedAt)
                    q.applySubject(s.source, s.level, s.removedAt, s.addedAt, s.updatedAt, userId, s.subjectId, guestMerge)
                }
                // 5. rounds and answers (a finished round never changes: the first one wins)
                response.attempts.forEach { a ->
                    q.applyAttempt(a.attemptId, userId, a.attemptType, a.topicId, a.learningUnitId, a.status, a.startedAt, a.finishedAt, a.earnedPoints, a.maxPoints)
                    a.answers.forEach {
                        q.applyAnswer(a.attemptId, it.taskId, userId, it.isCorrect, it.earnedPoints, it.maxPoints, it.answeredAt, it.givenAnswer)
                        answersChanged = true
                    }
                }
                // 6. unit progress
                response.unitProgress.forEach { u ->
                    q.applyUnitProgressInsert(userId, u.learningUnitId, u.status, u.startedAt, u.completedAt, u.updatedAt)
                    q.applyUnitProgress(u.status, u.startedAt, u.completedAt, u.updatedAt, userId, u.learningUnitId)
                }
                // 7. streak
                response.streak?.let { remote ->
                    q.applyStreakInsert(userId, remote.currentStreak, remote.longestStreak, remote.lastActivityDate, remote.updatedAt)
                    val local = activityStreak(userId)
                    val merged = mergeStreak(local, remote)
                    q.applyStreak(merged.currentStreak, merged.longestStreak, merged.lastActivityDate, merged.updatedAt, userId)
                }
                // 8. minutes of the days (all devices)
                response.activity.forEach { a ->
                    q.applyActivityInsert(userId, a.activityDate, a.deviceId, a.secondsStudied, a.tasksAnswered, a.updatedAt)
                    q.applyActivity(a.secondsStudied, a.tasksAnswered, a.updatedAt, userId, a.activityDate, a.deviceId)
                }
                // 9. the cache of the tasks follows the answers
                if (answersChanged) {
                    q.clearTaskProgress(userId)
                    q.rebuildTaskProgress(userId)
                }
                // 10. the official exam dates, whole; written only when they changed (a write reloads the home screen)
                val examDates = response.examDates.map { Exam_date(it.examDateId, it.examYear, it.subjectId, it.examOn, it.publishedAt, it.note) }
                if (examQ.selectAll().executeAsList().sortedBy { it.exam_date_id } != examDates.sortedBy { it.exam_date_id }) {
                    examQ.deleteAll()
                    examDates.forEach { examQ.insert(it) }
                }
                // 11. the state: the push mark, the cursor, the time; a guest merge is over
                q.ensureSyncState(userId)
                q.markPushed(pushedUpTo)
                q.markSynced(response.cursor, now, response.rejected.size.toLong())
                if (guestMerge) q.setGuestMergePending(false)
            }
        }
    }

    // the local streak row after the insert-or-ignore above: the same as the remote one when there was none before
    private fun activityStreak(userId: String): SyncStreak? =
        q.selectChangedStreak(userId, -1L).executeAsOneOrNull()?.let {
            SyncStreak(it.current_streak, it.longest_streak, it.last_activity_date, it.updated_at)
        }

    override suspend fun markPage(pushedUpTo: Long?) {
        withContext(dispatcher) {
            localUser.userId()
            q.markPushed(pushedUpTo)
        }
    }

    override suspend fun markFailed(error: SyncError) {
        withContext(dispatcher) { q.markFailed(error.name) }
    }

    override suspend fun requestFullPush() {
        withContext(dispatcher) { q.requestFullPush() }
    }

    override suspend fun pendingSummary(): Pair<Int, Long?> = withContext(dispatcher) {
        val userId = localUser.userId()
        val since = q.selectSyncState().executeAsOneOrNull()?.last_push_at ?: -1L
        q.countPendingRounds(userId, since).executeAsOne().toInt() to q.oldestPendingRound(userId, since).executeAsOne().MIN
    }

    override suspend fun clear() {
        withContext(dispatcher) { q.clearSyncState() }
    }

    override fun changes(): Flow<Unit> = q.selectSyncState().asFlow().mapToOneOrNull(dispatcher).map { }

    private fun Sync_state.toStored() = StoredSyncState(
        userId = user_id, cursor = pull_cursor, lastPushAt = last_push_at, lastSuccessAt = last_success_at,
        fullPushPending = full_push_pending, guestMergePending = guest_merge_pending, rejectedCount = rejected_count.toInt(),
    )
}

/**
 * The streak rule of the server: the longest is kept; the current one comes from the row with the later day, and the
 * larger of the two when the day is the same.
 */
internal fun mergeStreak(local: SyncStreak?, remote: SyncStreak): SyncStreak {
    if (local == null) return remote
    val current = when {
        local.lastActivityDate == null || (remote.lastActivityDate != null && remote.lastActivityDate > local.lastActivityDate) -> remote.currentStreak
        remote.lastActivityDate == local.lastActivityDate -> maxOf(local.currentStreak, remote.currentStreak)
        else -> local.currentStreak
    }
    val last = listOfNotNull(local.lastActivityDate, remote.lastActivityDate).maxOrNull()
    return SyncStreak(
        currentStreak = current,
        longestStreak = maxOf(local.longestStreak, remote.longestStreak, current),
        lastActivityDate = last,
        updatedAt = maxOf(local.updatedAt, remote.updatedAt),
    )
}
