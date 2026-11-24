package com.madovygabor.maturimate.features.auth.presentation

import com.madovygabor.maturimate.testutil.testDbDispatcher
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import com.madovygabor.maturimate.features.auth.domain.SignedInAccount
import androidx.compose.ui.test.ExperimentalTestApi
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.features.auth.domain.AccountSetup
import com.madovygabor.maturimate.features.auth.domain.AccountUser
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.domain.AuthRepository
import com.madovygabor.maturimate.features.auth.domain.AuthState
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.madovygabor.maturimate.core.database.LocalUserProvider
import com.madovygabor.maturimate.core.database.foreignKeysOn
import com.madovygabor.maturimate.database.user.UserDatabase
import com.madovygabor.maturimate.features.userProfile.data.UserProfileRepositoryImpl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import com.madovygabor.maturimate.testutil.awaitCondition
import com.madovygabor.maturimate.testutil.awaitFirst
import com.madovygabor.maturimate.testutil.awaitWithin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class, ExperimentalTime::class)
class SignInViewModelTest {

    private class FakeAuth(
        val signInResult: OperationResult<Unit, AuthError> = OperationResult.Success(Unit),
        /** The answers to the new password, one per try; success once they are used up. */
        val passwordResults: MutableList<OperationResult<Unit, AuthError>> = mutableListOf(),
        val resendResult: OperationResult<Unit, AuthError> = OperationResult.Success(Unit),
    ) : AuthRepository {
        var resent = false
        @Volatile var signedOut = false
        val verifiedCodes = mutableListOf<Pair<String, String>>()
        val newPasswords = mutableListOf<String>()
        private val ok: OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
        override val state: Flow<AuthState> = emptyFlow()
        override fun currentUser(): AccountUser? = null
        override suspend fun signUp(email: String, password: String) = ok
        override suspend fun confirmSignUp(email: String, code: String) = ok
        override suspend fun resendSignUpCode(email: String) = resendResult.also { resent = true }
        override suspend fun signIn(email: String, password: String) = signInResult
        override suspend fun signOut() = ok.also { signedOut = true }
        override suspend fun sendPasswordResetCode(email: String) = ok
        override suspend fun verifyResetCode(email: String, code: String) = ok.also { verifiedCodes += email to code }
        override suspend fun setNewPassword(newPassword: String): OperationResult<Unit, AuthError> {
            newPasswords += newPassword
            return passwordResults.removeFirstOrNull() ?: ok
        }
    }

    private class FakeSetup(val finished: Boolean, val profile: UserProfile? = null) : AccountSetup {
        override suspend fun completeSignUp(firstName: String, lastName: String, marketing: Boolean, finishedAt: Instant): OperationResult<Unit, AuthError> =
            OperationResult.Success(Unit)
        override suspend fun completeSignIn(): OperationResult<SignedInAccount, AuthError> = OperationResult.Success(SignedInAccount(finished, profile, isSetUp = finished))
    }

