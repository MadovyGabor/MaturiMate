package com.madovygabor.maturimate.features.auth.presentation

import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import com.madovygabor.maturimate.features.userProfile.domain.UserProfile
import com.madovygabor.maturimate.features.auth.domain.SignedInAccount
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.features.auth.domain.AccountSetup
import com.madovygabor.maturimate.features.auth.domain.AccountUser
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.domain.AuthRepository
import com.madovygabor.maturimate.features.auth.domain.AuthState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.reset_save
import maturimate.shared.generated.resources.signin_why
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import java.io.File
import java.util.Locale
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class, ExperimentalTime::class)
class SignInTest {

    private val defaultLocale: Locale = Locale.getDefault()

    @AfterTest
    fun restoreLocale() = Locale.setDefault(defaultLocale)

    private fun text(key: StringResource) = runBlocking { getString(key) }

    private class FakeAuth(
        val signInResult: OperationResult<Unit, AuthError> = OperationResult.Success(Unit),
        /** The answers to the new password, one per try; success once they are used up. */
        val passwordResults: MutableList<OperationResult<Unit, AuthError>> = mutableListOf(),
    ) : AuthRepository {
        var resent = false
        var signedOut = false
        val verifiedCodes = mutableListOf<Pair<String, String>>()
        val newPasswords = mutableListOf<String>()
        private val ok: OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
        override val state: Flow<AuthState> = emptyFlow()
        override fun currentUser(): AccountUser? = null
        override suspend fun signUp(email: String, password: String) = ok
        override suspend fun confirmSignUp(email: String, code: String) = ok
        override suspend fun resendSignUpCode(email: String) = ok.also { resent = true }
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
        override suspend fun completeSignIn(): OperationResult<SignedInAccount, AuthError> = OperationResult.Success(SignedInAccount(finished, profile))
    }

    private suspend fun SignInViewModel.idle() = withTimeout(5_000) { state.first { !it.busy } }

    @Test
    fun signInLoadsTheAccountAndTellsWhetherItIsDone() = runBlocking {
        val viewModel = SignInViewModel(FakeAuth(), FakeSetup(finished = true), SnackbarController())
        viewModel.onEmailChanged("anna@example.com")
        viewModel.onPasswordChanged("Passw0rdA")
        val done = CompletableDeferred<Boolean>()

        viewModel.onSignIn { done.complete(it.onboardingFinished) }

        assertTrue(withTimeout(5_000) { done.await() })
    }

    @Test
    fun unconfirmedAccountAsksForTheCode() = runBlocking {
        val auth = FakeAuth(OperationResult.Error(AuthError.EMAIL_NOT_CONFIRMED))
        val viewModel = SignInViewModel(auth, FakeSetup(finished = false), SnackbarController())
        viewModel.onEmailChanged("anna@example.com")
        viewModel.onPasswordChanged("Passw0rdA")

        viewModel.onSignIn { error("not signed in yet") }

        assertEquals(SignInPhase.CONFIRM, viewModel.idle().phase)
        assertTrue(auth.resent)
        val done = CompletableDeferred<Boolean>()
        viewModel.onCodeChanged("123456")
        viewModel.onConfirm { done.complete(it.onboardingFinished) }
        assertFalse(withTimeout(5_000) { done.await() })
    }

    @Test
    fun forgottenPasswordIsResetWithTheCode() = runBlocking {
        val auth = FakeAuth()
        val viewModel = SignInViewModel(auth, FakeSetup(finished = true), SnackbarController())
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
        assertTrue(withTimeout(5_000) { done.await() })
        assertEquals(listOf("anna@example.com" to "123456"), auth.verifiedCodes)
        assertEquals(listOf("NewPassw0rd"), auth.newPasswords)
    }

    // The code and the new password used to go in one call: when the server refused the password,
    // the retry checked the code again, which was used up by then, and only a new code helped.
    @Test
    fun aRefusedNewPasswordCanBeRetriedWithoutANewCode() = runBlocking {
        val auth = FakeAuth(passwordResults = mutableListOf(OperationResult.Error(AuthError.SAME_PASSWORD)))
        val viewModel = SignInViewModel(auth, FakeSetup(finished = true), SnackbarController())
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

        assertTrue(withTimeout(5_000) { done.await() })
        assertEquals(1, auth.verifiedCodes.size)
        assertEquals(listOf("OldPassw0rd", "NewPassw0rd"), auth.newPasswords)
    }

    @Test
    fun leavingAfterAnAcceptedCodeSignsOutAgain() = runBlocking {
        val auth = FakeAuth(passwordResults = mutableListOf(OperationResult.Error(AuthError.NETWORK)))
        val viewModel = SignInViewModel(auth, FakeSetup(finished = true), SnackbarController())
        viewModel.onForgotPassword()
        viewModel.onEmailChanged("anna@example.com")
        viewModel.onSendResetCode()
        viewModel.idle()
        viewModel.onCodeChanged("123456")
        viewModel.onNewPasswordChanged("NewPassw0rd")
        viewModel.onResetPassword { error("no connection") }
        assertTrue(viewModel.idle().resetCodeVerified)

        viewModel.onBack()

        withTimeout(5_000) { while (!auth.signedOut) kotlinx.coroutines.delay(10) }
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
        val viewModel = SignInViewModel(FakeAuth(), FakeSetup(finished = false, profile = answers), SnackbarController())
        viewModel.onEmailChanged("anna@example.com")
        viewModel.onPasswordChanged("Passw0rdA")
        val account = CompletableDeferred<SignedInAccount>()

        viewModel.onSignIn { account.complete(it) }

        assertEquals(answers, withTimeout(5_000) { account.await() }.profile)
    }

    @Test
    fun backReturnsToTheFormBeforeLeaving() {
        val viewModel = SignInViewModel(FakeAuth(), FakeSetup(finished = true), SnackbarController())
        viewModel.onForgotPassword()

        assertTrue(viewModel.onBack())
        assertEquals(SignInPhase.FORM, viewModel.state.value.phase)
        assertFalse(viewModel.onBack())
    }

    /** Pictures of the sign-in and the reset, both themes and languages, into build/screenshots/signin. */
    @Test
    fun rendersAtEverySizeAndTheme() {
        val sizes = mapOf("phone" to (412 to 915), "desktop" to (1280 to 800))
        val dir = File("build/screenshots/signin").apply { mkdirs() }
        val form = SignInUiState(email = "anna@example.com", password = "secret")
        val reset = SignInUiState(phase = SignInPhase.RESET_CODE, email = "anna@example.com", code = "123")
        for (language in listOf("hu", "sk")) {
            Locale.setDefault(Locale.forLanguageTag(language))
            for ((name, size) in sizes) for (dark in listOf(false, true)) for ((step, state) in listOf("form" to form, "reset" to reset)) {
                runDesktopComposeUiTest(size.first, size.second) {
                    setContent { AppTheme(darkTheme = dark) { SignInContent(state, SignInActions()) } }
                    onNodeWithText(text(if (step == "form") Res.string.signin_why else Res.string.reset_save)).assertExists()
                    ImageIO.write(onRoot().captureToImage().toAwtImage(), "png", File(dir, "signin-$step-$language-$name-${if (dark) "dark" else "light"}.png"))
                }
            }
        }
    }
}
