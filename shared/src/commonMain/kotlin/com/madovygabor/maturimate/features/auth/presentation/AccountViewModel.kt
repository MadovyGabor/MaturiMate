package com.madovygabor.maturimate.features.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.madovygabor.maturimate.core.domain.error.OperationResult
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarController
import com.madovygabor.maturimate.core.presentation.utils.snackbar.SnackbarKind
import com.madovygabor.maturimate.features.auth.domain.AccountRules
import com.madovygabor.maturimate.features.auth.domain.AccountSetup
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.auth.domain.AuthRepository
import com.madovygabor.maturimate.features.userProfile.domain.UserProfileRepository
import com.madovygabor.maturimate.core.presentation.utils.UiText
import com.madovygabor.maturimate.features.appOnboarding.presentation.profileSetup.error.toUiText
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.account_consent_required
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Instant
import kotlin.time.ExperimentalTime

/** A field of the account form. */
enum class AccountField { LAST_NAME, FIRST_NAME, EMAIL, PASSWORD, PASSWORD_REPEAT }

/** What is wrong with a field; the screen turns it into a sentence. */
enum class FieldError { EMPTY, EMAIL_INVALID, PASSWORD_WEAK, EMAIL_TAKEN, CODE_INVALID, PASSWORDS_DIFFER }

