package com.madovygabor.maturimate.features.auth.domain

import com.madovygabor.maturimate.core.domain.error.OperationResult

/** What the settings change on the signed-in account: the name, the password, and the sign-out. */
interface AccountSettings {
    /** The new name on the server, then on this phone. */
    suspend fun saveNames(firstName: String, lastName: String): OperationResult<Unit, AuthError>

    /**
     * The password, after the old one is checked by a sign-in with it ([AuthError.INVALID_CREDENTIALS] when it is
     * wrong). The new one must follow [AccountRules.isStrongPassword].
     */
    suspend fun changePassword(oldPassword: String, newPassword: String): OperationResult<Unit, AuthError>

    /**
     * Signs out and removes every local row of the user (until the sync there is no copy to keep, DECISIONS
     * 2026-11-04). The app then starts again with the welcome screen.
     */
    suspend fun signOut(): OperationResult<Unit, AuthError>
}
