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
import com.madovygabor.maturimate.features.appOnboarding.presentation.components.OnboardingAction
import com.madovygabor.maturimate.features.appOnboarding.presentation.components.OnboardingScaffold
import maturimate.composeapp.generated.resources.Res
import maturimate.composeapp.generated.resources.account_email
import maturimate.composeapp.generated.resources.account_google
import maturimate.composeapp.generated.resources.account_or_email
import maturimate.composeapp.generated.resources.account_password_hint
import maturimate.composeapp.generated.resources.code_confirm
import maturimate.composeapp.generated.resources.code_label
import maturimate.composeapp.generated.resources.code_sent
import maturimate.composeapp.generated.resources.code_title
import maturimate.composeapp.generated.resources.reset_new_password
import maturimate.composeapp.generated.resources.reset_save
import maturimate.composeapp.generated.resources.reset_send
import maturimate.composeapp.generated.resources.reset_title
import maturimate.composeapp.generated.resources.reset_why
import maturimate.composeapp.generated.resources.signin_button
import maturimate.composeapp.generated.resources.signin_forgot
import maturimate.composeapp.generated.resources.signin_password
import maturimate.composeapp.generated.resources.signin_title
import maturimate.composeapp.generated.resources.signin_why
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

/**
 * "Már van fiókom": sign in with e-mail and password or Google. [onSignedIn] gets whether the
 * account finished the onboarding before.
 */
@Composable
fun SignInScreen(
    onBack: () -> Unit,
    onSignedIn: (Boolean) -> Unit,
    viewModel: SignInViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val google = rememberGoogleSignIn(onSignedIn = { viewModel.onGoogleSignedIn(onSignedIn) }, onError = viewModel::onGoogleFailed)
    SignInContent(
        state = state,
        actions = SignInActions(
            onEmailChanged = viewModel::onEmailChanged,
            onPasswordChanged = viewModel::onPasswordChanged,
            onNewPasswordChanged = viewModel::onNewPasswordChanged,
            onCodeChanged = viewModel::onCodeChanged,
            onSignIn = { viewModel.onSignIn(onSignedIn) },
            onGoogle = google,
            onForgot = viewModel::onForgotPassword,
            onConfirm = { viewModel.onConfirm(onSignedIn) },
            onSendResetCode = viewModel::onSendResetCode,
            onResetPassword = { viewModel.onResetPassword(onSignedIn) },
            onBack = { if (!viewModel.onBack()) onBack() },
        ),
    )
}

/** What the sign-in screen can do. */
data class SignInActions(
    val onEmailChanged: (String) -> Unit = {},
    val onPasswordChanged: (String) -> Unit = {},
    val onNewPasswordChanged: (String) -> Unit = {},
    val onCodeChanged: (String) -> Unit = {},
    val onSignIn: () -> Unit = {},
    val onGoogle: () -> Unit = {},
    val onForgot: () -> Unit = {},
    val onConfirm: () -> Unit = {},
    val onSendResetCode: () -> Unit = {},
    val onResetPassword: () -> Unit = {},
    val onBack: () -> Unit = {},
)

@Composable
fun SignInContent(state: SignInUiState, actions: SignInActions) {
    val idle = !state.busy
    val buttons = when (state.phase) {
        SignInPhase.FORM -> listOf(
            OnboardingAction(stringResource(Res.string.signin_button), actions.onSignIn, enabled = idle && state.password.isNotEmpty()),
            OnboardingAction(stringResource(Res.string.signin_forgot), actions.onForgot, AppButtonVariant.TEXT, enabled = idle),
        )
        SignInPhase.CONFIRM -> listOf(OnboardingAction(stringResource(Res.string.code_confirm), actions.onConfirm, enabled = idle))
        SignInPhase.RESET_REQUEST -> listOf(OnboardingAction(stringResource(Res.string.reset_send), actions.onSendResetCode, enabled = idle))
        SignInPhase.RESET_CODE -> listOf(OnboardingAction(stringResource(Res.string.reset_save), actions.onResetPassword, enabled = idle))
    }
    OnboardingScaffold(actions = buttons, stackedActions = true, onBack = actions.onBack) {
        val title = when (state.phase) {
            SignInPhase.FORM -> Res.string.signin_title
            SignInPhase.CONFIRM -> Res.string.code_title
            SignInPhase.RESET_REQUEST, SignInPhase.RESET_CODE -> Res.string.reset_title
        }
        Text(stringResource(title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        when (state.phase) {
            SignInPhase.FORM -> {
                Text(stringResource(Res.string.signin_why), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AppButton(
                    text = stringResource(Res.string.account_google),
                    onClick = actions.onGoogle,
                    modifier = Modifier.fillMaxWidth(),
                    variant = AppButtonVariant.OUTLINED,
                    enabled = idle,
                )
                DividerWithText(stringResource(Res.string.account_or_email))
                EmailField(state, actions)
                AppTextField(
                    value = state.password,
                    onValueChange = actions.onPasswordChanged,
                    label = stringResource(Res.string.signin_password),
                    password = true,
                )
            }
            SignInPhase.CONFIRM -> {
                Text(stringResource(Res.string.code_sent, state.email.trim()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CodeField(state, actions)
            }
            SignInPhase.RESET_REQUEST -> {
                Text(stringResource(Res.string.reset_why), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                EmailField(state, actions)
            }
            SignInPhase.RESET_CODE -> {
                Text(stringResource(Res.string.code_sent, state.email.trim()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CodeField(state, actions)
                AppTextField(
                    value = state.newPassword,
                    onValueChange = actions.onNewPasswordChanged,
                    label = stringResource(Res.string.reset_new_password),
                    helper = stringResource(Res.string.account_password_hint),
                    error = state.passwordError?.text(AccountField.PASSWORD),
                    password = true,
                )
            }
        }
    }
}

@Composable
private fun EmailField(state: SignInUiState, actions: SignInActions) {
    AppTextField(
        value = state.email,
        onValueChange = actions.onEmailChanged,
        label = stringResource(Res.string.account_email),
        error = state.emailError?.text(AccountField.EMAIL),
        keyboardType = KeyboardType.Email,
    )
}

@Composable
private fun CodeField(state: SignInUiState, actions: SignInActions) {
    AppTextField(
        value = state.code,
        onValueChange = actions.onCodeChanged,
        label = stringResource(Res.string.code_label),
        error = state.codeError?.text(null),
        keyboardType = KeyboardType.NumberPassword,
    )
}

@Composable
@Preview
private fun SignInContentPreview() {
    AppTheme(darkTheme = false) { SignInContent(SignInUiState(email = "anna@example.com"), SignInActions()) }
}
