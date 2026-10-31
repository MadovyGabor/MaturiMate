package com.madovygabor.maturimate.features.userProfile.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.database.user.SelectUserProfile
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.userProfile.domain.DataError
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * SQLDelight implementation of [UserProfileRepository] over the `user_profile` and
 * `user_settings` rows of the local user.
 *
 * The first save creates both rows; the time zone comes from the phone.
 */
@OptIn(ExperimentalTime::class)
class UserProfileRepositoryImpl(
    private val database: UserDatabase,
    private val localUser: LocalUserProvider,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val timeZone: () -> String = { TimeZone.currentSystemDefault().id },
) : UserProfileRepository {

    private val queries get() = database.profileQueries

    override val profile: Flow<UserProfile?> = flow {
        emitAll(queries.selectUserProfile(localUser.userId()).asFlow().mapToOneOrNull(dispatcher))
    }.map { it?.toDomain() }.flowOn(dispatcher)

    override suspend fun saveTeachingLanguage(language: TeachingLanguage) = save { userId ->
        queries.updateTeachingLanguage(language.name, userId)
        queries.updateUiLanguage(language.name, userId)
    }

    override suspend fun saveGraduationDate(date: LocalDate) = save { userId ->
        queries.updateGraduationDate(date.toString(), userId)
    }

    override suspend fun saveDailyGoal(minutes: Int) = save { userId ->
        queries.updateDailyGoal(minutes.toLong(), userId)
    }

    override suspend fun completeOnboarding(at: Instant) = save { userId ->
        queries.updateOnboardingCompletedAt(at.toEpochMilliseconds(), userId)
    }

    /** Creates the user's rows if needed and runs [block] in one transaction on the IO dispatcher. */
    private suspend fun save(block: (userId: String) -> Unit): OperationResult<Unit, DataError> =
        withContext(dispatcher) {
            try {
                val userId = localUser.userId()
                database.transaction {
                    queries.ensureProfile(userId)
                    queries.ensureSettings(userId, timeZone())
                    block(userId)
                }
                OperationResult.Success(Unit)
            } catch (e: Exception) {
                OperationResult.Error(e.toDataError())
            }
        }

    private fun Exception.toDataError(): DataError {
        val text = message.orEmpty()
        return if ("SQLITE_FULL" in text || "disk is full" in text) DataError.STORAGE_FULL else DataError.IO_ERROR
    }

    private fun SelectUserProfile.toDomain() = UserProfile(
        teachingLanguage = teaching_language?.let { TeachingLanguage.valueOf(it) },
        graduationDate = graduation_date?.let { LocalDate.parse(it) },
        uiLanguage = TeachingLanguage.valueOf(ui_language),
        dailyGoalMinutes = daily_goal_minutes?.toInt(),
        timezone = timezone,
        onboardingCompletedAt = onboarding_completed_at?.let { Instant.fromEpochMilliseconds(it) },
    )
}
