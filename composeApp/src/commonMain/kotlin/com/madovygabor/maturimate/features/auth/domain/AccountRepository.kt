package com.madovygabor.maturimate.features.auth.domain

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile

/** What a user accepted at sign-up (`user_consent.consent_type`). */
enum class ConsentType { TERMS, PRIVACY, AGE_16, MARKETING }

/** The signed-in account as Supabase Auth knows it; the names come from Google when it was used. */
data class AccountUser(
    val id: String,
    val email: String?,
    val firstName: String?,
    val lastName: String?,
)

/** The account's profile on the server, for a sign-in on a new phone. */
data class RemoteProfile(
    val firstName: String,
    val lastName: String,
    val profile: UserProfile,
)

/**
 * The signed-in account's own rows on the server (Supabase, row level security). Only the
 * account's profile, settings, role, subjects and consents; progress sync comes in Milestone 10.
 */
interface AccountRepository {
    /** Writes the profile and the onboarding answers under the signed-in account. */
    suspend fun uploadProfile(profile: UserProfile, firstName: String, lastName: String): OperationResult<Unit, AuthError>

    /** Records what the user accepted, with the version of the documents. */
    suspend fun recordConsents(consents: Set<ConsentType>): OperationResult<Unit, AuthError>

    /** The signed-in account's profile; null when it has none on the server yet. */
    suspend fun downloadProfile(): OperationResult<RemoteProfile?, AuthError>
}
