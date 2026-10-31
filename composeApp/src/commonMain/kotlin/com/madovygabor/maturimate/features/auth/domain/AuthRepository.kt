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

    /** Too many attempts or e-mails in a short time. */
    TOO_MANY_REQUESTS,

    /** No connection to the server. */
    NETWORK,

    UNKNOWN,
}

/**
 * Sign-up, sign-in and password reset with Supabase Auth.
 *
 * E-mails carry a 6-digit code that the user types into the app (DECISIONS 2026-10-31).
 * Google sign-in comes with the account screen (roadmap step 5.12).
 */
interface AuthRepository {
    val state: Flow<AuthState>

    /** The signed-in account, with the names Google gave when it was used; null when signed out. */
    fun currentUser(): AccountUser?

    /** Creates the account and sends the sign-up code; the user is signed in by [confirmSignUp]. */
    suspend fun signUp(email: String, password: String): OperationResult<Unit, AuthError>
    suspend fun confirmSignUp(email: String, code: String): OperationResult<Unit, AuthError>
    /** Sends the sign-up code again. */
    suspend fun resendSignUpCode(email: String): OperationResult<Unit, AuthError>

    suspend fun signIn(email: String, password: String): OperationResult<Unit, AuthError>
    suspend fun signOut(): OperationResult<Unit, AuthError>

    suspend fun sendPasswordResetCode(email: String): OperationResult<Unit, AuthError>
    /** Checks the code, then sets the new password; the user is signed in afterwards. */
    suspend fun resetPassword(email: String, code: String, newPassword: String): OperationResult<Unit, AuthError>
}
