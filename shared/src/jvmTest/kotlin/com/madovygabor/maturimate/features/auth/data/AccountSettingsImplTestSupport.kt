package com.madovygabor.maturimate.features.auth.data

import com.madovygabor.maturimate.testutil.testDbDispatcher
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.auth.domain.AccountSession
import com.madovygabor.maturimate.features.auth.domain.AccountUser
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import com.madovygabor.maturimate.features.auth.domain.RemoteProfile
import com.madovygabor.maturimate.features.auth.domain.ConsentType
import com.madovygabor.maturimate.features.auth.domain.AccountRepository
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.domain.AuthRepository
import com.madovygabor.maturimate.features.auth.domain.AuthState
import com.madovygabor.maturimate.features.auth.domain.FreshSignInCheck
import com.madovygabor.maturimate.features.sync.data.SyncStoreImpl
import com.madovygabor.maturimate.features.sync.domain.SyncChanges
import com.madovygabor.maturimate.features.sync.domain.SyncEngine
import com.madovygabor.maturimate.features.sync.domain.SyncError
import com.madovygabor.maturimate.features.sync.domain.SyncRepository
import com.madovygabor.maturimate.features.sync.domain.SyncResponse
import com.madovygabor.maturimate.features.sync.domain.SyncSession
import com.madovygabor.maturimate.features.learning.data.ProgressRepositoryImpl
import com.madovygabor.maturimate.features.userProfile.data.UserProfileRepositoryImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
abstract class AccountSettingsImplTestBase {

    // the app's setup: one thread runs the database calls (AppModul); with Unconfined a ViewModel on the main thread and the
    // test could use the one connection at the same time
    internal val dbDispatcher = testDbDispatcher()

    internal companion object {
        const val EXPORT = """{"profile":{"first_name":"Anna","last_name":"Kovács"},"subjects":[{"subject_id":1}]}"""
    }

