package com.madovygabor.maturimate.features.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.features.auth.domain.AccountRules
import com.madovygabor.maturimate.features.auth.domain.AccountSetup
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.domain.AuthRepository
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import com.madovygabor.maturimate.core.presentation.utils.UiText
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.account_consent_required
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** A field of the account form. */
enum class AccountField { LAST_NAME, FIRST_NAME, EMAIL, PASSWORD }

/** What is wrong with a field; the screen turns it into a sentence. */
enum class FieldError { EMPTY, EMAIL_INVALID, PASSWORD_WEAK, EMAIL_TAKEN, CODE_INVALID }

data class AccountUiState(
    val lastName: String = "",
    val firstName: String = "",
    val email: String = "",
    val password: String = "",
    val consent: Boolean = false,
    /** Shown after a try to go on without the required consent. */
    val consentMissing: Boolean = false,
    val news: Boolean = false,
    /** Shown after the first try to create the account. */
    val errors: Map<AccountField, FieldError> = emptyMap(),
    /** The e-mail address the code was sent to; the code step is shown while it is set. */
    val codeSentTo: String? = null,
    val code: String = "",
    val codeError: FieldError? = null,
    /** Signed in with Google already: only the name and the consents are left. */
    val googleEmail: String? = null,
    val busy: Boolean = false,
)

/**
 * The account step (docs/onboarding/README.md, screen 9): e-mail and password with a 6-digit
 * code, or Google; the required consent; or "Körülnézek" to go on as a guest.
 */
@OptIn(ExperimentalTime::class)
class AccountViewModel(
    private val auth: AuthRepository,
    private val accountSetup: AccountSetup,
    private val profiles: UserProfileRepository,
    private val snackbarController: SnackbarController,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountUiState())
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    init {
        // signed in already (Google on the sign-in screen, or a restart): prefill from the account
        auth.currentUser()?.let { user ->
            _state.update {
                it.copy(googleEmail = user.email, firstName = user.firstName.orEmpty(), lastName = user.lastName.orEmpty())
            }
        }
    }

    fun onChanged(field: AccountField, value: String) {
        _state.update {
            val cleared = it.errors - field
            when (field) {
                AccountField.LAST_NAME -> it.copy(lastName = value, errors = cleared)
                AccountField.FIRST_NAME -> it.copy(firstName = value, errors = cleared)
                AccountField.EMAIL -> it.copy(email = value, errors = cleared)
                AccountField.PASSWORD -> it.copy(password = value, errors = cleared)
            }
        }
    }

    fun onConsentChanged(value: Boolean) = _state.update { it.copy(consent = value, consentMissing = false) }

    /**
     * "Folytatás Google-lel": [start] opens Google right away. The consent is asked after it,
     * together with the name, and nothing is written to the server before both are given.
     */
    fun onGoogle(start: () -> Unit) {
        if (_state.value.busy) return
        start()
    }

    private fun askForConsent() {
        _state.update { it.copy(consentMissing = true) }
        viewModelScope.launch { snackbarController.showSnackbar(message = UiText.StringRes(Res.string.account_consent_required)) }
    }

    fun onNewsChanged(value: Boolean) = _state.update { it.copy(news = value) }

    fun onCodeChanged(value: String) = _state.update { it.copy(code = value.filter { c -> c.isDigit() }.take(AccountRules.CODE_LENGTH), codeError = null) }

    fun onBackToForm() = _state.update { it.copy(codeSentTo = null, code = "", codeError = null) }

    /** "Fiók létrehozása": checks the form, then sends the sign-up code, or finishes a Google account. */
    fun onCreateAccount() {
        val current = _state.value
        val errors = buildMap {
            if (current.lastName.isBlank()) put(AccountField.LAST_NAME, FieldError.EMPTY)
            if (current.firstName.isBlank()) put(AccountField.FIRST_NAME, FieldError.EMPTY)
            if (current.googleEmail == null) {
                if (!AccountRules.isEmail(current.email)) put(AccountField.EMAIL, FieldError.EMAIL_INVALID)
                if (!AccountRules.isStrongPassword(current.password)) put(AccountField.PASSWORD, FieldError.PASSWORD_WEAK)
            }
        }
        _state.update { it.copy(errors = errors) }
        if (current.busy) return
        if (!current.consent) return askForConsent()
        if (errors.isNotEmpty()) return
        if (current.googleEmail != null) return finishSignUp()
        busy {
            when (val result = auth.signUp(current.email.trim(), current.password)) {
                is OperationResult.Success -> _state.update { it.copy(codeSentTo = current.email.trim()) }
                is OperationResult.Error -> when (result.error) {
                    AuthError.EMAIL_TAKEN -> _state.update { it.copy(errors = it.errors + (AccountField.EMAIL to FieldError.EMAIL_TAKEN)) }
                    AuthError.WEAK_PASSWORD -> _state.update { it.copy(errors = it.errors + (AccountField.PASSWORD to FieldError.PASSWORD_WEAK)) }
                    AuthError.INVALID_EMAIL -> _state.update { it.copy(errors = it.errors + (AccountField.EMAIL to FieldError.EMAIL_INVALID)) }
                    else -> showError(result.error)
                }
            }
        }
    }

    /** "Megerősítés": checks the code; the account is then set up and the onboarding finished. */
    fun onConfirmCode() {
        val current = _state.value
        val email = current.codeSentTo ?: return
        if (!AccountRules.isCode(current.code)) return _state.update { it.copy(codeError = FieldError.CODE_INVALID) }
        busy {
            when (val result = auth.confirmSignUp(email, current.code)) {
                is OperationResult.Success -> setUp()
                is OperationResult.Error ->
                    if (result.error == AuthError.INVALID_CODE) _state.update { it.copy(codeError = FieldError.CODE_INVALID) } else showError(result.error)
            }
        }
    }

    fun onResendCode() {
        val email = _state.value.codeSentTo ?: return
        busy { (auth.resendSignUpCode(email) as? OperationResult.Error)?.let { showError(it.error) } }
    }

    /**
     * Google answered. An account that already has a profile on the server is a sign-in: its
     * profile comes to this phone. Otherwise the name from Google is offered for the new account.
     */
    fun onGoogleSignedIn() {
        busy {
            when (val result = accountSetup.completeSignIn()) {
                is OperationResult.Success -> if (!result.data) {
                    val user = auth.currentUser()
                    _state.update {
                        it.copy(
                            googleEmail = user?.email,
                            firstName = it.firstName.ifBlank { user?.firstName.orEmpty() },
                            lastName = it.lastName.ifBlank { user?.lastName.orEmpty() },
                        )
                    }
                    if (_state.value.consent && _state.value.firstName.isNotBlank() && _state.value.lastName.isNotBlank()) setUp()
                }
                is OperationResult.Error -> showError(result.error)
            }
        }
    }

    fun onGoogleFailed(error: AuthError) {
        viewModelScope.launch { showError(error) }
    }

    /** "Körülnézek": the onboarding ends here and the user goes on as a guest on this phone. */
    fun onBrowseAsGuest() {
        busy { profiles.completeOnboarding(Clock.System.now()) }
    }

    private fun finishSignUp() = busy { setUp() }

    private suspend fun setUp() {
        val current = _state.value
        val result = accountSetup.completeSignUp(current.firstName.trim(), current.lastName.trim(), current.news, Clock.System.now())
        if (result is OperationResult.Error) showError(result.error)
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
