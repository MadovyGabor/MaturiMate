package com.madovygabor.maturimate.features.auth.domain

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** The account on this phone after a sign-in. */
data class SignedInAccount(
    /** Whether the account finished the onboarding before: it goes home. */
    val onboardingFinished: Boolean,
    /** The answers on this phone after the sign-in, to continue the onboarding from. */
    val profile: UserProfile?,
    /**
     * The account finished its sign-up: the onboarding end AND an active TERMS consent (the rule of
     * [RemoteProfile.isSetUp] and of the server's guard). A finished profile without TERMS (pushed by a sync before
     * `save_sign_up` went through) is not set up: it still needs the account form and its consents.
     */
    val isSetUp: Boolean,
)

/** What happens on this phone and on the server after a sign-up or a sign-in. */
interface AccountSetup {
    /**
     * After a new account is confirmed (code or Google): the guest's answers become the
     * account's, the name is saved, the profile (finished at [finishedAt]) is written to the
     * server and the consents are recorded. The onboarding is marked finished on this phone last,
     * so the switch to the home screen comes after everything is saved.
     */
    @OptIn(ExperimentalTime::class)
    suspend fun completeSignUp(firstName: String, lastName: String, marketing: Boolean, finishedAt: Instant): OperationResult<Unit, AuthError>

    /**
     * After signing in to an existing account: the guest's rows become the account's, and the
     * account's profile on the server, if it has one, replaces the answers on this phone.
     */
    suspend fun completeSignIn(): OperationResult<SignedInAccount, AuthError>
}
