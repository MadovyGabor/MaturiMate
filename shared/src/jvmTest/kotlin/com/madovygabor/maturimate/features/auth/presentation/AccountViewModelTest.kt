package com.madovygabor.maturimate.features.auth.presentation

import com.madovygabor.maturimate.features.auth.domain.SignedInAccount
import androidx.compose.ui.test.ExperimentalTestApi
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.core.presentation.utils.UiText
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarEvent
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.auth.domain.AccountSetup
import com.madovygabor.maturimate.features.auth.domain.AccountUser
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.domain.AuthRepository
import com.madovygabor.maturimate.features.auth.domain.AuthState
import com.madovygabor.maturimate.features.userProfile.data.UserProfileRepositoryImpl
import com.madovygabor.maturimate.features.userProfile.domain.DataError
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.madovygabor.maturimate.testutil.awaitFirst
import com.madovygabor.maturimate.testutil.testDbDispatcher
import kotlinx.coroutines.withTimeoutOrNull
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.error_io
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString

@OptIn(ExperimentalTestApi::class, ExperimentalTime::class)
class AccountViewModelTest {

    private fun text(key: StringResource) = runBlocking { getString(key) }

    private class FakeAuth(var signUpResult: OperationResult<Unit, AuthError> = OperationResult.Success(Unit)) : AuthRepository {
        var signedUp: Pair<String, String>? = null
        @Volatile var signedOut = false
        var confirmed: String? = null
        var user: AccountUser? = null
        private val ok: OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
        override val state: Flow<AuthState> = emptyFlow()
        override fun currentUser() = user
        override suspend fun signUp(email: String, password: String) = signUpResult.also { signedUp = email to password }
        override suspend fun confirmSignUp(email: String, code: String): OperationResult<Unit, AuthError> =
            if (code == "123456") ok.also { confirmed = code } else OperationResult.Error(AuthError.INVALID_CODE)
        override suspend fun resendSignUpCode(email: String) = ok
        override suspend fun signIn(email: String, password: String) = ok
        override suspend fun signOut() = ok.also { signedOut = true; user = null }
        override suspend fun sendPasswordResetCode(email: String) = ok
        override suspend fun verifyResetCode(email: String, code: String) = ok
        override suspend fun setNewPassword(newPassword: String) = ok
    }

    private class FakeSetup(
        val existingProfile: Boolean = false,
        /** Whether that account is set up (onboarding end AND TERMS); a finished profile without TERMS is not. */
        val setUp: Boolean = existingProfile,
        /** The answers to the sign-ins, one per call; then the answer of [existingProfile]. */
        val signInResults: MutableList<OperationResult<SignedInAccount, AuthError>> = mutableListOf(),
    ) : AccountSetup {
        var signedUp: Triple<String, String, Boolean>? = null
        var finishedAt: Instant? = null
        var signIns = 0
        override suspend fun completeSignUp(firstName: String, lastName: String, marketing: Boolean, finishedAt: Instant): OperationResult<Unit, AuthError> {
            signedUp = Triple(firstName, lastName, marketing)
            this.finishedAt = finishedAt
            return OperationResult.Success(Unit)
        }
        override suspend fun completeSignIn(): OperationResult<SignedInAccount, AuthError> {
            signIns++
            return signInResults.removeFirstOrNull() ?: OperationResult.Success(SignedInAccount(existingProfile, null, isSetUp = setUp))
        }
    }

    private val noon = Instant.fromEpochMilliseconds(1_800_000_000_000)

    private fun viewModel(
        auth: AuthRepository = FakeAuth(),
        setup: AccountSetup = FakeSetup(),
        profiles: UserProfileRepository = profiles(),
        snackbar: SnackbarController = SnackbarController(),
    ) = AccountViewModel(auth, setup, profiles, snackbar, now = { noon })

