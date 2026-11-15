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
import com.madovygabor.maturimate.features.sync.data.SyncStoreImpl
import com.madovygabor.maturimate.features.sync.domain.SyncChanges
import com.madovygabor.maturimate.features.sync.domain.SyncEngine
import com.madovygabor.maturimate.features.sync.domain.SyncError
import com.madovygabor.maturimate.features.sync.domain.SyncProfile
import com.madovygabor.maturimate.features.sync.domain.SyncRepository
import com.madovygabor.maturimate.features.sync.domain.SyncResponse
import com.madovygabor.maturimate.features.sync.domain.SyncSession
import com.madovygabor.maturimate.features.sync.domain.SyncSettings
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

    /** The Authorization header of each call (never printed by a test). */
    private val authorizations = mutableListOf<String?>()

    /** A Supabase client signed in as [USER_ID], answering every Postgrest call from [rows]. */
    private fun remote(status: HttpStatusCode = HttpStatusCode.OK, signedIn: Boolean = true, rows: (String) -> String = { "[]" }): SupabaseAccountRepository = runBlocking {
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

    private class FakeAccount(
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

    // A restored session counted as a new Google account, and the sign-up form overwrote the account's profile on the server.
    @Test
    fun aSignUpNeverOverwritesAnAccountThatIsSetUpAlready() = runBlocking {
        val (provider, profiles) = local()
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        val remote = RemoteProfile(
            "Anna", "Kovács", student.copy(teachingLanguage = TeachingLanguage.SK, onboardingCompletedAt = Instant.fromEpochMilliseconds(5_000)),
            acceptedTerms = true,
        )
        val account = FakeAccount(remote)

        val result = AccountSetupImpl(FakeAuth(), account, profiles, provider, Dispatchers.Unconfined)
            .completeSignUp("Béla", "Nagy", marketing = false, finishedAt = Instant.fromEpochMilliseconds(9_000))

        // it became a sign-in: nothing was sent, and the account's answers are on this phone
        assertEquals(OperationResult.Success(Unit), result)
        assertEquals(null, account.saved)
        val profile = profiles.profile.first()!!
        assertEquals(TeachingLanguage.SK, profile.teachingLanguage)
        assertEquals("Anna", profile.firstName)
        assertEquals(Instant.fromEpochMilliseconds(5_000), profile.onboardingCompletedAt)
        assertEquals(USER_ID, provider.userId())
    }

    // A sync could push the finished guest profile before save_sign_up (the guest was adopted first); the guard then
    // refused the sign-up, the retry became a sign-in and the consents were never stored.
    @Test
    fun aGuestsSignUpIsSavedWhileThePhoneIsStillTheGuests() = runBlocking {
        val (provider, profiles) = local()
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        profiles.completeOnboarding(Instant.fromEpochMilliseconds(5_000))
        val account = FakeAccount(localUser = { provider })

        val result = AccountSetupImpl(FakeAuth(), account, profiles, provider, Dispatchers.Unconfined)
            .completeSignUp("Anna", "Kovács", marketing = true, finishedAt = Instant.fromEpochMilliseconds(9_000))

        assertEquals(OperationResult.Success(Unit), result)
        // still a guest while the server saved it: no sync of this account could run before
        assertEquals(true, account.guestWhileSaving)
        assertEquals(ConsentType.entries.toSet(), account.consents)
        assertEquals(USER_ID, provider.userId())
        assertEquals("Anna", profiles.profile.first()!!.firstName)
    }

    @Test
    fun aFinishedProfileWithoutTheConsentsIsStillSignedUp() = runBlocking {
        val (provider, profiles) = local()
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        // an earlier sync pushed the finished profile, but save_sign_up never went through: no TERMS consent
        val pushed = RemoteProfile("", "", student.copy(onboardingCompletedAt = Instant.fromEpochMilliseconds(5_000)), acceptedTerms = false)
        val account = FakeAccount(pushed)

        val result = AccountSetupImpl(FakeAuth(), account, profiles, provider, Dispatchers.Unconfined)
            .completeSignUp("Anna", "Kovács", marketing = false, finishedAt = Instant.fromEpochMilliseconds(9_000))

        assertEquals(OperationResult.Success(Unit), result)
        assertEquals("Anna", account.saved?.second)
        assertEquals(setOf(ConsentType.TERMS, ConsentType.PRIVACY, ConsentType.AGE_16), account.consents)
    }

    @Test
    fun aGuestsFailedSignUpAdoptsNothing() = runBlocking {
        val (provider, profiles) = local()
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        val account = FakeAccount(saveFails = true)

        val result = AccountSetupImpl(FakeAuth(), account, profiles, provider, Dispatchers.Unconfined)
            .completeSignUp("Anna", "Kovács", marketing = false, finishedAt = Instant.fromEpochMilliseconds(9_000))

        assertEquals(OperationResult.Error(AuthError.NETWORK), result)
        // the phone is still the guest's, so no sync can push anything of it to the account
        assertTrue(provider.isGuest())
        assertEquals("guest-1", provider.userId())
    }

    @Test
    fun theDownloadReadsWhetherTheTermsWereAccepted() = runBlocking {
        val repository = remote { table ->
            when (table) {
                "user_profile" -> """[{"user_id":"$USER_ID","first_name":"Anna","last_name":"Kovács","onboarding_completed_at":"2026-10-31T10:00:00Z"}]"""
                "user_consent" -> """[{"consent_type":"TERMS"}]"""
                else -> "[]"
            }
        }

        val remote = (repository.downloadProfile() as OperationResult.Success).data!!

        assertTrue(remote.acceptedTerms)
        assertTrue(remote.isSetUp)
        assertTrue(requests.any { it.first == "GET user_consent" }, requests.toString())
    }

    @Test
    fun aSignUpThatCannotCheckTheAccountSendsNothing() = runBlocking {
        val (provider, profiles) = local()
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        val account = FakeAccount(downloadFails = true)

        val result = AccountSetupImpl(FakeAuth(), account, profiles, provider, Dispatchers.Unconfined)
            .completeSignUp("Béla", "Nagy", marketing = false, finishedAt = Instant.fromEpochMilliseconds(9_000))

        assertEquals(OperationResult.Error(AuthError.NETWORK), result)
        assertEquals(null, account.saved)
        assertTrue(provider.isGuest())
    }

    @Test
    fun anAccountWithAnUnfinishedProfileIsStillSignedUp() = runBlocking {
        val (provider, profiles) = local()
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        val account = FakeAccount(RemoteProfile("", "", student.copy(onboardingCompletedAt = null)))

        val result = AccountSetupImpl(FakeAuth(), account, profiles, provider, Dispatchers.Unconfined)
            .completeSignUp("Béla", "Nagy", marketing = false, finishedAt = Instant.fromEpochMilliseconds(9_000))

        assertEquals(OperationResult.Success(Unit), result)
        assertEquals("Béla", account.saved?.second)
    }

    // A 401 (an expired or revoked session) used to be "something went wrong" instead of "sign in again".
    @Test
    fun anExpiredSessionIsReportedAsSuch() = runBlocking {
        val repository = remote(status = HttpStatusCode.Unauthorized) { """{"code":"PGRST301","details":null,"hint":null,"message":"JWT expired"}""" }

        assertEquals(OperationResult.Error(AuthError.SESSION_EXPIRED), repository.exportMyData())
    }

    // Without a session the account calls said "wrong e-mail or password", though nothing was typed.
    @Test
    fun noSessionIsAnExpiredSessionNotAWrongPassword() = runBlocking {
        val repository = remote(signedIn = false)

        assertEquals(OperationResult.Error(AuthError.SESSION_EXPIRED), repository.exportMyData())
        assertEquals(OperationResult.Error(AuthError.SESSION_EXPIRED), repository.deleteAccount("proof-token"))
        // nothing was asked of the server
        assertTrue(requests.isEmpty())
    }

    // Card 12 (N-8): the deletion goes out with the proof's own fresh session, not the phone's, so the server can check
    // the proof itself (a sign-in of the last 5 minutes in the token, migration 20261128120000)
    @Test
    fun theDeletionCarriesTheProofsTokenNotThePhonesSession() = runBlocking {
        assertEquals(OperationResult.Success(Unit), remote().deleteAccount("proof-token"))

        assertEquals("POST delete_my_account", requests.single().first)
        assertEquals("Bearer proof-token", authorizations.single())
    }

    @Test
    fun aDeletionTheServerRefusesForAnOldSignInNeedsANewProof() = runBlocking {
        val stale = remote(status = HttpStatusCode.Forbidden) { """{"code":"42501","details":null,"hint":null,"message":"recent_login_required"}""" }
        assertEquals(OperationResult.Error(AuthError.PROOF_NEEDED), stale.deleteAccount("old-token"))

        val expired = remote(status = HttpStatusCode.Unauthorized) { """{"code":"PGRST301","details":null,"hint":null,"message":"JWT expired"}""" }
        assertEquals(OperationResult.Error(AuthError.SESSION_EXPIRED), expired.deleteAccount("old-token"))

        val broken = remote(status = HttpStatusCode.InternalServerError) { """{"code":"P0001","details":null,"hint":null,"message":"consent_hmac_key is not set: the account is not deleted"}""" }
        assertEquals(OperationResult.Error(AuthError.UNKNOWN), broken.deleteAccount("proof-token"))
    }

    // F7: the server's guard (PT409 account_already_set_up, HTTP 409) was "something went wrong".
    @Test
    fun aSignUpTheServerRefusesForASetUpAccountSaysSo() = runBlocking {
        val repository = remote(status = HttpStatusCode.Conflict) {
            """{"code":"PT409","details":null,"hint":null,"message":"account_already_set_up"}"""
        }

        val result = repository.saveSignUp(student, "Anna", "Kovács", setOf(ConsentType.TERMS), Instant.parse("2026-11-01T10:00:00Z"))

        assertEquals(OperationResult.Error(AuthError.ACCOUNT_ALREADY_SET_UP), result)
    }

    // F7: the refused save is never a silent sign-in: the guest's phone stays the guest's, nothing is adopted or synced.
    @Test
    fun aRefusedSignUpOfASetUpAccountAdoptsNothing() = runBlocking {
        val (provider, profiles) = local()
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        val account = FakeAccount(saveError = AuthError.ACCOUNT_ALREADY_SET_UP)

        val result = AccountSetupImpl(FakeAuth(), account, profiles, provider, Dispatchers.Unconfined)
            .completeSignUp("Anna", "Kovács", marketing = false, finishedAt = Instant.fromEpochMilliseconds(9_000))

        assertEquals(OperationResult.Error(AuthError.ACCOUNT_ALREADY_SET_UP), result)
        assertTrue(provider.isGuest())
        assertEquals(false, profiles.profile.first()!!.isOnboardingCompleted)
    }

    /** A finished profile of an account that is set up (the onboarding end AND the TERMS consent). */
    private fun setUp(profile: UserProfile = student) =
        RemoteProfile("Anna", "Kovács", profile.copy(onboardingCompletedAt = profile.onboardingCompletedAt ?: Instant.fromEpochMilliseconds(5_000)), acceptedTerms = true)

    @Test
    fun signInReplacesTheLocalAnswersWithTheAccounts() = runBlocking {
        val (provider, profiles) = local()
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        val remote = setUp(student.copy(teachingLanguage = TeachingLanguage.SK))

        val result = AccountSetupImpl(FakeAuth(), FakeAccount(remote), profiles, provider, Dispatchers.Unconfined).completeSignIn()

        val account = (result as OperationResult.Success).data
        assertTrue(account.onboardingFinished)
        assertTrue(account.isSetUp)
        // the answers to continue from are the ones just written
        assertEquals(TeachingLanguage.SK, account.profile?.teachingLanguage)
        val profile = profiles.profile.first()!!
        assertEquals(TeachingLanguage.SK, profile.teachingLanguage)
        assertEquals(UserRole.STUDENT, profile.role)
        assertEquals(student.subjects, profile.subjects)
        assertTrue(profile.isOnboardingCompleted)
        assertEquals(USER_ID, provider.userId())
    }

    // F2-F4: an account that is not set up is checked read-only; the phone stays the guest's, nothing is adopted or
    // written. The sign-up (completeSignUp) saves the guest's answers first and adopts after (card 3's order).
    @Test
    fun signInOfAnAccountThatIsNotSetUpAdoptsAndWritesNothing() = runBlocking {
        val (provider, profiles) = local()
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        // a partly answered (or finished without TERMS) profile on the server: it does not come to this device
        val remote = RemoteProfile("Anna", "Kovács", student.copy(teachingLanguage = TeachingLanguage.SK, onboardingCompletedAt = Instant.fromEpochMilliseconds(5_000)))

        val result = AccountSetupImpl(FakeAuth(), FakeAccount(remote), profiles, provider, Dispatchers.Unconfined).completeSignIn()

        val account = (result as OperationResult.Success).data
        assertFalse(account.isSetUp)
        // the guest's state: not finished, the guest's answers
        assertFalse(account.onboardingFinished)
        assertEquals(TeachingLanguage.HU, account.profile?.teachingLanguage)
        assertTrue(provider.isGuest())
        assertEquals(TeachingLanguage.HU, profiles.profile.first()!!.teachingLanguage)
    }

    @Test
    fun signInWithoutAServerProfileKeepsTheGuestsAnswersAndThePhoneTheGuests() = runBlocking {
        val (provider, profiles) = local()
        profiles.saveTeachingLanguage(TeachingLanguage.HU)

        val result = AccountSetupImpl(FakeAuth(), FakeAccount(null), profiles, provider, Dispatchers.Unconfined).completeSignIn()

        val account = (result as OperationResult.Success).data
        assertFalse(account.onboardingFinished)
        assertFalse(account.isSetUp)
        assertEquals(TeachingLanguage.HU, account.profile?.teachingLanguage)
        assertTrue(provider.isGuest())
    }

    @Test
    fun aFailedCheckAdoptsAndWritesNothing() = runBlocking {
        val (provider, profiles) = local()
        profiles.saveTeachingLanguage(TeachingLanguage.HU)

        val result = AccountSetupImpl(FakeAuth(), FakeAccount(downloadFails = true), profiles, provider, Dispatchers.Unconfined).completeSignIn()

        assertEquals(OperationResult.Error(AuthError.NETWORK), result)
        assertTrue(provider.isGuest())
        assertEquals(TeachingLanguage.HU, profiles.profile.first()!!.teachingLanguage)
    }

    // F8: the account form after a sign-in of an account that is not set up signs in once more; still nothing is adopted,
    // and its sign-up saves while the phone is the guest's.
    @Test
    fun theSignUpAfterASignInOfAnAccountThatIsNotSetUpSavesBeforeAdopting() = runBlocking {
        val (provider, profiles) = local()
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        profiles.saveRole(UserRole.LEARNER)
        val account = FakeAccount(null, localUser = { provider })
        val setup = AccountSetupImpl(FakeAuth(), account, profiles, provider, Dispatchers.Unconfined)

        setup.completeSignIn()
        setup.completeSignIn()
        assertTrue(provider.isGuest())
        val signedUp = setup.completeSignUp("Anna", "Kovács", marketing = false, finishedAt = Instant.fromEpochMilliseconds(9_000))

        assertEquals(OperationResult.Success(Unit), signedUp)
        assertEquals(true, account.guestWhileSaving)
        assertEquals(USER_ID, provider.userId())
    }

    @Test
    fun aServerProfileWithoutSubjectsKeepsTheSubjectsOfThisPhone() = runBlocking {
        val (provider, profiles) = local()
        profiles.saveTeachingLanguage(TeachingLanguage.HU)
        profiles.saveSubjects(listOf(UserSubject(1, compulsory = true)))
        val remote = setUp(student.copy(subjects = emptyList()))

        AccountSetupImpl(FakeAuth(), FakeAccount(remote), profiles, provider, Dispatchers.Unconfined).completeSignIn()

        assertEquals(listOf(UserSubject(1, compulsory = true)), profiles.profile.first()!!.subjects)
    }

    // --- the sign-in with the sync (roadmap 12.5): the account's data comes by one guest-merge sync ---

    private class SyncServer(val fail: Boolean = false) : SyncRepository {
        val merges = mutableListOf<Boolean>()
        override suspend fun sync(cursor: String?, deviceId: String, guestMerge: Boolean, changes: SyncChanges): OperationResult<SyncResponse, SyncError> {
            merges += guestMerge
            if (fail) return OperationResult.Error(SyncError.NETWORK)
            // an account that finished the onboarding, in Slovak
            return OperationResult.Success(SyncResponse(
                serverTime = 1_000, cursor = "c",
                profile = SyncProfile("Anna", "Kovács", "SK", null, onboardingCompletedAt = 5_000, roleId = 1, updatedAt = 10),
                settings = SyncSettings("SK", 25, false, null, updatedAt = 10),
                roles = listOf(1),
            ))
        }
    }

    private class SyncSetup(
        val setup: AccountSetupImpl,
        val provider: LocalUserProvider,
        val profiles: UserProfileRepositoryImpl,
        val store: SyncStoreImpl,
        val engine: SyncEngine,
    )

    private fun withSync(server: SyncServer, account: FakeAccount): SyncSetup {
        val database = UserDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) })
        val provider = LocalUserProvider(database, newDeviceId = { "dev" }, newGuestId = { "guest-1" })
        val profiles = UserProfileRepositoryImpl(database, provider, Dispatchers.Unconfined)
        val store = SyncStoreImpl(database, provider, Dispatchers.Unconfined)
        val session = object : SyncSession {
            override suspend fun isSignedIn() = !provider.isGuest()
            override suspend fun deviceId() = "dev"
        }
        val engine = SyncEngine(server, store, session, now = { 1_000 })
        return SyncSetup(AccountSetupImpl(FakeAuth(), account, profiles, provider, Dispatchers.Unconfined, engine), provider, profiles, store, engine)
    }

    @Test
    fun aSignInWithTheSyncTakesOverTheGuestAndBringsTheAccountsAnswers() = runBlocking {
        val server = SyncServer()
        // the download only tells whether the account is set up; its (Hungarian) profile is never written
        val sync = withSync(server, FakeAccount(setUp()))
        sync.profiles.saveTeachingLanguage(TeachingLanguage.HU)

        val result = sync.setup.completeSignIn()

        val account = (result as OperationResult.Success).data
        assertTrue(account.onboardingFinished)
        assertTrue(account.isSetUp)
        assertEquals(TeachingLanguage.SK, account.profile?.teachingLanguage)
        assertEquals(USER_ID, sync.provider.userId())
        // one sync, in the guest-merge mode
        assertEquals(listOf(true), server.merges)
        assertFalse(sync.store.state().guestMergePending)
    }

    @Test
    fun aSignInWhoseSyncFailsIsANetworkErrorAndTheMergeWaitsForTheNextSync() = runBlocking {
        val sync = withSync(SyncServer(fail = true), FakeAccount(setUp()))
        sync.profiles.saveTeachingLanguage(TeachingLanguage.HU)

        val result = sync.setup.completeSignIn()

        assertEquals(OperationResult.Error(AuthError.NETWORK), result)
        assertEquals(USER_ID, sync.provider.userId())
        assertTrue(sync.store.state().guestMergePending)
    }

    // --- "set up" after a sign-in: the onboarding end AND the TERMS consent, the rule of RemoteProfile.isSetUp ---

    @Test
    fun aFinishedProfileWithoutTheTermsIsNotSetUpAfterASignIn() = runBlocking {
        val (provider, profiles) = local()
        val finished = student.copy(onboardingCompletedAt = Instant.fromEpochMilliseconds(5_000))

        val withoutTerms = AccountSetupImpl(FakeAuth(), FakeAccount(RemoteProfile("Anna", "Kovács", finished)), profiles, provider, Dispatchers.Unconfined)
            .completeSignIn() as OperationResult.Success
        assertFalse(withoutTerms.data.isSetUp)

        val withTerms = AccountSetupImpl(FakeAuth(), FakeAccount(RemoteProfile("Anna", "Kovács", finished, acceptedTerms = true)), profiles, provider, Dispatchers.Unconfined)
            .completeSignIn() as OperationResult.Success
        assertTrue(withTerms.data.isSetUp)
        assertTrue(withTerms.data.onboardingFinished)
    }

    // F2: the guest-merge sync ran before anything was known about the account, and pushed the guest's (finished) profile.
    @Test
    fun aSignInOfAnAccountThatIsNotSetUpSyncsAndAdoptsNothing() = runBlocking {
        val server = SyncServer()
        val remote = RemoteProfile("Anna", "Kovács", student.copy(onboardingCompletedAt = Instant.fromEpochMilliseconds(5_000)), acceptedTerms = false)
        val sync = withSync(server, FakeAccount(remote))
        sync.profiles.saveTeachingLanguage(TeachingLanguage.HU)

        val account = (sync.setup.completeSignIn() as OperationResult.Success).data

        assertFalse(account.isSetUp)
        assertFalse(account.onboardingFinished)
        assertTrue(sync.provider.isGuest())
        assertTrue(server.merges.isEmpty())
        assertEquals(TeachingLanguage.HU, sync.profiles.profile.first()!!.teachingLanguage)
    }

    @Test
    fun aFailedCheckWithTheSyncSyncsAndAdoptsNothing() = runBlocking {
        val server = SyncServer()
        val sync = withSync(server, FakeAccount(downloadFails = true))

        assertEquals(OperationResult.Error(AuthError.NETWORK), sync.setup.completeSignIn())
        assertTrue(sync.provider.isGuest())
        assertTrue(server.merges.isEmpty())
    }

    // "Körülnézek" after a sign-in of an account that is not set up is a guest's: the finished profile stays on this
    // device and never reaches the account.
    @Test
    fun lookingAroundAfterASignInOfAnAccountThatIsNotSetUpPushesNothingToTheAccount() = runBlocking {
        val server = SyncServer()
        val sync = withSync(server, FakeAccount(null))
        sync.profiles.saveTeachingLanguage(TeachingLanguage.HU)
        sync.setup.completeSignIn()

        sync.profiles.completeOnboarding(Instant.fromEpochMilliseconds(9_000))
        val outcome = sync.engine.sync(com.madovygabor.maturimate.features.sync.domain.SyncReason.FOREGROUND)

        assertEquals(com.madovygabor.maturimate.features.sync.domain.SyncOutcome.NotApplicable, outcome)
        assertTrue(server.merges.isEmpty())
        assertTrue(sync.provider.isGuest())
    }

    private companion object {
        const val USER_ID = "8d0fd2b3-9ca7-4c64-9a1e-6b1c9a7f1e11"
    }
}
