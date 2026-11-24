package com.madovygabor.maturimate.features.auth.domain

import com.madovygabor.maturimate.core.domain.error.OperationResult

/** What the settings change on the signed-in account: the name, the password, the sign-out, the deletion and the export. */
interface AccountSettings {
    /** The new name on this phone; the sync takes it to the server (an account's name is a profile field like the others). */
    suspend fun saveNames(firstName: String, lastName: String): OperationResult<Unit, AuthError>

    /**
     * Sends a 6-digit code to the account's e-mail address: half of the proof for [changePassword] (Supabase
     * reauthentication). [AuthError.SESSION_EXPIRED] without a session.
     */
    suspend fun sendPasswordCode(): OperationResult<Unit, AuthError>

    /**
     * The new password, proved by BOTH the [oldPassword] (checked by the server without a second session on this phone)
     * and the [code] from [sendPasswordCode]: GoTrue checks the code only on sessions older than 24 hours, so the code
     * alone would not prove anything on a fresh one. A wrong old password is [AuthError.INVALID_CREDENTIALS], too many tries
     * [AuthError.TOO_MANY_REQUESTS]; the new one must follow [AccountRules.isStrongPassword] ([AuthError.WEAK_PASSWORD]);
     * a wrong or expired code is [AuthError.INVALID_CODE], the old password again [AuthError.SAME_PASSWORD]. An account
     * without a password (Google) has nothing to change.
     */
    suspend fun changePassword(oldPassword: String, code: String, newPassword: String): OperationResult<Unit, AuthError>

    /**
     * Uploads what is not uploaded yet, signs out and removes every local row of the user. The app then starts again with
     * the welcome screen. When the upload does not go through ([AuthError.NETWORK]) nothing is removed, unless [force]:
     * then the part not uploaded is lost. Without a network the session is ended on the server later.
     */
    suspend fun signOut(force: Boolean = false): OperationResult<Unit, AuthError>

    /**
     * Deletes the account on the server for good, then this phone's rows and session: the app starts again with the
     * welcome screen. It needs the [proof] that the user is the account's owner (the password of an account that has one,
     * a fresh Google sign-in of an account that has none); without it nothing is deleted ([AuthError.PROOF_NEEDED], a wrong
     * password [AuthError.INVALID_CREDENTIALS]). Needs the network; without it nothing is deleted.
     */
    suspend fun deleteAccount(proof: DeletionProof): OperationResult<Unit, AuthError> = OperationResult.Error(AuthError.UNKNOWN)

    /**
     * Holds the sync and remembers the session open now, right before the Google sign-in that proves the deletion of an
     * account without a password. On an error the Google sign-in must not start (the hold is released already).
     */
    suspend fun startFreshSignInProof(): OperationResult<Unit, AuthError> = OperationResult.Error(AuthError.UNKNOWN)

    /**
     * After that Google sign-in: success when it opened a new session of the same account (then [DeletionProof.FreshSignIn]
     * holds for a few minutes). Another account is refused ([AuthError.WRONG_ACCOUNT]): its session ends and this phone's
     * account stays signed in, its data untouched. Releases the sync.
     */
    suspend fun finishFreshSignInProof(): OperationResult<Unit, AuthError> = OperationResult.Error(AuthError.UNKNOWN)

    /**
     * The Google sign-in failed or was closed: another account's session that came in meanwhile is ended and this phone's
     * put back; the sync is released.
     */
    suspend fun cancelFreshSignInProof() {}

    /**
     * An account's phone whose open session belongs to another account (left by a proof that never finished): that session
     * is ended (this device's scope; later when offline) and nothing of the phone's data is touched. True when it did so.
     * Does nothing while a proof runs.
     */
    suspend fun endForeignSession(): Boolean = false

    /**
     * Whether this phone is a guest's (its rows were never an account's), the rule of the sync too. It can be while a
     * session is open: an account that is not set up is signed in without taking the phone over. Then the settings are a
     * guest's, and a sign-out ends the session only.
     */
    suspend fun isGuestPhone(): Boolean = false

    /** Whether the export can also be shared (the platform's share sheet), not only saved to a file. */
    val canShareExport: Boolean get() = false

    /**
     * A JSON copy of the user's data on the server, handed to [ExportSaver] (saved to a file, or with [share] to the share
     * sheet); returns where it went, [ExportSaver.CANCELLED] when the user closed the picker.
     */
    suspend fun exportData(share: Boolean = false): OperationResult<String, AuthError> = OperationResult.Error(AuthError.UNKNOWN)
}

/** How the user proves to be the account's owner before the account is deleted. */
sealed interface DeletionProof {
    /** The account's password; checked by the server, never kept. */
    class Password(val password: String) : DeletionProof {
        override fun toString() = "Password(…)"
    }

    /** A Google sign-in of the same account made just before ([AccountSettings.finishFreshSignInProof]). */
    data object FreshSignIn : DeletionProof
}
