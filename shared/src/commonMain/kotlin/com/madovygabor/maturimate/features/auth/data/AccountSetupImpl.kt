package com.madovygabor.maturimate.features.auth.data

import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.auth.domain.AccountRepository
import com.madovygabor.maturimate.features.auth.domain.AccountSetup
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.domain.AuthRepository
import com.madovygabor.maturimate.features.auth.domain.ConsentType
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * [AccountSetup] over the local user database and the account's rows on the server. In the app
 * [dispatcher] is the user database's one-at-a-time dispatcher, so moving the guest's rows cannot
 * overlap a save; the network calls suspend without holding it.
 */
@OptIn(ExperimentalTime::class)
class AccountSetupImpl(
    private val auth: AuthRepository,
    private val account: AccountRepository,
    private val profiles: UserProfileRepository,
    private val localUser: LocalUserProvider,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AccountSetup {

    // Both run to the end even when the screen that started them goes away: saving a new app
    // language or the end of the onboarding rebuilds the screens while the work is still running.
    override suspend fun completeSignUp(firstName: String, lastName: String, marketing: Boolean, finishedAt: Instant) =
        withContext(dispatcher + NonCancellable) { signUp(firstName, lastName, marketing, finishedAt) }

    override suspend fun completeSignIn() = withContext(dispatcher + NonCancellable) { signIn() }

    private suspend fun signUp(firstName: String, lastName: String, marketing: Boolean, finishedAt: Instant): OperationResult<Unit, AuthError> {
        val accountId = auth.currentUser()?.id ?: return OperationResult.Error(AuthError.INVALID_CREDENTIALS)
        adopt(accountId)
        if (profiles.saveNames(firstName, lastName) is OperationResult.Error) return OperationResult.Error(AuthError.UNKNOWN)
        val profile = profiles.profile.first() ?: return OperationResult.Error(AuthError.UNKNOWN)
        val uploaded = account.uploadProfile(profile, firstName, lastName)
        if (uploaded is OperationResult.Error) return uploaded
        val consents = setOf(ConsentType.TERMS, ConsentType.PRIVACY, ConsentType.AGE_16) +
            if (marketing) setOf(ConsentType.MARKETING) else emptySet()
        val recorded = account.recordConsents(consents)
        if (recorded is OperationResult.Error) return recorded
        // only a complete upload counts as finished on the server
        val completed = account.completeOnboarding(finishedAt)
        if (completed is OperationResult.Error) return completed
        // last: finishing the onboarding takes the app to the home screen
        if (profiles.completeOnboarding(finishedAt) is OperationResult.Error) return OperationResult.Error(AuthError.UNKNOWN)
        return OperationResult.Success(Unit)
    }

    private suspend fun signIn(): OperationResult<Boolean, AuthError> {
        val accountId = auth.currentUser()?.id ?: return OperationResult.Error(AuthError.INVALID_CREDENTIALS)
        adopt(accountId)
        val remote = when (val result = account.downloadProfile()) {
            is OperationResult.Success -> result.data
            is OperationResult.Error -> return OperationResult.Error(result.error)
        } ?: return OperationResult.Success(false)
        if (!write(remote.profile) || profiles.saveNames(remote.firstName, remote.lastName) is OperationResult.Error) {
            return OperationResult.Error(AuthError.UNKNOWN)
        }
        return OperationResult.Success(remote.profile.isOnboardingCompleted)
    }

    private fun adopt(accountId: String) = localUser.adoptAccount(accountId)

    /** The account's answers from the server replace the ones on this phone; false when a save fails. */
    private suspend fun write(profile: UserProfile): Boolean {
        val saves = buildList {
            profile.teachingLanguage?.let { add(profiles.saveTeachingLanguage(it)) }
            profile.role?.let { add(profiles.saveRole(it)) }
            add(profiles.saveSubjects(profile.subjects))
            profile.graduationDate?.let { add(profiles.saveGraduationDate(it)) }
            profile.dailyGoalMinutes?.let { add(profiles.saveDailyGoal(it)) }
            add(profiles.saveReminder(profile.reminderEnabled, profile.reminderTime))
            profile.onboardingCompletedAt?.let { add(profiles.completeOnboarding(it)) }
        }
        return saves.none { it is OperationResult.Error }
    }
}
