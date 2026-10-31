package com.madovygabor.maturimate.features.auth.data

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.auth.domain.AccountUser
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.domain.AuthRepository
import com.madovygabor.maturimate.features.auth.domain.AuthState
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.exception.AuthWeakPasswordException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.HttpRequestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** [AuthRepository] over the Auth plugin of the Supabase client. */
class AuthRepositoryImpl(private val auth: Auth) : AuthRepository {

    override val state: Flow<AuthState> = auth.sessionStatus.map { status ->
        when (status) {
            is SessionStatus.Authenticated -> status.session.user
                ?.let { AuthState.SignedIn(it.id, it.email) }
                ?: AuthState.SignedOut
            is SessionStatus.NotAuthenticated -> AuthState.SignedOut
            // a failed refresh keeps the session; it is retried when the network is back
            is SessionStatus.RefreshFailure -> auth.currentUserOrNull()
                ?.let { AuthState.SignedIn(it.id, it.email) }
                ?: AuthState.SignedOut
            SessionStatus.Initializing -> AuthState.Loading
        }
    }

    override fun currentUser(): AccountUser? = auth.currentUserOrNull()?.let { user ->
        fun meta(key: String) = (user.userMetadata?.get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        // Google gives given_name and family_name; full_name is split as a fallback
        val full = meta("full_name") ?: meta("name")
        AccountUser(
            id = user.id,
            email = user.email,
            firstName = meta("given_name") ?: full?.substringBefore(' '),
            lastName = meta("family_name") ?: full?.substringAfter(' ', missingDelimiterValue = "")?.takeIf { it.isNotBlank() },
        )
    }

    override suspend fun signUp(email: String, password: String) = call {
        val user = auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        // With e-mail confirmation on, Supabase answers an existing address with a user
        // without identities instead of an error, so addresses cannot be probed.
        if (user != null && user.identities.isNullOrEmpty()) throw EmailTaken()
    }

    override suspend fun confirmSignUp(email: String, code: String) = call {
        auth.verifyEmailOtp(type = OtpType.Email.SIGNUP, email = email, token = code)
    }

    override suspend fun resendSignUpCode(email: String) = call {
        auth.resendEmail(OtpType.Email.SIGNUP, email)
    }

    override suspend fun signIn(email: String, password: String) = call {
        auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    override suspend fun signOut() = call {
        auth.signOut()
    }

    override suspend fun sendPasswordResetCode(email: String) = call {
        auth.resetPasswordForEmail(email)
    }

    override suspend fun resetPassword(email: String, code: String, newPassword: String) = call {
        auth.verifyEmailOtp(type = OtpType.Email.RECOVERY, email = email, token = code)
        auth.updateUser { password = newPassword }
    }

    /** Runs a call to Supabase and maps its failures to [AuthError]. */
    private suspend fun call(block: suspend () -> Unit): OperationResult<Unit, AuthError> =
        try {
            block()
            OperationResult.Success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OperationResult.Error(e.toAuthError())
        }

    private class EmailTaken : Exception()

    private fun Exception.toAuthError(): AuthError = when (this) {
        is EmailTaken -> AuthError.EMAIL_TAKEN
        is AuthWeakPasswordException -> AuthError.WEAK_PASSWORD
        is AuthRestException -> when (errorCode) {
            AuthErrorCode.InvalidCredentials -> AuthError.INVALID_CREDENTIALS
            AuthErrorCode.EmailNotConfirmed -> AuthError.EMAIL_NOT_CONFIRMED
            AuthErrorCode.EmailExists, AuthErrorCode.UserAlreadyExists -> AuthError.EMAIL_TAKEN
            AuthErrorCode.WeakPassword -> AuthError.WEAK_PASSWORD
            AuthErrorCode.SamePassword -> AuthError.SAME_PASSWORD
            AuthErrorCode.EmailAddressInvalid -> AuthError.INVALID_EMAIL
            AuthErrorCode.OtpExpired -> AuthError.INVALID_CODE
            AuthErrorCode.OverRequestRateLimit, AuthErrorCode.OverEmailSendRateLimit -> AuthError.TOO_MANY_REQUESTS
            else -> AuthError.UNKNOWN
        }
        is HttpRequestException -> AuthError.NETWORK
        else -> AuthError.UNKNOWN
    }
}