    private fun profiles(): UserProfileRepositoryImpl {
        val database = UserDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, foreignKeysOn()).also { UserDatabase.Schema.create(it) })
        // the app's setup: one thread runs the database calls. With Unconfined the view model (on the main thread) and the
        // test's save ran at the same time on one connection, and the read sometimes failed
        return UserProfileRepositoryImpl(database, LocalUserProvider(database), testDbDispatcher())
    }

    // Decision 6: the screen warns only when this phone holds answers.
    @Test
    fun theStateTellsWhetherThisPhoneHoldsAnswers() = runBlocking {
        val profiles = profiles()
        val viewModel = SignInViewModel(FakeAuth(), FakeSetup(finished = true), SnackbarController(), profiles)
        assertFalse(viewModel.state.value.hasLocalAnswers)

        profiles.saveTeachingLanguage(TeachingLanguage.SK)

        viewModel.state.awaitFirst { it.hasLocalAnswers }
        Unit
    }

    private suspend fun SignInViewModel.idle() = state.awaitFirst { !it.busy }

    @Test
    fun signInLoadsTheAccountAndTellsWhetherItIsDone() = runBlocking {
        val viewModel = SignInViewModel(FakeAuth(), FakeSetup(finished = true), SnackbarController(), profiles())
        viewModel.onEmailChanged("anna@example.com")
        viewModel.onPasswordChanged("Passw0rdA")
        val done = CompletableDeferred<Boolean>()

        viewModel.onSignIn { done.complete(it.onboardingFinished) }

        assertTrue(done.awaitWithin())
    }

    @Test
    fun unconfirmedAccountAsksForTheCode() = runBlocking {
        val auth = FakeAuth(OperationResult.Error(AuthError.EMAIL_NOT_CONFIRMED))
        val viewModel = SignInViewModel(auth, FakeSetup(finished = false), SnackbarController(), profiles())
        viewModel.onEmailChanged("anna@example.com")
        viewModel.onPasswordChanged("Passw0rdA")

        viewModel.onSignIn { error("not signed in yet") }

        assertEquals(SignInPhase.CONFIRM, viewModel.idle().phase)
        assertTrue(auth.resent)
        val done = CompletableDeferred<Boolean>()
        viewModel.onCodeChanged("123456")
        viewModel.onConfirm { done.complete(it.onboardingFinished) }
        assertFalse(done.awaitWithin())
    }

    // The new code was asked for but its result was dropped: a refused e-mail (rate limit) went unnoticed.
    @Test
    fun aRefusedNewCodeIsShownAndTheCodeStepStays() = runBlocking {
        val auth = FakeAuth(OperationResult.Error(AuthError.EMAIL_NOT_CONFIRMED), resendResult = OperationResult.Error(AuthError.TOO_MANY_REQUESTS))
        val snackbar = SnackbarController()
        val viewModel = SignInViewModel(auth, FakeSetup(finished = false), snackbar, profiles())
        viewModel.onEmailChanged("anna@example.com")
        viewModel.onPasswordChanged("Passw0rdA")

        viewModel.onSignIn { error("not signed in yet") }

        assertEquals(AuthError.TOO_MANY_REQUESTS.toUiText(), snackbar.events.awaitFirst().message)
        // a code sent a moment ago still works
        assertEquals(SignInPhase.CONFIRM, viewModel.idle().phase)
    }

    // F6: while Google (the browser on desktop) is open the other buttons wait, and Google's answer is never dropped.
    @Test
    fun whileGoogleIsOpenTheOtherActionsWaitAndItsAnswerIsNeverDropped() = runBlocking {
        val viewModel = SignInViewModel(FakeAuth(), FakeSetup(finished = true), SnackbarController(), profiles())
        viewModel.onEmailChanged("anna@example.com")
        viewModel.onPasswordChanged("Passw0rdA")

        viewModel.onGoogle {}
        assertTrue(viewModel.state.value.busy)
        viewModel.onSignIn { error("the e-mail sign-in waits while Google is open") }
        viewModel.onForgotPassword()
        assertEquals(SignInPhase.FORM, viewModel.state.value.phase)

        val done = CompletableDeferred<Boolean>()
        viewModel.onGoogleSignedIn { done.complete(it.onboardingFinished) }
        assertTrue(done.awaitWithin())
    }

    @Test
    fun aClosedOrFailedGoogleFreesTheButtons() {
        val viewModel = SignInViewModel(FakeAuth(), FakeSetup(finished = true), SnackbarController(), profiles())
        viewModel.onGoogle {}
        viewModel.onGoogleClosed()
        assertFalse(viewModel.state.value.busy)
        viewModel.onGoogle {}
        viewModel.onGoogleFailed(AuthError.UNKNOWN)
        assertFalse(viewModel.state.value.busy)
    }

    @Test
    fun forgottenPasswordIsResetWithTheCode() = runBlocking {
        val auth = FakeAuth()
        val viewModel = SignInViewModel(auth, FakeSetup(finished = true), SnackbarController(), profiles())
        viewModel.onForgotPassword()
        viewModel.onEmailChanged("anna@example.com")
        viewModel.onSendResetCode()
        assertEquals(SignInPhase.RESET_CODE, viewModel.idle().phase)

        viewModel.onCodeChanged("123456")
        viewModel.onNewPasswordChanged("weak")
        viewModel.onResetPassword { error("the password is too weak") }
        assertEquals(FieldError.PASSWORD_WEAK, viewModel.state.value.passwordError)

        viewModel.onNewPasswordChanged("NewPassw0rd")
        val done = CompletableDeferred<Boolean>()
        viewModel.onResetPassword { done.complete(it.onboardingFinished) }
        assertTrue(done.awaitWithin())
        assertEquals(listOf("anna@example.com" to "123456"), auth.verifiedCodes)
        assertEquals(listOf("NewPassw0rd"), auth.newPasswords)
    }

    // The code and the new password used to go in one call: when the server refused the password,
    // the retry checked the code again, which was used up by then, and only a new code helped.
    @Test
    fun aRefusedNewPasswordCanBeRetriedWithoutANewCode() = runBlocking {
        val auth = FakeAuth(passwordResults = mutableListOf(OperationResult.Error(AuthError.SAME_PASSWORD)))
        val viewModel = SignInViewModel(auth, FakeSetup(finished = true), SnackbarController(), profiles())
        viewModel.onForgotPassword()
        viewModel.onEmailChanged("anna@example.com")
        viewModel.onSendResetCode()
        viewModel.idle()
        viewModel.onCodeChanged("123456")
        viewModel.onNewPasswordChanged("OldPassw0rd")

        viewModel.onResetPassword { error("the server refused the password") }
        assertTrue(viewModel.idle().resetCodeVerified)

        viewModel.onNewPasswordChanged("NewPassw0rd")
        val done = CompletableDeferred<Boolean>()
        viewModel.onResetPassword { done.complete(it.onboardingFinished) }

        assertTrue(done.awaitWithin())
        assertEquals(1, auth.verifiedCodes.size)
        assertEquals(listOf("OldPassw0rd", "NewPassw0rd"), auth.newPasswords)
    }

    @Test
    fun leavingAfterAnAcceptedCodeSignsOutAgain() = runBlocking {
        val auth = FakeAuth(passwordResults = mutableListOf(OperationResult.Error(AuthError.NETWORK)))
        val viewModel = SignInViewModel(auth, FakeSetup(finished = true), SnackbarController(), profiles())
        viewModel.onForgotPassword()
        viewModel.onEmailChanged("anna@example.com")
        viewModel.onSendResetCode()
        viewModel.idle()
        viewModel.onCodeChanged("123456")
        viewModel.onNewPasswordChanged("NewPassw0rd")
        viewModel.onResetPassword { error("no connection") }
        assertTrue(viewModel.idle().resetCodeVerified)

        viewModel.onBack()

        awaitCondition { auth.signedOut }
        assertFalse(viewModel.state.value.resetCodeVerified)
    }

    @Test
    fun signInGivesTheAnswersToContinueFrom() = runBlocking {
        val answers = UserProfile(
            teachingLanguage = TeachingLanguage.SK,
            role = UserRole.LEARNER,
            graduationDate = null,
            uiLanguage = TeachingLanguage.SK,
            dailyGoalMinutes = null,
            timezone = "Europe/Bratislava",
            onboardingCompletedAt = null,
        )
        val viewModel = SignInViewModel(FakeAuth(), FakeSetup(finished = false, profile = answers), SnackbarController(), profiles())
        viewModel.onEmailChanged("anna@example.com")
        viewModel.onPasswordChanged("Passw0rdA")
        val account = CompletableDeferred<SignedInAccount>()

        viewModel.onSignIn { account.complete(it) }

        assertEquals(answers, account.awaitWithin().profile)
    }

    @Test
    fun backReturnsToTheFormBeforeLeaving() {
        val viewModel = SignInViewModel(FakeAuth(), FakeSetup(finished = true), SnackbarController(), profiles())
        viewModel.onForgotPassword()

        assertTrue(viewModel.onBack())
        assertEquals(SignInPhase.FORM, viewModel.state.value.phase)
        assertFalse(viewModel.onBack())
    }
}
