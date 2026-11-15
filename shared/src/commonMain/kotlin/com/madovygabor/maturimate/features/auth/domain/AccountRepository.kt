package com.madovygabor.maturimate.features.auth.domain

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** What a user accepted at sign-up (`user_consent.consent_type`). */
enum class ConsentType { TERMS, PRIVACY, AGE_16, MARKETING }

/** The signed-in account as Supabase Auth knows it; the names come from Google when it was used. */
data class AccountUser(
    val id: String,
    val email: String?,
    val firstName: String?,
    val lastName: String?,
    /** False for an account made with Google only: it has no password to change. */
    val hasPassword: Boolean = true,
)

/** The account's profile on the server, for a sign-in on a new phone. */
data class RemoteProfile(
    val firstName: String,
    val lastName: String,
    val profile: UserProfile,
    /** Whether the account has an active (not withdrawn) TERMS consent: only `save_sign_up` stores it. */
    val acceptedTerms: Boolean = false,
) {
    /**
     * The account finished its sign-up: the onboarding end AND an active TERMS consent. A finished profile alone may come
     * from a guest's sync before the sign-up was saved; such an account still needs `save_sign_up` (and its consents).
     * The same rule as the server's guard in `save_sign_up` (migration 20261127120000).
     */
    val isSetUp: Boolean get() = profile.isOnboardingCompleted && acceptedTerms
}

/**
 * The signed-in account's own rows on the server (Supabase, row level security). Only the
 * account's profile, settings, role, subjects and consents; progress sync comes in Milestone 11.
 */
@OptIn(ExperimentalTime::class)
interface AccountRepository {
    /**
     * Writes everything a new account sends in one transaction on the server (`save_sign_up`):
     * the profile and the onboarding answers, the [consents] with the version of the documents,
     * and the end of the onboarding at [finishedAt]. Either all of it is saved or nothing, so a
     * sign-up cannot stop half way; a retry stores each consent once.
     */
    suspend fun saveSignUp(
        profile: UserProfile,
        firstName: String,
        lastName: String,
        consents: Set<ConsentType>,
        finishedAt: Instant,
    ): OperationResult<Unit, AuthError>

    /** A new name of the signed-in account, on the server. */
    suspend fun saveNames(firstName: String, lastName: String): OperationResult<Unit, AuthError>

    /** The signed-in account's profile; null when it has none on the server yet. */
    suspend fun downloadProfile(): OperationResult<RemoteProfile?, AuthError>

    /**
     * Deletes the signed-in account on the server at once and for good (`delete_my_account`): the proof of the consents is
     * kept in the archive, everything else of the user goes. The call goes out with [accessToken], the session of the proof
     * (a password check or a fresh Google sign-in of the last minutes): the server deletes only for a sign-in of the last
     * 5 minutes in that token, else [AuthError.PROOF_NEEDED] (migration 20261128120000).
     */
    suspend fun deleteAccount(accessToken: String): OperationResult<Unit, AuthError> = OperationResult.Error(AuthError.UNKNOWN)

    /** A JSON copy of every row the server keeps about the signed-in user (`export_my_data`). */
    suspend fun exportMyData(): OperationResult<String, AuthError> = OperationResult.Error(AuthError.UNKNOWN)
}
