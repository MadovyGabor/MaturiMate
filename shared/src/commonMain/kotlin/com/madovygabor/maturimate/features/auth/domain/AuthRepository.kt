package com.madovygabor.maturimate.features.auth.domain

import com.madovygabor.maturimate.core.domain.error.Error
import com.madovygabor.maturimate.core.domain.error.OperationResult
import kotlinx.coroutines.flow.Flow

/** Whether someone is signed in to a MaturiMate account on this device. */
sealed interface AuthState {
    /** The stored session is still being loaded. */
    data object Loading : AuthState

    /** No account: a local guest, or signed out. */
    data object SignedOut : AuthState

    data class SignedIn(val userId: String, val email: String?) : AuthState
}

/** Errors of signing up, signing in and resetting the password. */
enum class AuthError : Error {
    /** Wrong e-mail address or password. */
    INVALID_CREDENTIALS,

    /** Signing in before the sign-up code was entered. */
    EMAIL_NOT_CONFIRMED,

    /** An account with this e-mail address already exists. */
    EMAIL_TAKEN,

    /** The password does not meet the rule (8 characters, lower and upper case letters, a digit). */
    WEAK_PASSWORD,

    /** The new password is the same as the old one. */
    SAME_PASSWORD,

    INVALID_EMAIL,

    /** The 6-digit code is wrong or has expired. */
    INVALID_CODE,

    /** The server requires a fresh sign-in / reauthentication code (session older than 24 hours). */
    REAUTHENTICATION_REQUIRED,

    /** Too many attempts or e-mails in a short time. */
    TOO_MANY_REQUESTS,

    /** No connection to the server. */
    NETWORK,

    /** The session is no longer valid on the server (expired or revoked): the user has to sign in again. */
    SESSION_EXPIRED,

    /**
     * The sign-up's save was refused because the account is set up already (`save_sign_up` answers 409, PT409
     * `account_already_set_up`). Nothing was written; the account is used through a sign-in.
     */
    ACCOUNT_ALREADY_SET_UP,

    /** A sign-in meant to prove who the user is came back with another account than the one of this phone. */
    WRONG_ACCOUNT,

    /** An action that cannot be undone (the account deletion) was asked without the proof it needs. */
    PROOF_NEEDED,

    UNKNOWN,
}

/**
 * Sign-up, sign-in and password reset with Supabase Auth.
 *
 * E-mails carry a 6-digit code that the user types into the app.
 * Google sign-in comes with the account screen (roadmap step 5.12).
 */
interface AuthRepository {
    val state: Flow<AuthState>

    /** The signed-in account, with the names Google gave when it was used; null when signed out. */
    fun currentUser(): AccountUser?

    /**
     * Creates the account and sends the sign-up code; the user is signed in by [confirmSignUp]. The app language goes
     * with it (user metadata `lang`), so the e-mail template can choose the language of the mail.
     */
    suspend fun signUp(email: String, password: String): OperationResult<Unit, AuthError>
    suspend fun confirmSignUp(email: String, code: String): OperationResult<Unit, AuthError>
    /** Sends the sign-up code again. */
    suspend fun resendSignUpCode(email: String): OperationResult<Unit, AuthError>

    suspend fun signIn(email: String, password: String): OperationResult<Unit, AuthError>
    suspend fun signOut(): OperationResult<Unit, AuthError>

    /** The refresh token of the current session, or null when signed out. */
    fun refreshToken(): String? = null

    /** Removes the session from this phone only, without telling the server (a sign-out without a network). */
    suspend fun clearLocalSession() {}

    /**
     * Ends on the server a session that was cleared locally while offline, by its own old [refreshToken], whether or not
     * another session is open on this phone now (that one is not touched). Succeeds when the session ended or the server
     * no longer knows it; an error (no network, a server failure) means it should be tried again later.
     */
    suspend fun revokeSession(refreshToken: String): OperationResult<Unit, AuthError> = OperationResult.Success(Unit)

    suspend fun sendPasswordResetCode(email: String): OperationResult<Unit, AuthError>
    /** Checks the password reset code; it signs the user in, and the code cannot be used again. */
    suspend fun verifyResetCode(email: String, code: String): OperationResult<Unit, AuthError>
    /** Sets the new password of the signed-in user, after [verifyResetCode]. */
    suspend fun setNewPassword(newPassword: String): OperationResult<Unit, AuthError>

