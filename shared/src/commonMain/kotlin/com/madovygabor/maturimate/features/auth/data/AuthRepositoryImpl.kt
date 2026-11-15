package com.madovygabor.maturimate.features.auth.data

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.auth.domain.AccountSession
import com.madovygabor.maturimate.features.auth.domain.AccountUser
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.domain.AuthRepository
import com.madovygabor.maturimate.features.auth.domain.AuthState
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.auth.exception.AuthWeakPasswordException
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import com.madovygabor.maturimate.features.auth.domain.FreshSignInCheck
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * [AuthRepository] over the Auth plugin of the Supabase client. [appLanguage] is the app language ("hu" or "sk") sent
 * with a sign-up, so the e-mail template can pick the language; null leaves it out (the template's default).
 */
class AuthRepositoryImpl(
    private val auth: Auth,
    private val appLanguage: suspend () -> String? = { null },
) : AuthRepository {

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
            // an account made with Google has only the google identity, and no password
            hasPassword = user.identities?.any { it.provider == "email" } ?: true,
        )
    }

    override suspend fun signUp(email: String, password: String) = call {
        val lang = appLanguage()
        val user = auth.signUpWith(Email) {
            this.email = email
            this.password = password
            // user metadata: the templates read it as {{ .Data.lang }}
            if (lang != null) data = buildJsonObject { put("lang", lang) }
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
        // this device only: the account stays signed in on the user's other devices (decision 16)
        auth.signOut(SignOutScope.LOCAL)
    }

    override fun refreshToken(): String? = auth.currentSessionOrNull()?.refreshToken

    override fun currentSession(): AccountSession? =
        auth.currentSessionOrNull()?.let { session -> session.user?.id?.let { AccountSession(it, session.accessToken) } }

    override suspend fun clearLocalSession() {
        auth.clearSession()
    }

    override suspend fun revokeSession(refreshToken: String) = call {
        // An access token for the old session, without making it this phone's session (the app would see a sign-in).
        // It works the same while another sign-in is open: that session has its own tokens and is not touched.
        val session = try {
            auth.refreshSession(refreshToken)
        } catch (e: RestException) {
            // the server no longer knows the token (ended, used, the user deleted): nothing is left to end
            if (e.statusCode in GONE) return@call
            throw e
        }
        val client = auth.supabaseClient
        val response = client.httpClient.request("${client.supabaseHttpUrl}/auth/v1/logout") {
            method = HttpMethod.Post
            parameter("scope", "local")
            bearerAuth(session.accessToken)
        }
        // 401, 403, 404: the session or the user is gone already, which is what was wanted; a 5xx is tried again later
        if (!response.status.isSuccess() && response.status.value !in listOf(401, 403, 404)) {
            throw ServerFailed()
        }
    }

    override suspend fun sendPasswordResetCode(email: String) = call {
        auth.resetPasswordForEmail(email)
    }

    override suspend fun verifyResetCode(email: String, code: String) = call {
        auth.verifyEmailOtp(type = OtpType.Email.RECOVERY, email = email, token = code)
    }

    override suspend fun setNewPassword(newPassword: String) = call {
        auth.updateUser { password = newPassword }
    }

    override suspend fun sendReauthenticationCode() = call {
        auth.reauthenticate()
    }

    override suspend fun changePasswordWithCode(code: String, newPassword: String, currentPassword: String?) = call {
        // A plain PUT /user, not updateUser { currentPassword = … }: supabase-kt 3.8.0 serializes that field as
        // "currentPassword" (no @SerialName, no naming strategy), and GoTrue reads "current_password", so it would be dropped.
        val body = buildJsonObject {
            put("password", newPassword)
            put("nonce", code)
            currentPassword?.let { put("current_password", it) }
        }.toString()
        var response = putUser(body)
        if (response.status.value == 401) {
            // an access token that ran out: renewed once, then the same request again
            auth.refreshCurrentSession()
            response = putUser(body)
        }
        if (!response.status.isSuccess()) {
            val answer = runCatching { Json.parseToJsonElement(response.bodyAsText()).jsonObject }.getOrNull()
            throw PasswordRefused(response.status.value, (answer?.get("error_code") as? JsonPrimitive)?.contentOrNull)
        }
    }

    private suspend fun putUser(body: String): HttpResponse {
        val session = auth.currentSessionOrNull() ?: throw NoSession()
        val client = auth.supabaseClient
        return client.httpClient.request("${client.supabaseHttpUrl}/auth/v1/user") {
            method = HttpMethod.Put
            bearerAuth(session.accessToken)
            setBody(TextContent(body, ContentType.Application.Json))
        }
    }

    override suspend fun verifyPassword(password: String, whileOpen: (suspend (accessToken: String) -> Unit)?): OperationResult<String?, AuthError> {
        val user = auth.currentUserOrNull() ?: return OperationResult.Error(AuthError.SESSION_EXPIRED)
        val email = user.email?.takeIf { it.isNotBlank() } ?: return OperationResult.Error(AuthError.UNKNOWN)
        return try {
            val client = auth.supabaseClient
            // GoTrue's password grant through the plain HTTP client (the project's API key, no session header): a session
            // is made on the server, but never imported here, so this phone's session and the app's state stay as they are.
            // signInWith(Email) would replace this phone's session.
            val response = client.httpClient.request("${client.supabaseHttpUrl}/auth/v1/token") {
                method = HttpMethod.Post
                parameter("grant_type", "password")
                setBody(
                    TextContent(
                        buildJsonObject { put("email", email); put("password", password) }.toString(),
                        ContentType.Application.Json,
                    )
                )
            }
            val status = response.status.value
            val body = runCatching { Json.parseToJsonElement(response.bodyAsText()).jsonObject }.getOrNull()
            fun field(name: String) = (body?.get(name) as? JsonPrimitive)?.contentOrNull
            when {
                status == 429 || field("error_code") == "over_request_rate_limit" -> OperationResult.Error(AuthError.TOO_MANY_REQUESTS)
                // GoTrue: 400 invalid_credentials (older versions: invalid_grant without an error code)
                status == 400 && (field("error_code") ?: "invalid_credentials") == "invalid_credentials" ->
                    OperationResult.Error(AuthError.INVALID_CREDENTIALS)
                !response.status.isSuccess() -> OperationResult.Error(AuthError.UNKNOWN)
                else -> {
                    val access = field("access_token") ?: return OperationResult.Error(AuthError.UNKNOWN)
                    val refresh = field("refresh_token")
                    val checkedId = ((body?.get("user") as? JsonObject)?.get("id") as? JsonPrimitive)?.contentOrNull
                    // the address is the session's own, so this cannot be another account; if it ever were, it is no proof
                    val sameAccount = checkedId == user.id
                    // The check's session is lent out (a deletion goes out with it), then it ends; when ending fails its
                    // refresh token goes back, to be ended later. NonCancellable: a session the server made is always
                    // ended or handed back, never forgotten.
                    val ended = withContext(NonCancellable) {
                        var endedNow = false
                        try {
                            if (sameAccount) whileOpen?.invoke(access)
                        } finally {
                            endedNow = endSession(access)
                        }
                        endedNow
                    }
                    when {
                        !sameAccount -> OperationResult.Error(AuthError.INVALID_CREDENTIALS)
                        else -> OperationResult.Success(refresh.takeUnless { ended })
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // never the exception's text: the request carried the password
            OperationResult.Error(if (e is HttpRequestException) AuthError.NETWORK else AuthError.UNKNOWN)
        }
    }

    /** The session open before a fresh sign-in that proves who the user is; never shown or logged. */
    private var beforeProof: UserSession? = null

    override suspend fun markSessionBeforeProof(): OperationResult<Unit, AuthError> {
        beforeProof = null
        if (auth.currentSessionOrNull() == null) return OperationResult.Error(AuthError.SESSION_EXPIRED)
        // renewed first: its tokens then hold for an hour, so the remembered copy is the live one while the sign-in runs
        val renewed = call { auth.refreshCurrentSession() }
        if (renewed is OperationResult.Error) return renewed
        beforeProof = auth.currentSessionOrNull() ?: return OperationResult.Error(AuthError.SESSION_EXPIRED)
        return OperationResult.Success(Unit)
    }

    override suspend fun checkFreshSignIn(): FreshSignInCheck = withContext(NonCancellable) {
        val before = beforeProof ?: return@withContext FreshSignInCheck(OperationResult.Error(AuthError.SESSION_EXPIRED))
        beforeProof = null
        val now = auth.currentSessionOrNull()
        val beforeId = before.user?.id
        val nowId = now?.user?.id
        when {
            now == null || nowId == null || beforeId == null -> FreshSignInCheck(OperationResult.Error(AuthError.PROOF_NEEDED))
            nowId != beforeId -> putBack(before, now)
            now.accessToken == before.accessToken -> FreshSignInCheck(OperationResult.Error(AuthError.PROOF_NEEDED))
            else -> {
                // a new session of the same account: the old one is no longer this phone's, so it ends on the server;
                // when that fails its token is handed back, to be ended later
                val ended = revokeSession(before.refreshToken) is OperationResult.Success
                FreshSignInCheck(OperationResult.Success(Unit), if (ended) emptyList() else listOf(before.refreshToken))
            }
        }
    }

    override suspend fun cancelFreshSignIn(): FreshSignInCheck = withContext(NonCancellable) {
        val before = beforeProof ?: return@withContext FreshSignInCheck(OperationResult.Success(Unit))
        beforeProof = null
        val now = auth.currentSessionOrNull()
        val nowId = now?.user?.id
        // another account came in after all (e.g. a flow that failed after saving its session): the same as a wrong account
        if (now != null && nowId != null && nowId != before.user?.id) putBack(before, now) else FreshSignInCheck(OperationResult.Success(Unit))
    }

    /**
     * Another account's session [other] is open instead of this phone's [own]: the phone's own comes back first, then the
     * other one ends on the server (this device's scope). The local rows are not touched: they were never that account's.
     */
    private suspend fun putBack(own: UserSession, other: UserSession): FreshSignInCheck {
        val unended = mutableListOf<String>()
        val restored = try {
            auth.importSession(own)
            // renewed at once: a token that turned over meanwhile shows here, not at some later call
            auth.refreshCurrentSession()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: RestException) {
            // the server no longer knows it: the phone is signed out cleanly (its rows stay), the user signs in again
            auth.clearSession()
            false
        } catch (_: Exception) {
            // no network: the imported session stays and is renewed later
            true
        }
        if (!endSession(other.accessToken)) unended += other.refreshToken
        val error = if (restored) AuthError.WRONG_ACCOUNT else AuthError.SESSION_EXPIRED
        return FreshSignInCheck(OperationResult.Error(error), unended)
    }

    /** Ends the session of [accessToken] on the server, this device's scope only; true when it is gone. */
    private suspend fun endSession(accessToken: String): Boolean = try {
        val client = auth.supabaseClient
        val response = client.httpClient.request("${client.supabaseHttpUrl}/auth/v1/logout") {
            method = HttpMethod.Post
            parameter("scope", "local")
            bearerAuth(accessToken)
        }
        // 401, 403, 404: the session or the user is gone already
        response.status.isSuccess() || response.status.value in listOf(401, 403, 404)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
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

    /** The server answered the logout with an error of its own (a 5xx). */
    private class ServerFailed : Exception()

    private class NoSession : Exception()

    /** The server refused the password change ([status], GoTrue's [code]); carries nothing typed. */
    private class PasswordRefused(val status: Int, val code: String?) : Exception()

    private fun Exception.toAuthError(): AuthError = when (this) {
        is EmailTaken -> AuthError.EMAIL_TAKEN
        is NoSession -> AuthError.SESSION_EXPIRED
        is PasswordRefused -> when {
            status == 429 -> AuthError.TOO_MANY_REQUESTS
            // GoTrue with "require current password" on (codes from GoTrue's sources as recalled, not seen live)
            code == "current_password_mismatch" || code == "current_password_required" -> AuthError.INVALID_CREDENTIALS
            code == null && status == 401 -> AuthError.SESSION_EXPIRED
            else -> code?.let { AuthErrorCode.fromValue(it) }.toAuthError()
        }
        is AuthWeakPasswordException -> AuthError.WEAK_PASSWORD
        is AuthRestException -> errorCode.toAuthError()
        is HttpRequestException -> AuthError.NETWORK
        else -> AuthError.UNKNOWN
    }

    private fun AuthErrorCode?.toAuthError(): AuthError = when (this) {
        AuthErrorCode.InvalidCredentials -> AuthError.INVALID_CREDENTIALS
        AuthErrorCode.EmailNotConfirmed -> AuthError.EMAIL_NOT_CONFIRMED
        AuthErrorCode.EmailExists, AuthErrorCode.UserAlreadyExists -> AuthError.EMAIL_TAKEN
        AuthErrorCode.WeakPassword -> AuthError.WEAK_PASSWORD
        AuthErrorCode.SamePassword -> AuthError.SAME_PASSWORD
        AuthErrorCode.EmailAddressInvalid -> AuthError.INVALID_EMAIL
        // the reauthentication code: wrong, expired, or missing
        AuthErrorCode.OtpExpired, AuthErrorCode.ReauthenticationNotValid, AuthErrorCode.ReauthNonceMissing,
        AuthErrorCode.ReauthenticationNeeded -> AuthError.INVALID_CODE
        AuthErrorCode.SessionNotFound, AuthErrorCode.SessionExpired, AuthErrorCode.BadJwt -> AuthError.SESSION_EXPIRED
        AuthErrorCode.OverRequestRateLimit, AuthErrorCode.OverEmailSendRateLimit -> AuthError.TOO_MANY_REQUESTS
        else -> AuthError.UNKNOWN
    }

    private companion object {
        /**
         * Answers of the token refresh that mean the token is dead for good (GoTrue: 400 refresh_token_not_found or
         * refresh_token_already_used, 403/404 for a user that is gone). A 401 (a wrong API key), 408, 429 and 5xx say
         * nothing about the token: it is kept and tried again.
         */
        val GONE = setOf(400, 403, 404)
    }
}
