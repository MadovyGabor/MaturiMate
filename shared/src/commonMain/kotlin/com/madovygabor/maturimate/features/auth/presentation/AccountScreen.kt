package com.madovygabor.maturimate.features.auth.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.text.input.KeyboardType
import com.madovygabor.maturimate.core.presentation.locale.LocalAppLocale
import com.madovygabor.maturimate.core.presentation.uiComponents.AppCheckboxRow
import com.madovygabor.maturimate.core.presentation.uiComponents.AppDividerWithText
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.uiComponents.AppButton
import com.madovygabor.maturimate.core.presentation.uiComponents.AppButtonVariant
import com.madovygabor.maturimate.core.presentation.uiComponents.AppTextField
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingProgress
import com.madovygabor.maturimate.features.appOnboarding.presentation.OnboardingStep
import com.madovygabor.maturimate.features.appOnboarding.presentation.components.OnboardingAction
import com.madovygabor.maturimate.features.appOnboarding.presentation.components.OnboardingScaffold
import com.madovygabor.maturimate.features.appOnboarding.presentation.onboardingSteps
import com.madovygabor.maturimate.features.auth.domain.AuthError
import com.madovygabor.maturimate.features.userProfile.domain.UserRole
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.account_browse
import maturimate.shared.generated.resources.account_consent
import maturimate.shared.generated.resources.account_consent_required
import maturimate.shared.generated.resources.account_create
import maturimate.shared.generated.resources.account_email
import maturimate.shared.generated.resources.account_first_name
import maturimate.shared.generated.resources.account_google
import maturimate.shared.generated.resources.account_signed_in_as
import maturimate.shared.generated.resources.account_last_name
import maturimate.shared.generated.resources.account_news
import maturimate.shared.generated.resources.account_or_email
import maturimate.shared.generated.resources.account_password_hint
import maturimate.shared.generated.resources.account_title
import maturimate.shared.generated.resources.account_why
import maturimate.shared.generated.resources.code_confirm
import maturimate.shared.generated.resources.signup_app_code_sent
import maturimate.shared.generated.resources.signup_app_code_title
import maturimate.shared.generated.resources.signup_app_title
import maturimate.shared.generated.resources.signup_app_why
import maturimate.shared.generated.resources.code_resend
import maturimate.shared.generated.resources.code_sent
import maturimate.shared.generated.resources.code_title
import maturimate.shared.generated.resources.signin_password
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

/**
 * The last onboarding step: create the account (e-mail with a 6-digit code, or Google), or
 * "Körülnézek". Creating the account or going on as a guest finishes the onboarding.
 */
@Composable
fun AccountScreen(
    role: UserRole?,
    onBack: () -> Unit,
    /** Opened from the profile by a guest: no steps and no "Körülnézek"; [onDone] runs once the account is set up. */
    inApp: Boolean = false,
    onDone: () -> Unit = {},
    viewModel: AccountViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.done) { if (state.done) onDone() }
    val google = rememberGoogleSignIn(onSignedIn = viewModel::onGoogleSignedIn, onError = viewModel::onGoogleFailed, onClosed = viewModel::onGoogleClosed)
    AccountContent(
        state = state,
        role = role,
        actions = AccountActions(
            onChanged = viewModel::onChanged,
            onConsentChanged = viewModel::onConsentChanged,
            onNewsChanged = viewModel::onNewsChanged,
            onGoogle = { viewModel.onGoogle(google) },
            onCreate = viewModel::onCreateAccount,
            onBrowse = viewModel::onBrowseAsGuest,
            onCodeChanged = viewModel::onCodeChanged,
            onConfirmCode = viewModel::onConfirmCode,
            onResendCode = viewModel::onResendCode,
            onBackToForm = viewModel::onBackToForm,
            onBack = onBack,
        ),
        inApp = inApp,
    )
}

/** What the account screen can do; one object keeps the screen's parameters short. */
data class AccountActions(
    val onChanged: (AccountField, String) -> Unit = { _, _ -> },
    val onConsentChanged: (Boolean) -> Unit = {},
    val onNewsChanged: (Boolean) -> Unit = {},
    val onGoogle: () -> Unit = {},
    val onCreate: () -> Unit = {},
    val onBrowse: () -> Unit = {},
    val onCodeChanged: (String) -> Unit = {},
    val onConfirmCode: () -> Unit = {},
    val onResendCode: () -> Unit = {},
    val onBackToForm: () -> Unit = {},
    val onBack: () -> Unit = {},
)

@Composable
fun AccountContent(state: AccountUiState, role: UserRole?, actions: AccountActions, inApp: Boolean = false) {
    val email = state.codeSentTo
    if (email != null) CodeStep(state, email, role, actions, inApp) else FormStep(state, role, actions, inApp)
}

