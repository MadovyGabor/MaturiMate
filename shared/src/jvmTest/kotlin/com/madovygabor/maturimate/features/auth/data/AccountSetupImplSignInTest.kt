package com.madovygabor.maturimate.features.auth.data

import kotlin.test.assertFalse
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.auth.domain.AuthError
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
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import com.madovygabor.maturimate.features.userProfile.domain.UserSubject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class AccountSetupImplSignInTest : AccountSetupImplTestBase() {

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
}
