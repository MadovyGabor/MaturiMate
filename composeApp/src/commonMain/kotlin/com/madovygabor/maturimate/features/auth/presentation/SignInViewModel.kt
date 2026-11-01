package com.madovygabor.maturimate.features.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.features.auth.domain.AccountRules
import com.madovygabor.maturimate.features.auth.domain.AccountSetup
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.domain.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The parts of the sign-in screen. */
enum class SignInPhase {
    /** E-mail and password, or Google. */
    FORM,

    /** The account is not confirmed yet: the sign-up code. */
    CONFIRM,

    /** Forgotten password: the e-mail address that gets the code. */
    RESET_REQUEST,

    /** Forgotten password: the code and the new password. */
    RESET_CODE,
}

data class SignInUiState(
    val phase: SignInPhase = SignInPhase.FORM,
    val email: String = "",
    val password: String = "",
    val code: String = "",
    val newPassword: String = "",
    val emailError: FieldError? = null,
    val passwordError: FieldError? = null,
    val codeError: FieldError? = null,
    val busy: Boolean = false,
)

/**
 * "Már van fiókom": sign in with e-mail and password or Google, confirm an unconfirmed account,
 * or reset a forgotten password; then the account's profile comes to this phone.
 */
class SignInViewModel(
    private val auth: AuthRepository,
    private val accountSetup: AccountSetup,
    private val snackbarController: SnackbarController,
) : ViewModel() {

    private val _state = MutableStateFlow(SignInUiState())
    val state: StateFlow<SignInUiState> = _state.asStateFlow()

    fun onEmailChanged(value: String) = _state.update { it.copy(email = value, emailError = null) }
    fun onPasswordChanged(value: String) = _state.update { it.copy(password = value, passwordError = null) }
    fun onNewPasswordChanged(value: String) = _state.update { it.copy(newPassword = value, passwordError = null) }
    fun onCodeChanged(value: String) = _state.update { it.copy(code = value.filter { c -> c.isDigit() }.take(AccountRules.CODE_LENGTH), codeError = null) }

    fun onForgotPassword() = _state.update { it.copy(phase = SignInPhase.RESET_REQUEST, code = "", newPassword = "") }

    /** Back inside the screen; false when the form is shown and Back leaves the screen. */
    fun onBack(): Boolean {
        if (_state.value.phase == SignInPhase.FORM) return false
        _state.update { it.copy(phase = SignInPhase.FORM, code = "", codeError = null, passwordError = null) }
        return true
    }

    /**
     * "Belépés". [onSignedIn] gets whether the account finished the onboarding before: true goes
     * home, false continues the onboarding from the account's answers.
     */
    fun onSignIn(onSignedIn: (Boolean) -> Unit) {
        val current = _state.value
        if (!AccountRules.isEmail(current.email)) return _state.update { it.copy(emailError = FieldError.EMAIL_INVALID) }
        if (current.password.isEmpty()) return
        busy {
            when (val result = auth.signIn(current.email.trim(), current.password)) {
                is OperationResult.Success -> finish(onSignedIn)
                is OperationResult.Error -> when (result.error) {
                    AuthError.EMAIL_NOT_CONFIRMED -> {
                        auth.resendSignUpCode(current.email.trim())
                        _state.update { it.copy(phase = SignInPhase.CONFIRM, code = "") }
                    }
                    else -> showError(result.error)
                }
            }
        }
    }

    /** The sign-up code of an unconfirmed account. */
    fun onConfirm(onSignedIn: (Boolean) -> Unit) {
        val current = _state.value
        if (!AccountRules.isCode(current.code)) return _state.update { it.copy(codeError = FieldError.CODE_INVALID) }
        busy {
            when (val result = auth.confirmSignUp(current.email.trim(), current.code)) {
                is OperationResult.Success -> finish(onSignedIn)
                is OperationResult.Error ->
                    if (result.error == AuthError.INVALID_CODE) _state.update { it.copy(codeError = FieldError.CODE_INVALID) } else showError(result.error)
            }
        }
    }

    /** "Kód küldése" for a forgotten password. */
    fun onSendResetCode() {
        val current = _state.value
        if (!AccountRules.isEmail(current.email)) return _state.update { it.copy(emailError = FieldError.EMAIL_INVALID) }
        busy {
            when (val result = auth.sendPasswordResetCode(current.email.trim())) {
                is OperationResult.Success -> _state.update { it.copy(phase = SignInPhase.RESET_CODE) }
                is OperationResult.Error -> showError(result.error)
            }
        }
    }

    /** The reset code and the new password; the user is signed in afterwards. */
    fun onResetPassword(onSignedIn: (Boolean) -> Unit) {
        val current = _state.value
        if (!AccountRules.isCode(current.code)) return _state.update { it.copy(codeError = FieldError.CODE_INVALID) }
        if (!AccountRules.isStrongPassword(current.newPassword)) return _state.update { it.copy(passwordError = FieldError.PASSWORD_WEAK) }
        busy {
            when (val result = auth.resetPassword(current.email.trim(), current.code, current.newPassword)) {
                is OperationResult.Success -> finish(onSignedIn)
                is OperationResult.Error ->
                    if (result.error == AuthError.INVALID_CODE) _state.update { it.copy(codeError = FieldError.CODE_INVALID) } else showError(result.error)
            }
        }
    }

    /** Google answered: the account's profile comes to this phone. */
    fun onGoogleSignedIn(onSignedIn: (Boolean) -> Unit) = busy { finish(onSignedIn) }

    fun onGoogleFailed(error: AuthError) {
        viewModelScope.launch { showError(error) }
    }

    private suspend fun finish(onSignedIn: (Boolean) -> Unit) {
        when (val result = accountSetup.completeSignIn()) {
            is OperationResult.Success -> onSignedIn(result.data)
            is OperationResult.Error -> showError(result.error)
        }
    }

    private fun busy(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                block()
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private suspend fun showError(error: AuthError) = snackbarController.showSnackbar(message = error.toUiText())
}
