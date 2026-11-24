package com.madovygabor.maturimate.features.auth.data

import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.domain.ConsentType
import com.madovygabor.maturimate.features.auth.domain.RemoteProfile
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class AccountSetupImplSignUpTest : AccountSetupImplTestBase() {

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
}