data class AccountUiState(
    val lastName: String = "",
    val firstName: String = "",
    val email: String = "",
    val password: String = "",
    /** The password typed a second time: a typo in a hidden field is caught before the account is made. */
    val passwordRepeat: String = "",
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
    /** The address of the account signed in already (Google, or a restored session of any provider): only the name and the consents are left. */
    val signedInEmail: String? = null,
    val busy: Boolean = false,
    /** The account is set up (or an existing one signed in); a screen opened from the profile then goes back. */
    val done: Boolean = false,
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
    /** The clock: the day the onboarding ends. */
    private val now: () -> Instant,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountUiState())
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    /**
     * The id of the account that was checked here and found not set up: only that account's form may sign it up. Keyed to
     * the account, so another session (signed out and in again meanwhile) is checked again; cleared by a failed check.
     */
    private var checkedAccountId: String? = null

    /** Google (the browser on desktop) is open: the screen is busy until exactly one of its callbacks comes. */
    private var googleOpen = false

    init {
        // Signed in already: a session restored on this device, of any provider (Google, or an e-mail account whose
        // sign-up or sign-in was left halfway). Its account may be set up already, so it is signed in first; the
        // form is only for an account that is not. The address is shown at once, the busy state covers the check.
        auth.currentUser()?.let { user ->
            _state.update {
                it.copy(signedInEmail = user.email, firstName = user.firstName.orEmpty(), lastName = user.lastName.orEmpty())
            }
            busy { if (signInSession() && readyToSetUp()) setUp() }
        }
    }

    fun onChanged(field: AccountField, value: String) {
        _state.update {
            val cleared = it.errors - field
            when (field) {
                AccountField.LAST_NAME -> it.copy(lastName = value, errors = cleared)
                AccountField.FIRST_NAME -> it.copy(firstName = value, errors = cleared)
                AccountField.EMAIL -> it.copy(email = value, errors = cleared)
                AccountField.PASSWORD -> it.copy(password = value, errors = cleared - AccountField.PASSWORD_REPEAT)
                AccountField.PASSWORD_REPEAT -> it.copy(passwordRepeat = value, errors = cleared)
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
        // waiting for Google: the other buttons wait too, until onGoogleSignedIn, onGoogleFailed or onGoogleClosed
        googleOpen = true
        _state.update { it.copy(busy = true) }
        start()
    }

    /** Google's wait is over; its answer then runs even though the screen was busy waiting for it (never dropped). */
    private fun endGoogle() {
        if (!googleOpen) return
        googleOpen = false
        _state.update { it.copy(busy = false) }
    }

    private fun askForConsent() {
        _state.update { it.copy(consentMissing = true) }
        viewModelScope.launch { snackbarController.showSnackbar(message = UiText.StringRes(Res.string.account_consent_required), kind = SnackbarKind.ERROR) }
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
            if (current.signedInEmail == null) {
                if (!AccountRules.isEmail(current.email)) put(AccountField.EMAIL, FieldError.EMAIL_INVALID)
                if (!AccountRules.isStrongPassword(current.password)) put(AccountField.PASSWORD, FieldError.PASSWORD_WEAK)
                // only once the password itself is fine: a weak one is the first thing to fix
                else if (current.password != current.passwordRepeat) put(AccountField.PASSWORD_REPEAT, FieldError.PASSWORDS_DIFFER)
            }
        }
        _state.update { it.copy(errors = errors) }
        if (current.busy) return
        if (!current.consent) return askForConsent()
        if (errors.isNotEmpty()) return
        if (current.signedInEmail != null) return finishSignUp()
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
        endGoogle()
        busy { if (signInSession() && readyToSetUp()) setUp() }
    }

    /**
     * Signs in the session's account. A set-up account takes this device over (its profile comes here) and is done.
     * True when the account is not set up yet and the form goes on, prefilled with what the account gave (the device
     * stays the guest's until the sign-up); false when it was set up (done) or the check failed (the error is shown,
     * and the form checks again before any sign-up).
     */
    private suspend fun signInSession(): Boolean =
        when (val result = accountSetup.completeSignIn()) {
            // set up = the onboarding end AND the TERMS consent: a finished profile without TERMS keeps the form, whose
            // sign-up stores the consents
            is OperationResult.Success -> if (result.data.isSetUp) {
                // an existing account: signed in, nothing more to fill in
                _state.update { it.copy(done = true) }
                false
            } else {
                val user = auth.currentUser()
                checkedAccountId = user?.id
                _state.update {
                    it.copy(
                        signedInEmail = user?.email,
                        firstName = it.firstName.ifBlank { user?.firstName.orEmpty() },
                        lastName = it.lastName.ifBlank { user?.lastName.orEmpty() },
                    )
                }
                true
            }
            is OperationResult.Error -> {
                checkedAccountId = null
                showError(result.error)
                false
            }
        }

    private fun readyToSetUp() = _state.value.let { it.consent && it.firstName.isNotBlank() && it.lastName.isNotBlank() }

    /** The account picker was closed (Android): nothing happened. */
    fun onGoogleClosed() = endGoogle()

    fun onGoogleFailed(error: AuthError) {
        endGoogle()
        viewModelScope.launch { showError(error) }
    }

    /**
     * "Körülnézek": the onboarding ends here and the user goes on as a guest on this phone. A session open here belongs
     * to an account that is not set up (the phone is still the guest's): it ends first (this device only), so a guest
     * phone never keeps someone's session.
     */
    fun onBrowseAsGuest() {
        busy {
            if (auth.currentUser() != null) {
                // without a network the session is removed from this device only; it runs out on the server by itself
                if (auth.signOut() is OperationResult.Error) auth.clearLocalSession()
                checkedAccountId = null
                _state.update { it.copy(signedInEmail = null) }
            }
            val result = profiles.completeOnboarding(now())
            if (result is OperationResult.Error) snackbarController.showSnackbar(message = result.error.toUiText(), kind = SnackbarKind.ERROR)
        }
    }

    /** The form of a signed-in account: never a sign-up before its account was signed in here and found not set up. */
    private fun finishSignUp() = busy {
        val checked = checkedAccountId != null && checkedAccountId == auth.currentUser()?.id
        if (checked || signInSession()) setUp()
    }

    private suspend fun setUp() {
        val current = _state.value
        val result = accountSetup.completeSignUp(current.firstName.trim(), current.lastName.trim(), current.news, now())
        if (result is OperationResult.Error) showError(result.error) else _state.update { it.copy(done = true) }
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

    private suspend fun showError(error: AuthError) = snackbarController.showSnackbar(message = error.toUiText(), kind = SnackbarKind.ERROR)
}
