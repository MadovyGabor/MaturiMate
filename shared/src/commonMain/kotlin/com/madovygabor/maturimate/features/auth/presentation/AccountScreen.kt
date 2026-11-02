package com.madovygabor.maturimate.features.auth.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
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
import maturimate.shared.generated.resources.account_google_signed
import maturimate.shared.generated.resources.account_last_name
import maturimate.shared.generated.resources.account_news
import maturimate.shared.generated.resources.account_or_email
import maturimate.shared.generated.resources.account_password_hint
import maturimate.shared.generated.resources.account_title
import maturimate.shared.generated.resources.account_why
import maturimate.shared.generated.resources.code_confirm
import maturimate.shared.generated.resources.code_label
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
    viewModel: AccountViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val google = rememberGoogleSignIn(onSignedIn = viewModel::onGoogleSignedIn, onError = viewModel::onGoogleFailed)
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
fun AccountContent(state: AccountUiState, role: UserRole?, actions: AccountActions) {
    val email = state.codeSentTo
    if (email != null) CodeStep(state, email, role, actions) else FormStep(state, role, actions)
}

@Composable
private fun FormStep(state: AccountUiState, role: UserRole?, actions: AccountActions) {
    OnboardingScaffold(
        actions = listOf(
            OnboardingAction(stringResource(Res.string.account_create), actions.onCreate, enabled = !state.busy),
            OnboardingAction(stringResource(Res.string.account_browse), actions.onBrowse, AppButtonVariant.TEXT, enabled = !state.busy),
        ),
        stackedActions = true,
        progress = OnboardingProgress(OnboardingStep.ACCOUNT, onboardingSteps(role), actions.onBack),
    ) {
        Text(stringResource(Res.string.account_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(stringResource(Res.string.account_why), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.googleEmail != null) {
            Text(stringResource(Res.string.account_google_signed, state.googleEmail), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
        } else {
            // Google comes first; the name and the consent follow on this screen
            AppButton(
                text = stringResource(Res.string.account_google),
                onClick = actions.onGoogle,
                modifier = Modifier.fillMaxWidth(),
                variant = AppButtonVariant.OUTLINED,
                enabled = !state.busy,
            )
            DividerWithText(stringResource(Res.string.account_or_email))
        }
        AppTextField(
            value = state.lastName,
            onValueChange = { actions.onChanged(AccountField.LAST_NAME, it) },
            label = stringResource(Res.string.account_last_name),
            error = state.errors[AccountField.LAST_NAME]?.text(AccountField.LAST_NAME),
        )
        AppTextField(
            value = state.firstName,
            onValueChange = { actions.onChanged(AccountField.FIRST_NAME, it) },
            label = stringResource(Res.string.account_first_name),
            error = state.errors[AccountField.FIRST_NAME]?.text(AccountField.FIRST_NAME),
        )
        if (state.googleEmail == null) {
            AppTextField(
                value = state.email,
                onValueChange = { actions.onChanged(AccountField.EMAIL, it) },
                label = stringResource(Res.string.account_email),
                error = state.errors[AccountField.EMAIL]?.text(AccountField.EMAIL),
                keyboardType = KeyboardType.Email,
            )
            AppTextField(
                value = state.password,
                onValueChange = { actions.onChanged(AccountField.PASSWORD, it) },
                label = stringResource(Res.string.signin_password),
                helper = stringResource(Res.string.account_password_hint),
                error = state.errors[AccountField.PASSWORD]?.text(AccountField.PASSWORD),
                password = true,
            )
        }
        CheckRow(
            stringResource(Res.string.account_consent), state.consent, actions.onConsentChanged,
            error = stringResource(Res.string.account_consent_required).takeIf { state.consentMissing },
        )
        CheckRow(stringResource(Res.string.account_news), state.news, actions.onNewsChanged)
    }
}

/** The 6-digit code from the sign-up e-mail. */
@Composable
private fun CodeStep(state: AccountUiState, email: String, role: UserRole?, actions: AccountActions) {
    OnboardingScaffold(
        actions = listOf(
            OnboardingAction(stringResource(Res.string.code_confirm), actions.onConfirmCode, enabled = !state.busy),
            OnboardingAction(stringResource(Res.string.code_resend), actions.onResendCode, AppButtonVariant.TEXT, enabled = !state.busy),
        ),
        stackedActions = true,
        progress = OnboardingProgress(OnboardingStep.ACCOUNT, onboardingSteps(role), actions.onBackToForm),
    ) {
        Text(stringResource(Res.string.code_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(stringResource(Res.string.code_sent, email), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AppTextField(
            value = state.code,
            onValueChange = actions.onCodeChanged,
            label = stringResource(Res.string.code_label),
            error = state.codeError?.text(null),
            keyboardType = KeyboardType.NumberPassword,
        )
    }
}

@Composable
@Preview
private fun AccountContentPreview() {
    AppTheme(darkTheme = false) { AccountContent(AccountUiState(consent = true), UserRole.STUDENT, AccountActions()) }
}
