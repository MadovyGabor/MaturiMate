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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class AccountTest {

    private val requests = mutableListOf<Pair<String, String>>()

    /** A Supabase client signed in as [USER_ID], answering every Postgrest call from [rows]. */
    private fun remote(rows: (String) -> String = { "[]" }): SupabaseAccountRepository = runBlocking {
        val client = createSupabaseClient("https://test.supabase.co", "test-key") {
            httpEngine = MockEngine { request ->
                val body = (request.body as? OutgoingContent.ByteArrayContent)?.bytes()?.decodeToString().orEmpty()
                val table = request.url.encodedPath.substringAfterLast('/')
                requests += "${request.method.value} $table" to body
                respond(rows(table), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
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
        client.auth.importSession(
            UserSession(accessToken = "access", refreshToken = "refresh", expiresIn = 3600, tokenType = "bearer", user = UserInfo(aud = "authenticated", id = USER_ID))
        )
        SupabaseAccountRepository(client.auth, client.postgrest)
    }

    private val student = UserProfile(
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

    @Test
    fun uploadWritesTheProfileUnderTheAccount() = runBlocking {
        val result = remote().uploadProfile(student, "Anna", "Kovács")

        assertEquals(OperationResult.Success(Unit), result)
        val calls = requests.map { it.first }
        assertEquals(
            listOf("POST user_profile", "POST user_settings", "DELETE user_role", "POST user_role", "DELETE user_subject", "POST user_subject"),
            calls,
        )
        val profile = requests[0].second
        assertTrue("\"first_name\":\"Anna\"" in profile && "\"teaching_language\":\"HU\"" in profile && USER_ID in profile, profile)
        assertTrue("\"reminder_time\":\"18:00\"" in requests[1].second, requests[1].second)
        assertTrue("\"level\":\"B2\"" in requests[5].second && "\"source\":\"AUTO\"" in requests[5].second)
    }

    @Test
    fun consentsAreRecordedWithTheDocumentVersion() = runBlocking {
        remote().recordConsents(setOf(ConsentType.TERMS, ConsentType.AGE_16))

        val (call, body) = requests.single()
        assertEquals("POST user_consent", call)
        assertTrue("\"consent_type\":\"TERMS\"" in body && "\"consent_type\":\"AGE_16\"" in body && "\"document_version\":\"1.0\"" in body, body)
    }

    @Test
    fun downloadReadsTheAccountsRows() = runBlocking {
        val repository = remote { table ->
            when (table) {
                "user_profile" -> """[{"user_id":"$USER_ID","first_name":"Anna","last_name":"Kovács","teaching_language":"SK","graduation_date":"2027-03-15","onboarding_completed_at":"2026-10-31T10:00:00Z"}]"""
                "user_settings" -> """[{"user_id":"$USER_ID","ui_language":"SK","timezone":"Europe/Bratislava","daily_goal_minutes":15,"reminder_enabled":true,"reminder_time":"07:30:00"}]"""
                "user_role" -> """[{"user_id":"$USER_ID","role_id":1}]"""
                "user_subject" -> """[{"user_id":"$USER_ID","subject_id":2,"source":"AUTO","level":null}]"""
                else -> "[]"
            }
        }

        val remote = (repository.downloadProfile() as OperationResult.Success).data!!

        assertEquals("Anna", remote.firstName)
        assertEquals(TeachingLanguage.SK, remote.profile.teachingLanguage)
        assertEquals(UserRole.STUDENT, remote.profile.role)
        assertEquals(15, remote.profile.dailyGoalMinutes)
        assertEquals(LocalTime(7, 30), remote.profile.reminderTime)
        assertEquals(listOf(UserSubject(2, true)), remote.profile.subjects)
        assertEquals(Instant.parse("2026-10-31T10:00:00Z"), remote.profile.onboardingCompletedAt)
    }

    @Test
    fun accountWithoutAProfileDownloadsNothing() = runBlocking {
        assertEquals(OperationResult.Success(null), remote().downloadProfile())
    }

    // --- AccountSetupImpl: the steps after sign-up and sign-in, with the server faked ---

    private class FakeAuth : AuthRepository {
        override val state: Flow<AuthState> = emptyFlow()
        override fun currentUser() = AccountUser(USER_ID, "anna@example.com", null, null)
        private val ok: OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
        override suspend fun signUp(email: String, password: String) = ok
        override suspend fun confirmSignUp(email: String, code: String) = ok
        override suspend fun resendSignUpCode(email: String) = ok
        override suspend fun signIn(email: String, password: String) = ok
        override suspend fun signOut() = ok
        override suspend fun sendPasswordResetCode(email: String) = ok
        override suspend fun resetPassword(email: String, code: String, newPassword: String) = ok
    }

    private class FakeAccount(val remote: RemoteProfile? = null) : AccountRepository {
        var uploaded: Triple<UserProfile, String, String>? = null
        var consents: Set<ConsentType> = emptySet()
        override suspend fun uploadProfile(profile: UserProfile, firstName: String, lastName: String): OperationResult<Unit, AuthError> {
            uploaded = Triple(profile, firstName, lastName)
            return OperationResult.Success(Unit)
        }
        override suspend fun recordConsents(consents: Set<ConsentType>): OperationResult<Unit, AuthError> {
            this.consents = consents
            return OperationResult.Success(Unit)
        }
        override suspend fun downloadProfile(): OperationResult<RemoteProfile?, AuthError> = OperationResult.Success(remote)
    }

    private fun local(): Pair<LocalUserProvider, UserProfileRepositoryImpl> {
        val database = UserDatabase(
            JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) }
        )
        val provider = LocalUserProvider(database) { "guest-1" }
        return provider to UserProfileRepositoryImpl(database, provider, Dispatchers.Unconfined)
    }

    @Test
    fun signUpKeepsTheGuestAnswersAndUploadsThem() = runBlocking {
        val (provider, profiles) = local()
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        profiles.saveRole(UserRole.LEARNER)
        val account = FakeAccount()

        val result = AccountSetupImpl(FakeAuth(), account, profiles, provider, Dispatchers.Unconfined)
            .completeSignUp("Anna", "Kovács", marketing = true, finishedAt = Instant.fromEpochMilliseconds(9_000))

        assertEquals(OperationResult.Success(Unit), result)
        assertEquals(Instant.fromEpochMilliseconds(9_000), account.uploaded?.first?.onboardingCompletedAt)
        assertTrue(profiles.profile.first()!!.isOnboardingCompleted)
        assertEquals(USER_ID, provider.userId())
        assertEquals(UserRole.LEARNER, account.uploaded?.first?.role)
        assertEquals("Anna", account.uploaded?.second)
        assertEquals(ConsentType.entries.toSet(), account.consents)
    }

    @Test
    fun signInReplacesTheLocalAnswersWithTheAccounts() = runBlocking {
        val (provider, profiles) = local()
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        val remote = RemoteProfile("Anna", "Kovács", student.copy(teachingLanguage = TeachingLanguage.SK, onboardingCompletedAt = Instant.fromEpochMilliseconds(5_000)))

        val result = AccountSetupImpl(FakeAuth(), FakeAccount(remote), profiles, provider, Dispatchers.Unconfined).completeSignIn()

        assertEquals(OperationResult.Success(true), result)
        val profile = profiles.profile.first()!!
        assertEquals(TeachingLanguage.SK, profile.teachingLanguage)
        assertEquals(UserRole.STUDENT, profile.role)
        assertEquals(student.subjects, profile.subjects)
        assertTrue(profile.isOnboardingCompleted)
    }

    @Test
    fun signInWithoutAServerProfileKeepsTheLocalAnswers() = runBlocking {
        val (provider, profiles) = local()
        profiles.saveTeachingLanguage(TeachingLanguage.HU)

        val result = AccountSetupImpl(FakeAuth(), FakeAccount(null), profiles, provider, Dispatchers.Unconfined).completeSignIn()

        assertEquals(OperationResult.Success(false), result)
        assertEquals(TeachingLanguage.HU, profiles.profile.first()!!.teachingLanguage)
        assertEquals(USER_ID, provider.userId())
    }

    private companion object {
        const val USER_ID = "8d0fd2b3-9ca7-4c64-9a1e-6b1c9a7f1e11"
    }
}
