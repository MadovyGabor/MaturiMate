package com.madovygabor.maturimate.features.auth.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.auth.domain.AccountRepository
import com.madovygabor.maturimate.features.auth.domain.AccountUser
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.domain.AuthRepository
import com.madovygabor.maturimate.features.auth.domain.AuthState
import com.madovygabor.maturimate.features.auth.domain.ConsentType
import com.madovygabor.maturimate.features.auth.domain.RemoteProfile
import com.madovygabor.maturimate.features.userProfile.data.UserProfileRepositoryImpl
import com.madovygabor.maturimate.features.userProfile.domain.SubjectLevel
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import com.madovygabor.maturimate.features.userProfile.domain.UserSubject
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
abstract class AccountSetupImplTestBase {

    internal val requests = mutableListOf<Pair<String, String>>()

    /** The Authorization header of each call (never printed by a test). */
    internal val authorizations = mutableListOf<String?>()

    /** A Supabase client signed in as [USER_ID], answering every Postgrest call from [rows]. */
    internal fun remote(status: HttpStatusCode = HttpStatusCode.OK, signedIn: Boolean = true, rows: (String) -> String = { "[]" }): SupabaseAccountRepository = runBlocking {
        val client = createSupabaseClient("https://test.supabase.co", "test-key") {
            httpEngine = MockEngine { request ->
                val body = (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()
                val table = request.url.encodedPath.substringAfterLast('/')
                requests += "${request.method.value} $table" to body
                authorizations += request.headers[HttpHeaders.Authorization]
                respond(rows(table), status, headersOf(HttpHeaders.ContentType, "application/json"))
            }
            install(Auth) {
                sessionManager = MemorySessionManager()
                codeVerifierCache = MemoryCodeVerifierCache()
                autoLoadFromStorage = false
                alwaysAutoRefresh = false
                enableLifecycleCallbacks = false
            }
            install(Postgrest)
        }
        // the stored-session check must be over, or it could end after the import and drop it
        client.auth.awaitInitialization()
        if (signedIn) client.auth.importSession(
            UserSession(accessToken = "access", refreshToken = "refresh", expiresIn = 3600, tokenType = "bearer", user = UserInfo(aud = "authenticated", id = USER_ID))
        )
        SupabaseAccountRepository(client.auth, client.postgrest)
    }

    internal val student = UserProfile(
        teachingLanguage = TeachingLanguage.HU,
        role = UserRole.STUDENT,
        graduationDate = LocalDate(2027, 3, 15),
        uiLanguage = TeachingLanguage.HU,
        dailyGoalMinutes = 10,
        timezone = "Europe/Bratislava",
        onboardingCompletedAt = null,
        subjects = listOf(UserSubject(1, true), UserSubject(3, true, SubjectLevel.B2)),
        reminderEnabled = true,
        reminderTime = LocalTime(18, 0),
    )

    // --- AccountSetupImpl: the steps after sign-up and sign-in, with the server faked ---
    internal class FakeAuth : AuthRepository {
        override val state: Flow<AuthState> = emptyFlow()
        override fun currentUser() = AccountUser(USER_ID, "anna@example.com", null, null)
        private val ok: OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
        override suspend fun signUp(email: String, password: String) = ok
        override suspend fun confirmSignUp(email: String, code: String) = ok
        override suspend fun resendSignUpCode(email: String) = ok
        override suspend fun signIn(email: String, password: String) = ok
        override suspend fun signOut() = ok
        override suspend fun sendPasswordResetCode(email: String) = ok
        override suspend fun verifyResetCode(email: String, code: String) = ok
        override suspend fun setNewPassword(newPassword: String) = ok
    }

    internal class FakeAccount(
        val remote: RemoteProfile? = null,
        val downloadFails: Boolean = false,
        val saveFails: Boolean = false,
        /** Who the phone's user was while the sign-up went to the server (null: not asked). */
        val localUser: (() -> LocalUserProvider)? = null,
        /** The server's answer to the sign-up's save when it refuses it (null: accepted, or [saveFails]). */
        val saveError: AuthError? = null,
    ) : AccountRepository {
        var saved: Triple<UserProfile, String, String>? = null
        var downloads = 0
        var guestWhileSaving: Boolean? = null
        var consents: Set<ConsentType> = emptySet()
        var completedAt: Instant? = null
        override suspend fun saveSignUp(
            profile: UserProfile,
            firstName: String,
            lastName: String,
            consents: Set<ConsentType>,
            finishedAt: Instant,
        ): OperationResult<Unit, AuthError> {
            guestWhileSaving = localUser?.invoke()?.isGuest()
            if (saveFails) return OperationResult.Error(AuthError.NETWORK)
            saveError?.let { return OperationResult.Error(it) }
            saved = Triple(profile, firstName, lastName)
            this.consents = consents
            completedAt = finishedAt
            return OperationResult.Success(Unit)
        }
        override suspend fun downloadProfile(): OperationResult<RemoteProfile?, AuthError> {
            downloads++
            return if (downloadFails) OperationResult.Error(AuthError.NETWORK) else OperationResult.Success(remote)
        }
        override suspend fun saveNames(firstName: String, lastName: String): OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
    }

    internal fun local(): Pair<LocalUserProvider, UserProfileRepositoryImpl> {
        val database = UserDatabase(
            JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) }
        )
        val provider = LocalUserProvider(database) { "guest-1" }
        return provider to UserProfileRepositoryImpl(database, provider, Dispatchers.Unconfined)
    }

    internal companion object {
        const val USER_ID = "8d0fd2b3-9ca7-4c64-9a1e-6b1c9a7f1e11"
    }
}
