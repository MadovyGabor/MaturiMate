package com.madovygabor.maturimate.features.auth.data

import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.auth.domain.AccountRepository
import com.madovygabor.maturimate.features.auth.domain.AccountSetup
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.domain.AuthRepository
import com.madovygabor.maturimate.features.auth.domain.ConsentType
import com.madovygabor.maturimate.features.auth.domain.SignedInAccount
import com.madovygabor.maturimate.features.sync.domain.SyncEngine
import com.madovygabor.maturimate.features.sync.domain.SyncOutcome
import com.madovygabor.maturimate.features.sync.domain.SyncReason
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
    /** With the sync, a sign-in brings the account's data by a guest-merge sync instead of the old profile download. */
    private val sync: SyncEngine? = null,
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
        val consents = setOf(ConsentType.TERMS, ConsentType.PRIVACY, ConsentType.AGE_16) +
            if (marketing) setOf(ConsentType.MARKETING) else emptySet()
        // a guest who signs up later, from the profile, finished the onboarding long ago: that day stays
        val onboardingEnd = profile.onboardingCompletedAt ?: finishedAt
        // one transaction on the server: all of it is saved, or nothing
        val saved = account.saveSignUp(profile, firstName, lastName, consents, onboardingEnd)
        if (saved is OperationResult.Error) return saved
        // last: finishing the onboarding takes the app to the home screen
        if (profile.onboardingCompletedAt == null && profiles.completeOnboarding(finishedAt) is OperationResult.Error) {
            return OperationResult.Error(AuthError.UNKNOWN)
        }
        return OperationResult.Success(Unit)
    }

    private suspend fun signIn(): OperationResult<SignedInAccount, AuthError> {
        val accountId = auth.currentUser()?.id ?: return OperationResult.Error(AuthError.INVALID_CREDENTIALS)
        adopt(accountId)
        if (sync != null) return signInWithSync(sync)
        val remote = when (val result = account.downloadProfile()) {
            is OperationResult.Success -> result.data
            is OperationResult.Error -> return OperationResult.Error(result.error)
        }
        if (remote != null && (!write(remote.profile) || profiles.saveNames(remote.firstName, remote.lastName) is OperationResult.Error)) {
            return OperationResult.Error(AuthError.UNKNOWN)
        }
        // read after the writes, so the onboarding continues from these answers and not from older ones
        val local = profiles.profile.first()
        return OperationResult.Success(SignedInAccount(remote?.profile?.isOnboardingCompleted == true, local))
    }

    /**
     * The guest's rows are the account's now (adopt); one sync in the guest-merge mode adds the guest's learning data to the
     * account and brings the account's profile, settings, roles and subjects back, which win over the guest's (roadmap
     * 12.5). When it fails (no network) the mode stays set and the next sync does it; the sign-in reports the error.
     */
    private suspend fun signInWithSync(sync: SyncEngine): OperationResult<SignedInAccount, AuthError> {
        val outcome = sync.sync(SyncReason.AFTER_SIGN_IN)
        if (outcome is SyncOutcome.Failed) return OperationResult.Error(AuthError.NETWORK)
        // read after the sync, so the onboarding continues from the account's answers
        val local = profiles.profile.first()
        return OperationResult.Success(SignedInAccount(local?.isOnboardingCompleted == true, local))
    }

    private fun adopt(accountId: String) = localUser.adoptAccount(accountId)

    /** The account's answers from the server replace the ones on this phone; false when a save fails. */
    private suspend fun write(profile: UserProfile): Boolean {
        val saves = buildList {
            profile.teachingLanguage?.let {
                add(profiles.saveTeachingLanguage(it))
                // the app language may differ from the school's (set apart in the settings)
                add(profiles.saveUiLanguage(profile.uiLanguage))
            }
            profile.role?.let { add(profiles.saveRole(it)) }
            // a server profile without subjects never replaces the ones chosen on this phone
            if (profile.subjects.isNotEmpty()) add(profiles.saveSubjects(profile.subjects))
            profile.graduationDate?.let { add(profiles.saveGraduationDate(it)) }
            profile.dailyGoalMinutes?.let { add(profiles.saveDailyGoal(it)) }
            add(profiles.saveReminder(profile.reminderEnabled, profile.reminderTime))
            profile.onboardingCompletedAt?.let { add(profiles.completeOnboarding(it)) }
        }
        return saves.none { it is OperationResult.Error }
    }
}
