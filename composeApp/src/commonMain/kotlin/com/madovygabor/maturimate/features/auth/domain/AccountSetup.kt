package com.madovygabor.maturimate.features.auth.domain

import com.madovygabor.maturimate.core.domain.error.OperationResult
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

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
     *
     * @return Whether the account has finished the onboarding before.
     */
    suspend fun completeSignIn(): OperationResult<Boolean, AuthError>
}
