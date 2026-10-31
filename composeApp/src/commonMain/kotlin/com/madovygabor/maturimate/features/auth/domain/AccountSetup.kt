package com.madovygabor.maturimate.features.auth.domain

import com.madovygabor.maturimate.core.domain.error.OperationResult

/** What happens on this phone and on the server after a sign-up or a sign-in. */
interface AccountSetup {
    /**
     * After a new account is confirmed (code or Google): the guest's answers become the
     * account's, the name is saved, the profile is written to the server and the consents
     * are recorded.
     */
    suspend fun completeSignUp(firstName: String, lastName: String, marketing: Boolean): OperationResult<Unit, AuthError>

    /**
     * After signing in to an existing account: the guest's rows become the account's, and the
     * account's profile on the server, if it has one, replaces the answers on this phone.
     *
     * @return Whether the account has finished the onboarding before.
     */
    suspend fun completeSignIn(): OperationResult<Boolean, AuthError>
}