@Composable
private fun FormStep(state: AccountUiState, role: UserRole?, actions: AccountActions, inApp: Boolean) {
    val create = OnboardingAction(stringResource(Res.string.account_create), actions.onCreate, enabled = !state.busy, loading = state.busy)
    val idle = !state.busy
    OnboardingScaffold(
        // in the app the guest has looked around already: no "Körülnézek", and a plain back arrow instead of the steps
        actions = if (inApp) listOf(create)
        else listOf(create, OnboardingAction(stringResource(Res.string.account_browse), actions.onBrowse, AppButtonVariant.TEXT, enabled = !state.busy)),
        stackedActions = true,
        progress = if (inApp) null else OnboardingProgress(OnboardingStep.ACCOUNT, onboardingSteps(role), actions.onBack),
        onBack = if (inApp) actions.onBack else null,
    ) {
        Text(stringResource(if (inApp) Res.string.signup_app_title else Res.string.account_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(stringResource(if (inApp) Res.string.signup_app_why else Res.string.account_why), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.signedInEmail != null) {
            Text(stringResource(Res.string.account_signed_in_as, state.signedInEmail), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
        } else {
            // Google comes first; the name and the consent follow on this screen
            AppButton(
                text = stringResource(Res.string.account_google),
                onClick = actions.onGoogle,
                modifier = Modifier.fillMaxWidth(),
                variant = AppButtonVariant.OUTLINED,
                enabled = idle,
            )
            AppDividerWithText(stringResource(Res.string.account_or_email))
        }
        // the family name comes first in Hungarian, the first name in Slovak
        val nameFields = listOf(AccountField.LAST_NAME, AccountField.FIRST_NAME).let { if (hungarianOrder()) it else it.reversed() }
        for (field in nameFields) {
            val last = field == AccountField.LAST_NAME
            AppTextField(
                value = if (last) state.lastName else state.firstName,
                onValueChange = { actions.onChanged(field, it) },
                label = stringResource(if (last) Res.string.account_last_name else Res.string.account_first_name),
                error = state.errors[field]?.text(field),
                enabled = idle,
                contentType = if (last) ContentType.PersonLastName else ContentType.PersonFirstName,
            )
        }
        if (state.signedInEmail == null) {
            AppTextField(
                value = state.email,
                onValueChange = { actions.onChanged(AccountField.EMAIL, it) },
                label = stringResource(Res.string.account_email),
                error = state.errors[AccountField.EMAIL]?.text(AccountField.EMAIL),
                keyboardType = KeyboardType.Email,
                enabled = idle,
                contentType = ContentType.EmailAddress,
            )
            AppTextField(
                value = state.password,
                onValueChange = { actions.onChanged(AccountField.PASSWORD, it) },
                label = stringResource(Res.string.signin_password),
                helper = stringResource(Res.string.account_password_hint),
                error = state.errors[AccountField.PASSWORD]?.text(AccountField.PASSWORD),
                password = true,
                enabled = idle,
                contentType = ContentType.NewPassword,
            )
        }
        AppCheckboxRow(
            stringResource(Res.string.account_consent), state.consent, actions.onConsentChanged, enabled = idle,
            error = stringResource(Res.string.account_consent_required).takeIf { state.consentMissing },
        )
        AppCheckboxRow(stringResource(Res.string.account_news), state.news, actions.onNewsChanged, enabled = idle)
    }
}

/** The 6-digit code from the sign-up e-mail. */
@Composable
private fun CodeStep(state: AccountUiState, email: String, role: UserRole?, actions: AccountActions, inApp: Boolean) {
    OnboardingScaffold(
        actions = listOf(
            OnboardingAction(stringResource(Res.string.code_confirm), actions.onConfirmCode, enabled = !state.busy, loading = state.busy),
            OnboardingAction(stringResource(Res.string.code_resend), actions.onResendCode, AppButtonVariant.TEXT, enabled = !state.busy),
        ),
        stackedActions = true,
        progress = if (inApp) null else OnboardingProgress(OnboardingStep.ACCOUNT, onboardingSteps(role), actions.onBackToForm),
        onBack = if (inApp) actions.onBackToForm else null,
    ) {
        CodeStepContent(
            title = stringResource(if (inApp) Res.string.signup_app_code_title else Res.string.code_title),
            sent = stringResource(if (inApp) Res.string.signup_app_code_sent else Res.string.code_sent, email),
            code = state.code,
            error = state.codeError?.text(null),
            enabled = !state.busy,
            onCodeChanged = actions.onCodeChanged,
        )
    }
}

/** True when the app language puts the family name first (Hungarian); Slovak puts the first name first. */
@Composable
internal fun hungarianOrder(): Boolean = LocalAppLocale.current.startsWith("hu", ignoreCase = true)

@Composable
@Preview
private fun AccountContentPreview() {
    AppTheme(darkTheme = false) { AccountContent(AccountUiState(consent = true), UserRole.STUDENT, AccountActions()) }
}
