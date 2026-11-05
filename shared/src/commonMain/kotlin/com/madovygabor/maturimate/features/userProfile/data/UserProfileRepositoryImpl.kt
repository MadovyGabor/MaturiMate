package com.madovygabor.maturimate.features.userProfile.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.database.user.SelectUserProfile
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.database.user.User_role
import com.madovygabor.maturimate.database.user.User_subject
import com.madovygabor.maturimate.features.userProfile.domain.DataError
import com.madovygabor.maturimate.features.userProfile.domain.SubjectLevel
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import com.madovygabor.maturimate.features.userProfile.domain.UserSubject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * SQLDelight implementation of [UserProfileRepository] over the `user_profile`,
 * `user_settings`, `user_role` and `user_subject` rows of the local user.
 *
 * The first save creates both rows; the time zone comes from the phone.
 */
@OptIn(ExperimentalTime::class)
class UserProfileRepositoryImpl(
    private val database: UserDatabase,
    private val localUser: LocalUserProvider,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val timeZone: () -> String = { TimeZone.currentSystemDefault().id },
    private val now: () -> Instant = { Clock.System.now() },
) : UserProfileRepository {

    private val queries get() = database.profileQueries

    // follows the local user, so the profile moves along when a guest signs up or signs in
    @OptIn(ExperimentalCoroutinesApi::class)
    override val profile: Flow<UserProfile?> = localUser.userIdFlow(dispatcher).flatMapLatest { userId -> profileOf(userId) }
        .flowOn(dispatcher)

    private fun profileOf(userId: String): Flow<UserProfile?> = flow {
        val profileRow = queries.selectUserProfile(userId).asFlow().mapToOneOrNull(dispatcher)
        val roleRows = queries.selectRoles(userId).asFlow().mapToList(dispatcher)
        val subjectRows = queries.selectSubjects(userId).asFlow().mapToList(dispatcher)
        emitAll(combine(profileRow, roleRows, subjectRows) { row, roles, subjects ->
            row?.toDomain(
                role = roles.firstNotNullOfOrNull { UserRole.fromId(it.role_id) },
                subjects = subjects.map { it.toDomain() },
            )
        })
    }

    override suspend fun saveTeachingLanguage(language: TeachingLanguage) = save { userId ->
        queries.updateTeachingLanguage(language.name, userId)
        queries.updateUiLanguage(language.name, userId)
    }

    override suspend fun saveSchoolLanguage(language: TeachingLanguage) = save { userId ->
        queries.updateTeachingLanguage(language.name, userId)
    }

    override suspend fun saveUiLanguage(language: TeachingLanguage) = save { userId ->
        queries.updateUiLanguage(language.name, userId)
    }

    override suspend fun saveRole(role: UserRole) = save { userId ->
        queries.deleteOnboardingRoles(userId)
        queries.insertRole(User_role(userId, role.id, now().toEpochMilliseconds()))
    }

    override suspend fun saveSubjects(subjects: List<UserSubject>) = save { userId ->
        queries.deleteSubjects(userId)
        val addedAt = now().toEpochMilliseconds()
        // added_at keeps the order of the list
        subjects.forEachIndexed { index, subject ->
            queries.insertSubject(
                User_subject(
                    user_id = userId,
                    subject_id = subject.subjectId,
                    target_score = null,
                    source = if (subject.compulsory) "AUTO" else "CHOSEN",
                    added_at = addedAt + index,
                    level = subject.level?.name,
                )
            )
        }
    }

    override suspend fun saveGraduationDate(date: LocalDate) = save { userId ->
        queries.updateGraduationDate(date.toString(), userId)
    }

    override suspend fun saveDailyGoal(minutes: Int) = save { userId ->
        queries.updateDailyGoal(minutes.toLong(), userId)
    }

    override suspend fun saveReminder(enabled: Boolean, time: LocalTime?) = save { userId ->
        // hours and minutes only: "18:00"
        queries.updateReminder(enabled, time?.let { "${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}" }, userId)
    }

    override suspend fun saveNames(firstName: String, lastName: String) = save { userId ->
        queries.updateNames(firstName, lastName, userId)
    }

    override suspend fun completeOnboarding(at: Instant) = save { userId ->
        queries.updateOnboardingCompletedAt(at.toEpochMilliseconds(), userId)
    }

    /** Creates the user's rows if needed and runs [block] in one transaction on the IO dispatcher. */
    private suspend fun save(block: (userId: String) -> Unit): OperationResult<Unit, DataError> =
        withContext(dispatcher) {
            try {
                // the guest id is created in its own transaction, so a failed save cannot roll it back
                localUser.userId()
                database.transaction {
                    // read again inside, so a sign-up that moves the guest's rows cannot come in between
                    val userId = localUser.userId()
                    queries.ensureProfile(userId)
                    queries.ensureSettings(userId, timeZone())
                    block(userId)
                }
                OperationResult.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                OperationResult.Error(e.toDataError())
            }
        }

    private fun Exception.toDataError(): DataError {
        val text = message.orEmpty()
        return if ("SQLITE_FULL" in text || "disk is full" in text) DataError.STORAGE_FULL else DataError.IO_ERROR
    }

    private fun User_subject.toDomain() = UserSubject(
        subjectId = subject_id,
        compulsory = source == "AUTO",
        level = level?.let { SubjectLevel.valueOf(it) },
    )

    private fun SelectUserProfile.toDomain(role: UserRole?, subjects: List<UserSubject>) = UserProfile(
        firstName = first_name?.takeIf { it.isNotBlank() },
        lastName = last_name?.takeIf { it.isNotBlank() },
        teachingLanguage = teaching_language?.let { TeachingLanguage.valueOf(it) },
        role = role,
        graduationDate = graduation_date?.let { LocalDate.parse(it) },
        uiLanguage = TeachingLanguage.valueOf(ui_language),
        dailyGoalMinutes = daily_goal_minutes?.toInt(),
        timezone = timezone,
        onboardingCompletedAt = onboarding_completed_at?.let { Instant.fromEpochMilliseconds(it) },
        subjects = subjects,
        reminderEnabled = reminder_enabled,
        reminderTime = reminder_time?.let { LocalTime.parse(it) },
    )
}