    internal val database = UserDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) })

    internal var guests = 0

    internal val localUser = LocalUserProvider(database) { "guest-${++guests}" }

    internal val profiles = UserProfileRepositoryImpl(database, localUser, dbDispatcher)

    internal val progress = ProgressRepositoryImpl(database, localUser, dbDispatcher, now = { Instant.fromEpochMilliseconds(1_000) })

    // the settings act on the account this phone belongs to; the guest phone with a session is set up in its own tests
    init {
        localUser.adoptAccount("account-1")
    }

    /** A guest phone (this phone's rows were never the account's) while a Supabase session is open: a sign-in of an account that is not set up. */
    internal fun guestPhone() {
        localUser.removeUser()
        check(localUser.isGuest())
    }

    internal class Auth(private val password: String = "Old12345", offline: Boolean = false) : AuthRepository {
        var newPassword: String? = null
        var codesSent = 0
        var signIns = 0
        var user: AccountUser? = AccountUser("account-1", "anna@example.com", "Anna", "Kovács")
        var signedOut = false
        var offline = offline
        var localSessionCleared = false
        var revoked: String? = null
        val revokedAll = mutableListOf<String>()
        /** The server answers the sign-out with a 5xx; supabase-kt has cleared the session by then. */
        var signOutFails = false
        /** The server answers the revocation with an error (a 5xx): neither done nor known to be gone. */
        var serverFails = false
        override fun refreshToken() = "refresh-1"
        override suspend fun clearLocalSession() { localSessionCleared = true }
        override suspend fun revokeSession(refreshToken: String): OperationResult<Unit, AuthError> = when {
            offline -> OperationResult.Error(AuthError.NETWORK)
            serverFails -> OperationResult.Error(AuthError.UNKNOWN)
            else -> ok.also { revoked = refreshToken; revokedAll += refreshToken }
        }
        override val state: Flow<AuthState> = emptyFlow()
        override fun currentUser() = user
        private val ok: OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
        override suspend fun signUp(email: String, password: String) = ok
        override suspend fun confirmSignUp(email: String, code: String) = ok
        override suspend fun resendSignUpCode(email: String) = ok
        override suspend fun signIn(email: String, password: String): OperationResult<Unit, AuthError> {
            signIns++
            return if (password == this.password) ok else OperationResult.Error(AuthError.INVALID_CREDENTIALS)
        }
        override suspend fun signOut(): OperationResult<Unit, AuthError> = when {
            offline -> OperationResult.Error(AuthError.NETWORK)
            signOutFails -> OperationResult.Error(AuthError.UNKNOWN)
            else -> ok.also { signedOut = true }
        }
        override suspend fun sendPasswordResetCode(email: String) = ok
        override suspend fun verifyResetCode(email: String, code: String) = ok
        override suspend fun setNewPassword(newPassword: String) = ok.also { this.newPassword = newPassword }
        override suspend fun sendReauthenticationCode() = ok.also { codesSent++ }
        /** The server checks the code: "123456" is the one it sent. */
        /** The current password the change carried to the server. */
        var sentCurrentPassword: String? = null
        override suspend fun changePasswordWithCode(code: String, newPassword: String, currentPassword: String?): OperationResult<Unit, AuthError> {
            sentCurrentPassword = currentPassword
            return when {
                code != "123456" -> OperationResult.Error(AuthError.INVALID_CODE)
                newPassword == password -> OperationResult.Error(AuthError.SAME_PASSWORD)
                else -> ok.also { this.newPassword = newPassword }
            }
        }

        var passwordChecks = 0
        var rateLimited = false
        /** The refresh token of a check whose session could not be ended (the server's logout failed). */
        var leftover: String? = null
        override suspend fun verifyPassword(password: String, whileOpen: (suspend (accessToken: String) -> Unit)?): OperationResult<String?, AuthError> {
            passwordChecks++
            return when {
                user == null -> OperationResult.Error(AuthError.SESSION_EXPIRED)
                rateLimited -> OperationResult.Error(AuthError.TOO_MANY_REQUESTS)
                password != this.password -> OperationResult.Error(AuthError.INVALID_CREDENTIALS)
                // the check's own fresh session, lent out before it ends
                else -> OperationResult.Success<String?, AuthError>(leftover).also { whileOpen?.invoke("check-access") }
            }
        }

        /** The phone's session: after a Google proof, the fresh one of the same account. */
        var phoneSession: AccountSession? = AccountSession("account-1", "phone-access")
        override fun currentSession() = phoneSession
        /** What the phone's session was when the cancel of a proof compared the sessions. */
        var sessionAtCancel: AccountSession? = null

        var marked = 0
        var markResult: OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
        var freshSignIn: OperationResult<Unit, AuthError> = OperationResult.Error(AuthError.PROOF_NEEDED)
        /** Sessions the check or the cancel could not end on the server. */
        var unended: List<String> = emptyList()
        var cancels = 0
        override suspend fun markSessionBeforeProof(): OperationResult<Unit, AuthError> { marked++; return markResult }
        override suspend fun checkFreshSignIn() = FreshSignInCheck(freshSignIn, unended)
        override suspend fun cancelFreshSignIn(): FreshSignInCheck { cancels++; sessionAtCancel = phoneSession; return FreshSignInCheck(OperationResult.Success(Unit), unended) }
    }

    /** The pending token's store in memory (the app's is the session's settings store). */
    internal class MemoryTokens : PendingTokenStore {
        var tokens: List<String> = emptyList()
        override fun read() = tokens
        override fun write(tokens: List<String>) { this.tokens = tokens }
    }

    internal fun settings(auth: Auth = Auth(), sync: SyncEngine? = null, revocation: PendingRevocation? = null) =
        AccountSettingsImpl(auth, profiles, localUser, dbDispatcher, sync, revocation)

    internal class Repo(var result: OperationResult<SyncResponse, SyncError>) : SyncRepository {
        override suspend fun sync(cursor: String?, deviceId: String, guestMerge: Boolean, changes: SyncChanges) = result
    }

    internal val store = SyncStoreImpl(database, localUser, dbDispatcher)

    internal val signedIn = object : SyncSession {
        override suspend fun isSignedIn() = true
        override suspend fun deviceId() = "dev"
    }

    internal fun engine(repo: Repo) = SyncEngine(repo, store, signedIn, now = { 1_000 })

    internal class Server(private val fails: Boolean = false, private val refusal: AuthError? = null) : AccountRepository {
        var deleted = false
        /** The tokens the deletions went out with. */
        val tokens = mutableListOf<String>()
        override suspend fun saveSignUp(profile: UserProfile, firstName: String, lastName: String, consents: Set<ConsentType>, finishedAt: Instant):
            OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
        override suspend fun saveNames(firstName: String, lastName: String): OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
        override suspend fun downloadProfile(): OperationResult<RemoteProfile?, AuthError> = OperationResult.Success(null)
        override suspend fun deleteAccount(accessToken: String): OperationResult<Unit, AuthError> {
            tokens += accessToken
            return when {
                fails -> OperationResult.Error(AuthError.NETWORK)
                refusal != null -> OperationResult.Error(refusal)
                else -> OperationResult.Success<Unit, AuthError>(Unit).also { deleted = true }
            }
        }
        override suspend fun exportMyData(): OperationResult<String, AuthError> =
            if (fails) OperationResult.Error(AuthError.NETWORK) else OperationResult.Success(EXPORT)
    }
}