    /** Sends a 6-digit reauthentication code to the signed-in user's e-mail address, the proof for [changePasswordWithCode]. */
    suspend fun sendReauthenticationCode(): OperationResult<Unit, AuthError> = OperationResult.Error(AuthError.UNKNOWN)

    /**
     * Sets the new password of the signed-in user, proved by the optional [code] of [sendReauthenticationCode] (needed
     * only when the server demands reauthentication, i.e. sessions older than 24 hours) and, when given, the
     * [currentPassword] (sent as GoTrue's `current_password`, checked by the server when "require current password" is on).
     * A wrong or expired code is [AuthError.INVALID_CODE], a missing required code [AuthError.REAUTHENTICATION_REQUIRED],
     * a wrong current password [AuthError.INVALID_CREDENTIALS].
     */
    suspend fun changePasswordWithCode(code: String?, newPassword: String, currentPassword: String? = null): OperationResult<Unit, AuthError> =
        OperationResult.Error(AuthError.UNKNOWN)

    /**
     * Checks the [password] of the signed-in account on the server without making it a sign-in on this phone: the session
     * open now is not changed, and the session the server opened for the check is ended at once (this device's scope).
     * Success carries the refresh token of that session when it could not be ended (the caller ends it later), else null.
     * A wrong password is [AuthError.INVALID_CREDENTIALS], too many tries (429) [AuthError.TOO_MANY_REQUESTS], no session
     * [AuthError.SESSION_EXPIRED].
     *
     * [whileOpen] gets the access token of that fresh session after the server accepted the password and before the
     * session ends: a call the server lets through only after a recent sign-in (the account deletion) goes out with it.
     * The token is never kept, shown or logged.
     */
    suspend fun verifyPassword(password: String, whileOpen: (suspend (accessToken: String) -> Unit)? = null): OperationResult<String?, AuthError> =
        OperationResult.Error(AuthError.UNKNOWN)

    /**
     * The session open on this phone now (whose it is and its access token, read together from one snapshot), or null;
     * for a call that must name its session itself.
     */
    fun currentSession(): AccountSession? = null

    /**
     * Renews the session open now and remembers it, right before a fresh sign-in that proves who the user is
     * ([checkFreshSignIn]). Renewed first, so its tokens do not turn over while the sign-in runs (the remembered copy
     * could not be put back then). Without a session [AuthError.SESSION_EXPIRED]; a failed renewal its error.
     */
    suspend fun markSessionBeforeProof(): OperationResult<Unit, AuthError> = OperationResult.Error(AuthError.UNKNOWN)

    /**
     * After a sign-in started past [markSessionBeforeProof]: success when a NEW session of the SAME account is open now
     * (the remembered one is then ended on the server). A session of another account is ended (this device's scope) and
     * the remembered one is put back, without touching anything else on the phone: [AuthError.WRONG_ACCOUNT] (or
     * [AuthError.SESSION_EXPIRED] when the remembered one is no longer valid). No new session is
     * [AuthError.PROOF_NEEDED]; nothing remembered [AuthError.SESSION_EXPIRED].
     */
    suspend fun checkFreshSignIn(): FreshSignInCheck = FreshSignInCheck(OperationResult.Error(AuthError.UNKNOWN))

    /**
     * The fresh sign-in ended without a result (failed or closed): when another account's session is open now, it is
     * ended and the remembered one put back, as in [checkFreshSignIn]. Forgets the remembered session either way.
     */
    suspend fun cancelFreshSignIn(): FreshSignInCheck = FreshSignInCheck(OperationResult.Success(Unit))
}

/**
 * How a fresh sign-in check ended ([result]), and the refresh tokens of the sessions it could not end on the server
 * ([unended]): the caller ends them later. Never shown or logged.
 */
class FreshSignInCheck(val result: OperationResult<Unit, AuthError>, val unended: List<String> = emptyList()) {
    override fun toString() = "FreshSignInCheck($result, ${unended.size} unended)"
}

/** A session: its user and its access token, taken together. Never shown or logged. */
data class AccountSession(val userId: String, val accessToken: String) {
    override fun toString() = "AccountSession(…)"
}
