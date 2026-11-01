package com.madovygabor.maturimate.features.auth.presentation

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
import maturimate.composeapp.generated.resources.Res
import maturimate.composeapp.generated.resources.reset_save
import maturimate.composeapp.generated.resources.signin_why
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

    private class FakeAuth(val signInResult: OperationResult<Unit, AuthError> = OperationResult.Success(Unit)) : AuthRepository {
        var resent = false
        var reset: Triple<String, String, String>? = null
        private val ok: OperationResult<Unit, AuthError> = OperationResult.Success(Unit)
        override val state: Flow<AuthState> = emptyFlow()
        override fun currentUser(): AccountUser? = null
        override suspend fun signUp(email: String, password: String) = ok
        override suspend fun confirmSignUp(email: String, code: String) = ok
        override suspend fun resendSignUpCode(email: String) = ok.also { resent = true }
        override suspend fun signIn(email: String, password: String) = signInResult
        override suspend fun signOut() = ok
        override suspend fun sendPasswordResetCode(email: String) = ok
        override suspend fun resetPassword(email: String, code: String, newPassword: String) = ok.also { reset = Triple(email, code, newPassword) }
    }

    private class FakeSetup(val finished: Boolean) : AccountSetup {
        override suspend fun completeSignUp(firstName: String, lastName: String, marketing: Boolean, finishedAt: Instant): OperationResult<Unit, AuthError> =
            OperationResult.Success(Unit)
        override suspend fun completeSignIn(): OperationResult<Boolean, AuthError> = OperationResult.Success(finished)
    }

    private suspend fun SignInViewModel.idle() = withTimeout(5_000) { state.first { !it.busy } }

    @Test
    fun signInLoadsTheAccountAndTellsWhetherItIsDone() = runBlocking {
        val viewModel = SignInViewModel(FakeAuth(), FakeSetup(finished = true), SnackbarController())
        viewModel.onEmailChanged("anna@example.com")
        viewModel.onPasswordChanged("Passw0rdA")
        val done = CompletableDeferred<Boolean>()

        viewModel.onSignIn { done.complete(it) }

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
        viewModel.onConfirm { done.complete(it) }
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
        viewModel.onResetPassword { done.complete(it) }
        assertTrue(withTimeout(5_000) { done.await() })
        assertEquals(Triple("anna@example.com", "123456", "NewPassw0rd"), auth.reset)
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