    private fun profiles(): UserProfileRepositoryImpl {
        val database = UserDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) })
        return UserProfileRepositoryImpl(database, LocalUserProvider(database), testDbDispatcher())
    }

    /** The next snackbar if one comes within a moment; null otherwise. */
    private fun Flow<SnackbarEvent>.nextOrNull() = runBlocking { withTimeoutOrNull(200) { first() } }

    private suspend fun AccountViewModel.idle() = state.awaitFirst { !it.busy }

    private fun AccountViewModel.fill(email: String = "anna@example.com", password: String = "Passw0rdA", repeat: String = password) {
        onChanged(AccountField.LAST_NAME, "Kovács")
        onChanged(AccountField.FIRST_NAME, "Anna")
        onChanged(AccountField.EMAIL, email)
        onChanged(AccountField.PASSWORD, password)
        onChanged(AccountField.PASSWORD_REPEAT, repeat)
        onConsentChanged(true)
    }

    @Test
    fun wrongFieldsAreMarkedAndNothingIsSent() = runBlocking {
        val auth = FakeAuth()
        val viewModel = viewModel(auth)
        viewModel.fill(email = "anna", password = "short")

        viewModel.onCreateAccount()

        assertEquals(
            mapOf(AccountField.EMAIL to FieldError.EMAIL_INVALID, AccountField.PASSWORD to FieldError.PASSWORD_WEAK),
            viewModel.state.value.errors,
        )
        assertNull(auth.signedUp)
    }

    @Test
    fun aRepeatThatDiffersIsMarkedAndNothingIsSent() = runBlocking {
        val auth = FakeAuth()
        val viewModel = viewModel(auth)
        viewModel.fill(repeat = "Passw0rdB")

        viewModel.onCreateAccount()

        assertEquals(mapOf(AccountField.PASSWORD_REPEAT to FieldError.PASSWORDS_DIFFER), viewModel.state.value.errors)
        assertNull(auth.signedUp)

        // typing the repeat again clears the mark, and the matching form goes on
        viewModel.onChanged(AccountField.PASSWORD_REPEAT, "Passw0rdA")
        assertEquals(emptyMap(), viewModel.state.value.errors)
        viewModel.onCreateAccount()
        assertEquals("anna@example.com", viewModel.idle().codeSentTo)
    }

    @Test
    fun signUpAsksForTheCodeThenSetsUpTheAccount() = runBlocking {
        val auth = FakeAuth()
        val setup = FakeSetup()
        val viewModel = viewModel(auth, setup)
        viewModel.fill()
        viewModel.onNewsChanged(true)

        viewModel.onCreateAccount()
        assertEquals("anna@example.com", viewModel.idle().codeSentTo)

        viewModel.onCodeChanged("000000")
        viewModel.onConfirmCode()
        assertEquals(FieldError.CODE_INVALID, viewModel.idle().codeError)

        viewModel.onCodeChanged("123456")
        viewModel.onConfirmCode()
        viewModel.idle()
        assertEquals(Triple("Anna", "Kovács", true), setup.signedUp)
    }

    // Decision 17: the taken address is said on the e-mail field, not in a snackbar, and no code step opens.
    @Test
    fun takenAddressIsShownOnTheField() = runBlocking {
        val snackbar = SnackbarController()
        val viewModel = viewModel(FakeAuth(OperationResult.Error(AuthError.EMAIL_TAKEN)), snackbar = snackbar)
        viewModel.fill()

        viewModel.onCreateAccount()

        assertEquals(FieldError.EMAIL_TAKEN, viewModel.idle().errors[AccountField.EMAIL])
        assertNull(viewModel.state.value.codeSentTo)
        assertNull(snackbar.events.nextOrNull())
        // typing a new address clears it
        viewModel.onChanged(AccountField.EMAIL, "anna2@example.com")
        assertNull(viewModel.state.value.errors[AccountField.EMAIL])
    }

    @Test
    fun browsingAsAGuestFinishesTheOnboarding() = runBlocking {
        val profiles = profiles()
        val viewModel = viewModel(profiles = profiles)

        viewModel.onBrowseAsGuest()
        viewModel.idle()

        assertTrue(profiles.profile.first()!!.isOnboardingCompleted)
        // the day comes from the clock given to the view model
        assertEquals(noon, profiles.profile.first()!!.onboardingCompletedAt)
    }

    // The end of the onboarding was saved without looking at the result: a failed save left the button doing nothing.
    @Test
    fun aFailedEndOfTheOnboardingIsShown() = runBlocking {
        val failing = object : UserProfileRepository by profiles() {
            override suspend fun completeOnboarding(at: Instant): OperationResult<Unit, DataError> = OperationResult.Error(DataError.IO_ERROR)
        }
        val snackbar = SnackbarController()
        val viewModel = viewModel(profiles = failing, snackbar = snackbar)

        viewModel.onBrowseAsGuest()

        val shown = snackbar.events.awaitFirst().message as UiText.StringRes
        assertEquals(text(Res.string.error_io), text(shown.res))
        assertFalse(viewModel.idle().busy)
    }

    // A session restored on this device (any provider, not only Google) may belong to an account that is set up
    // already: it is a sign-in, and the sign-up (save_sign_up) must never run for it.
    @Test
    fun aRestoredSessionOfASetUpAccountIsASignInAndNeverASignUp() = runBlocking {
        val auth = FakeAuth().apply { user = AccountUser("id", "anna@example.com", null, null) }
        val setup = FakeSetup(existingProfile = true)
        val viewModel = viewModel(auth, setup)

        val state = viewModel.idle()

        assertEquals(1, setup.signIns)
        assertTrue(state.done)
        viewModel.fill()
        viewModel.onCreateAccount()
        viewModel.idle()
        assertNull(setup.signedUp)
    }

    @Test
    fun aRestoredSessionOfANewAccountKeepsTheFormWithTheAddress() = runBlocking {
        val auth = FakeAuth().apply { user = AccountUser("id", "anna@example.com", null, null) }
        val setup = FakeSetup(existingProfile = false)
        val viewModel = viewModel(auth, setup)

        val state = viewModel.idle()

        assertEquals(1, setup.signIns)
        assertFalse(state.done)
        assertEquals("anna@example.com", state.signedInEmail)
        viewModel.onChanged(AccountField.LAST_NAME, "Kovács")
        viewModel.onChanged(AccountField.FIRST_NAME, "Anna")
        viewModel.onConsentChanged(true)
        viewModel.onCreateAccount()
        viewModel.idle()
        assertEquals(Triple("Anna", "Kovács", false), setup.signedUp)
        assertEquals(noon, setup.finishedAt)
    }

    // Offline at the start: the account could not be checked, so the form checks it again before any sign-up.
    @Test
    fun aRestoredSessionThatCouldNotBeCheckedIsCheckedBeforeTheSignUp() = runBlocking {
        val auth = FakeAuth().apply { user = AccountUser("id", "anna@example.com", null, null) }
        val setup = FakeSetup(existingProfile = true, signInResults = mutableListOf(OperationResult.Error(AuthError.NETWORK)))
        val snackbar = SnackbarController()
        val viewModel = viewModel(auth, setup, snackbar = snackbar)
        viewModel.idle()
        assertEquals(AuthError.NETWORK.toUiText(), snackbar.events.awaitFirst().message)
        assertFalse(viewModel.state.value.done)

        viewModel.onChanged(AccountField.LAST_NAME, "Kovács")
        viewModel.onChanged(AccountField.FIRST_NAME, "Anna")
        viewModel.onConsentChanged(true)
        viewModel.onCreateAccount()

        assertTrue(viewModel.idle().done)
        assertEquals(2, setup.signIns)
        assertNull(setup.signedUp)
    }

    // A finished profile without the TERMS consent (a sync pushed it before save_sign_up went through, e.g. the app
    // was killed): it is not set up, so it must not be done without the consents; its form runs the sign-up.
    @Test
    fun aRestoredFinishedAccountWithoutTheTermsKeepsTheFormAndSignsUp() = runBlocking {
        val auth = FakeAuth().apply { user = AccountUser("id", "anna@example.com", null, null) }
        val setup = FakeSetup(existingProfile = true, setUp = false)
        val viewModel = viewModel(auth, setup)

        val state = viewModel.idle()

        assertFalse(state.done)
        assertEquals("anna@example.com", state.signedInEmail)
        viewModel.onChanged(AccountField.LAST_NAME, "Kovács")
        viewModel.onChanged(AccountField.FIRST_NAME, "Anna")
        viewModel.onConsentChanged(true)
        viewModel.onCreateAccount()
        assertTrue(viewModel.idle().done)
        assertEquals(Triple("Anna", "Kovács", false), setup.signedUp)
        assertEquals(1, setup.signIns)
    }

    @Test
    fun googleToAFinishedAccountWithoutTheTermsKeepsTheForm() = runBlocking {
        val auth = FakeAuth()
        val setup = FakeSetup(existingProfile = true, setUp = false)
        val viewModel = viewModel(auth, setup)
        auth.user = AccountUser("id", "anna@example.com", "Anna", "Kovács")

        viewModel.onGoogleSignedIn()

        val state = viewModel.idle()
        assertFalse(state.done)
        assertEquals("anna@example.com", state.signedInEmail)
        assertNull(setup.signedUp)
    }

    // F6: while Google (the browser on desktop) is open the other buttons wait, and Google's answer is never dropped.
    @Test
    fun whileGoogleIsOpenTheOtherActionsWaitAndItsAnswerIsNeverDropped() = runBlocking {
        val auth = FakeAuth()
        val setup = FakeSetup(existingProfile = true)
        val profiles = profiles()
        val viewModel = viewModel(auth, setup, profiles)

        viewModel.onGoogle {}
        assertTrue(viewModel.state.value.busy)
        viewModel.fill()
        viewModel.onCreateAccount()
        viewModel.onBrowseAsGuest()
        assertNull(auth.signedUp)
        assertFalse(profiles.profile.first()?.isOnboardingCompleted ?: false)

        auth.user = AccountUser("id", "anna@example.com", "Anna", "Kovács")
        viewModel.onGoogleSignedIn()

        assertTrue(viewModel.idle().done)
        assertEquals(1, setup.signIns)
    }

    @Test
    fun aClosedOrFailedGoogleFreesTheButtons() = runBlocking {
        val viewModel = viewModel()
        viewModel.onGoogle {}
        viewModel.onGoogleClosed()
        assertFalse(viewModel.state.value.busy)

        viewModel.onGoogle {}
        viewModel.onGoogleFailed(AuthError.UNKNOWN)
        assertFalse(viewModel.state.value.busy)
    }

    // F7: the check belongs to one account; another session (signed out and in again meanwhile) is checked again.
    @Test
    fun anotherSessionIsCheckedAgainBeforeTheSignUp() = runBlocking {
        val auth = FakeAuth().apply { user = AccountUser("id-1", "anna@example.com", null, null) }
        val setup = FakeSetup(existingProfile = false)
        val viewModel = viewModel(auth, setup)
        viewModel.idle()

        auth.user = AccountUser("id-2", "bea@example.com", null, null)
        setup.signInResults += OperationResult.Success(SignedInAccount(onboardingFinished = true, profile = null, isSetUp = true))
        viewModel.onChanged(AccountField.LAST_NAME, "Kovács")
        viewModel.onChanged(AccountField.FIRST_NAME, "Anna")
        viewModel.onConsentChanged(true)
        viewModel.onCreateAccount()

        assertTrue(viewModel.idle().done)
        assertEquals(2, setup.signIns)
        assertNull(setup.signedUp)
    }

    // N1: "Körülnézek" with a session open (an account that is not set up) goes on as a guest: the session ends first,
    // so the phone is not a guest's with someone else's session.
    @Test
    fun lookingAroundWithASessionEndsTheSessionAndGoesOnAsAGuest() = runBlocking {
        val auth = FakeAuth().apply { user = AccountUser("id", "anna@example.com", null, null) }
        val profiles = profiles()
        val viewModel = viewModel(auth, FakeSetup(existingProfile = false), profiles)
        viewModel.idle()

        viewModel.onBrowseAsGuest()
        val state = viewModel.idle()

        assertTrue(auth.signedOut)
        assertNull(state.signedInEmail)
        assertTrue(profiles.profile.first()!!.isOnboardingCompleted)
    }

    @Test
    fun lookingAroundWithoutASessionSignsNothingOut() = runBlocking {
        val auth = FakeAuth()
        val viewModel = viewModel(auth)

        viewModel.onBrowseAsGuest()
        viewModel.idle()

        assertFalse(auth.signedOut)
    }

    @Test
    fun googleAccountWithAProfileIsASignInAndANewOneKeepsTheForm() = runBlocking {
        val existing = FakeSetup(existingProfile = true)
        val signIn = viewModel(setup = existing)
        signIn.onGoogleSignedIn()
        signIn.idle()
        assertNull(existing.signedUp)

        val auth = FakeAuth().apply { user = AccountUser("id", "anna@example.com", "Anna", "Kovács") }
        val fresh = FakeSetup(existingProfile = false)
        val signUp = viewModel(auth, fresh)
        signUp.onConsentChanged(true)
        signUp.onGoogleSignedIn()
        signUp.idle()
        // the name from Google and the consent are there, so the account is set up at once
        assertEquals(Triple("Anna", "Kovács", false), fresh.signedUp)
    }

    @Test
    fun newGoogleAccountWaitsForTheNameAndTheConsent() = runBlocking {
        val auth = FakeAuth().apply { user = AccountUser("id", "anna@example.com", "Anna", "Kovács") }
        val setup = FakeSetup(existingProfile = false)
        val viewModel = viewModel(auth, setup)

        viewModel.onGoogleSignedIn()
        viewModel.idle()
        // back from Google: the name is filled in, but nothing is set up before the consent
        assertEquals("anna@example.com", viewModel.state.value.signedInEmail)
        assertEquals("Anna", viewModel.state.value.firstName)
        assertNull(setup.signedUp)

        viewModel.onConsentChanged(true)
        viewModel.onCreateAccount()
        viewModel.idle()
        assertEquals(Triple("Anna", "Kovács", false), setup.signedUp)
    }

    @Test
    fun googleOpensBeforeTheConsentButTheSignUpAsksForIt() = runBlocking {
        val auth = FakeAuth()
        val viewModel = viewModel(auth)
        var googleOpened = false

        viewModel.onGoogle { googleOpened = true }
        assertTrue(googleOpened)
        assertFalse(viewModel.state.value.consentMissing)
        viewModel.onGoogleClosed()

        viewModel.fill()
        viewModel.onConsentChanged(false)
        viewModel.onCreateAccount()
        assertNull(auth.signedUp)
        assertTrue(viewModel.state.value.consentMissing)

        viewModel.onConsentChanged(true)
        assertFalse(viewModel.state.value.consentMissing)
    }
}
