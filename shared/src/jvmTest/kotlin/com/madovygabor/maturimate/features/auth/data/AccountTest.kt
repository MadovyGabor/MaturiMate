package com.madovygabor.maturimate.features.auth.data

import kotlin.test.assertFalse
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

    // The sign-up used to be six separate requests, so a dropped connection could leave the
    // account half written; now it is one call, one transaction on the server.
    @Test
    fun aSignUpIsOneCallToTheServer() = runBlocking {
        val result = remote().saveSignUp(student, "Anna", "Kovács", setOf(ConsentType.TERMS, ConsentType.AGE_16), Instant.parse("2026-11-01T10:00:00Z"))

        assertEquals(OperationResult.Success(Unit), result)
        val (call, body) = requests.single()
        assertEquals("POST save_sign_up", call)
        assertTrue("\"p_first_name\":\"Anna\"" in body && "\"p_teaching_language\":\"HU\"" in body, body)
        assertTrue("\"p_reminder_time\":\"18:00\"" in body && "\"p_role_id\":1" in body, body)
        assertTrue("\"level\":\"B2\"" in body && "\"source\":\"AUTO\"" in body, body)
        assertTrue("\"p_consents\":[\"TERMS\",\"AGE_16\"]" in body && "\"p_document_version\":\"1.0\"" in body, body)
        assertTrue("\"p_onboarding_completed_at\":\"2026-11-01T10:00:00Z\"" in body, body)
        // the server takes the user from the session, never from the request
        assertTrue(USER_ID !in body, body)
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
    fun unknownValuesFromTheServerAreLeftOut() = runBlocking {
        val repository = remote { table ->
            when (table) {
                "user_profile" -> """[{"user_id":"$USER_ID","first_name":"Anna","last_name":"Kovács","teaching_language":"DE","graduation_date":"not a date"}]"""
                "user_settings" -> """[{"user_id":"$USER_ID","ui_language":"DE","timezone":"Europe/Bratislava","reminder_enabled":false,"reminder_time":"later"}]"""
                "user_subject" -> """[{"user_id":"$USER_ID","subject_id":3,"source":"CHOSEN","level":"C1"}]"""
                else -> "[]"
            }
        }

        val remote = (repository.downloadProfile() as OperationResult.Success).data!!

        assertEquals(null, remote.profile.teachingLanguage)
        assertEquals(null, remote.profile.graduationDate)
        assertEquals(TeachingLanguage.SK, remote.profile.uiLanguage)
        assertEquals(null, remote.profile.reminderTime)
        assertEquals(listOf(UserSubject(3, false, level = null)), remote.profile.subjects)
    }

    @Test
    fun anAnswerThatCannotBeReadIsAnErrorNotACrash() = runBlocking {
        val repository = remote { table -> if (table == "user_profile") """{"unexpected": true}""" else "[]" }

        assertEquals(OperationResult.Error(AuthError.UNKNOWN), repository.downloadProfile())
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
        override suspend fun verifyResetCode(email: String, code: String) = ok
        override suspend fun setNewPassword(newPassword: String) = ok
    }

    private class FakeAccount(val remote: RemoteProfile? = null) : AccountRepository {
        var saved: Triple<UserProfile, String, String>? = null
        var consents: Set<ConsentType> = emptySet()
        var completedAt: Instant? = null
        override suspend fun saveSignUp(
            profile: UserProfile,
            firstName: String,
            lastName: String,
            consents: Set<ConsentType>,
            finishedAt: Instant,
        ): OperationResult<Unit, AuthError> {
            saved = Triple(profile, firstName, lastName)
            this.consents = consents
            completedAt = finishedAt
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
        assertEquals(Instant.fromEpochMilliseconds(9_000), account.completedAt)
        assertTrue(profiles.profile.first()!!.isOnboardingCompleted)
        assertEquals(USER_ID, provider.userId())
        assertEquals(UserRole.LEARNER, account.saved?.first?.role)
        assertEquals("Anna", account.saved?.second)
        assertEquals(ConsentType.entries.toSet(), account.consents)
    }

    @Test
    fun aGuestWhoSignsUpFromTheProfileKeepsTheDayTheOnboardingEnded() = runBlocking {
        val (provider, profiles) = local()
        profiles.saveRole(UserRole.STUDENT)
        // looked around as a guest days ago
        profiles.completeOnboarding(Instant.fromEpochMilliseconds(5_000))
        val account = FakeAccount()

        val result = AccountSetupImpl(FakeAuth(), account, profiles, provider, Dispatchers.Unconfined)
            .completeSignUp("Anna", "Kovács", marketing = false, finishedAt = Instant.fromEpochMilliseconds(9_000))

        assertEquals(OperationResult.Success(Unit), result)
        assertEquals(Instant.fromEpochMilliseconds(5_000), account.completedAt)
        assertEquals(Instant.fromEpochMilliseconds(5_000), profiles.profile.first()!!.onboardingCompletedAt)
        // the guest's rows are the account's now
        assertEquals(USER_ID, provider.userId())
        assertEquals("Anna", profiles.profile.first()!!.firstName)
    }

    @Test
    fun signInReplacesTheLocalAnswersWithTheAccounts() = runBlocking {
        val (provider, profiles) = local()
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        val remote = RemoteProfile("Anna", "Kovács", student.copy(teachingLanguage = TeachingLanguage.SK, onboardingCompletedAt = Instant.fromEpochMilliseconds(5_000)))

        val result = AccountSetupImpl(FakeAuth(), FakeAccount(remote), profiles, provider, Dispatchers.Unconfined).completeSignIn()

        val account = (result as OperationResult.Success).data
        assertTrue(account.onboardingFinished)
        // the answers to continue from are the ones just written
        assertEquals(TeachingLanguage.SK, account.profile?.teachingLanguage)
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

        val account = (result as OperationResult.Success).data
        assertFalse(account.onboardingFinished)
        assertEquals(TeachingLanguage.HU, account.profile?.teachingLanguage)
        assertEquals(TeachingLanguage.HU, profiles.profile.first()!!.teachingLanguage)
        assertEquals(USER_ID, provider.userId())
    }

    @Test
    fun aServerProfileWithoutSubjectsKeepsTheSubjectsOfThisPhone() = runBlocking {
        val (provider, profiles) = local()
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        profiles.saveSubjects(listOf(UserSubject(1, compulsory = true)))
        val remote = RemoteProfile("Anna", "Kovács", student.copy(subjects = emptyList()))

        AccountSetupImpl(FakeAuth(), FakeAccount(remote), profiles, provider, Dispatchers.Unconfined).completeSignIn()

        assertEquals(listOf(UserSubject(1, compulsory = true)), profiles.profile.first()!!.subjects)
    }

    private companion object {
        const val USER_ID = "8d0fd2b3-9ca7-4c64-9a1e-6b1c9a7f1e11"
    }
}
