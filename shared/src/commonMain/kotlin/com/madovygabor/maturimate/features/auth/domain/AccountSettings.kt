package com.madovygabor.maturimate.features.auth.domain

import com.madovygabor.maturimate.core.domain.error.OperationResult

/** What the settings change on the signed-in account: the name, the password, and the sign-out. */
interface AccountSettings {
    /** The new name on this phone; the sync takes it to the server (an account's name is a profile field like the others). */
    suspend fun saveNames(firstName: String, lastName: String): OperationResult<Unit, AuthError>

    /**
     * The password, after the old one is checked by a sign-in with it ([AuthError.INVALID_CREDENTIALS] when it is
     * wrong). The new one must follow [AccountRules.isStrongPassword].
     */
    suspend fun changePassword(oldPassword: String, newPassword: String): OperationResult<Unit, AuthError>

    /**
     * Uploads what is not uploaded yet, signs out and removes every local row of the user. The app then starts again with
     * the welcome screen. When the upload does not go through ([AuthError.NETWORK]) nothing is removed, unless [force]:
     * then the part not uploaded is lost. Without a network the session is ended on the server later.
     */
    suspend fun signOut(force: Boolean = false): OperationResult<Unit, AuthError>

    /**
     * Deletes the account on the server for good, then this phone's rows and session: the app starts again with the
     * welcome screen. Needs the network; without it nothing is deleted.
     */
    suspend fun deleteAccount(): OperationResult<Unit, AuthError> = OperationResult.Error(AuthError.UNKNOWN)

    /** A JSON copy of the user's data on the server, handed to [ExportSaver]; returns where it went. */
    suspend fun exportData(): OperationResult<String, AuthError> = OperationResult.Error(AuthError.UNKNOWN)
}

/** Hands the exported file to the user: the share sheet on Android, a file in the home folder on the desktop. */
fun interface ExportSaver {
    /** Returns where the file went (shown to the user), or null when it could not be saved. */
    suspend fun save(fileName: String, text: String): String?
}
