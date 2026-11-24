package com.madovygabor.maturimate.features.auth.presentation

import com.madovygabor.maturimate.features.auth.domain.SignedInAccount
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.autofill.ContentType
import com.madovygabor.maturimate.core.presentation.uiComponents.AppDividerWithText
import com.madovygabor.maturimate.features.profileDetails.presentation.ConfirmDialog
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.uiComponents.AppButton
import com.madovygabor.maturimate.core.presentation.uiComponents.AppButtonVariant
import com.madovygabor.maturimate.core.presentation.uiComponents.AppTextField
import com.madovygabor.maturimate.features.appOnboarding.presentation.components.OnboardingAction
import com.madovygabor.maturimate.features.appOnboarding.presentation.components.OnboardingScaffold
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.account_email
import maturimate.shared.generated.resources.account_google
import maturimate.shared.generated.resources.account_or_email
import maturimate.shared.generated.resources.account_password_hint
import maturimate.shared.generated.resources.code_confirm
import maturimate.shared.generated.resources.signin_replace_confirm
import maturimate.shared.generated.resources.signin_replace_text
import maturimate.shared.generated.resources.signin_replace_title
import maturimate.shared.generated.resources.code_sent
import maturimate.shared.generated.resources.code_title
import maturimate.shared.generated.resources.reset_new_password
import maturimate.shared.generated.resources.reset_save
import maturimate.shared.generated.resources.reset_send
import maturimate.shared.generated.resources.reset_title
import maturimate.shared.generated.resources.reset_why
import maturimate.shared.generated.resources.signin_button
import maturimate.shared.generated.resources.signin_forgot
import maturimate.shared.generated.resources.signin_password
import maturimate.shared.generated.resources.signin_title
import maturimate.shared.generated.resources.signin_why
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

/**
 * "Már van fiókom": sign in with e-mail and password or Google. [onSignedIn] gets whether the
 * account finished the onboarding before. [confirmReplace]: the sign-in comes at the end of the onboarding, where the
 * answers given on this phone are replaced by those of the account, so it is asked first (when there are answers).
 */
@Composable
fun SignInScreen(
    onBack: () -> Unit,
    onSignedIn: (SignedInAccount) -> Unit,
    confirmReplace: Boolean = false,
    viewModel: SignInViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val google = rememberGoogleSignIn(onSignedIn = { viewModel.onGoogleSignedIn(onSignedIn) }, onError = viewModel::onGoogleFailed, onClosed = viewModel::onGoogleClosed)
    SignInContent(
        state = state,
        actions = SignInActions(
            onEmailChanged = viewModel::onEmailChanged,
            onPasswordChanged = viewModel::onPasswordChanged,
            onNewPasswordChanged = viewModel::onNewPasswordChanged,
            onCodeChanged = viewModel::onCodeChanged,
            onSignIn = { viewModel.onSignIn(onSignedIn) },
            onGoogle = { viewModel.onGoogle(google) },
            onForgot = viewModel::onForgotPassword,
            onConfirm = { viewModel.onConfirm(onSignedIn) },
            onSendResetCode = viewModel::onSendResetCode,
            onResetPassword = { viewModel.onResetPassword(onSignedIn) },
            onBack = { if (!viewModel.onBack()) onBack() },
        ),
        confirmReplace = confirmReplace,
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
fun SignInContent(state: SignInUiState, actions: SignInActions, confirmReplace: Boolean = false) {
    val idle = !state.busy
    // a sign-in waits here for the answer of the dialog "your answers here are replaced"
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val warn = confirmReplace && state.hasLocalAnswers
    fun guarded(action: () -> Unit): () -> Unit = if (warn) ({ pending = action }) else action
    val buttons = when (state.phase) {
        SignInPhase.FORM -> listOf(
            OnboardingAction(stringResource(Res.string.signin_button), guarded(actions.onSignIn), enabled = idle && state.password.isNotEmpty(), loading = state.busy),
            OnboardingAction(stringResource(Res.string.signin_forgot), actions.onForgot, AppButtonVariant.TEXT, enabled = idle),
        )
        SignInPhase.CONFIRM -> listOf(OnboardingAction(stringResource(Res.string.code_confirm), guarded(actions.onConfirm), enabled = idle, loading = state.busy))
        SignInPhase.RESET_REQUEST -> listOf(OnboardingAction(stringResource(Res.string.reset_send), actions.onSendResetCode, enabled = idle, loading = state.busy))
        SignInPhase.RESET_CODE -> listOf(OnboardingAction(stringResource(Res.string.reset_save), guarded(actions.onResetPassword), enabled = idle, loading = state.busy))
    }
    pending?.let { action ->
        ConfirmDialog(
            title = stringResource(Res.string.signin_replace_title),
            text = stringResource(Res.string.signin_replace_text),
            confirm = stringResource(Res.string.signin_replace_confirm),
            busy = false,
            onConfirm = { pending = null; action() },
            onDismiss = { pending = null },
        )
    }
    OnboardingScaffold(actions = buttons, stackedActions = true, onBack = actions.onBack) {
        val title = when (state.phase) {
            SignInPhase.FORM -> Res.string.signin_title
            SignInPhase.CONFIRM -> Res.string.code_title
            SignInPhase.RESET_REQUEST, SignInPhase.RESET_CODE -> Res.string.reset_title
        }
        when (state.phase) {
            SignInPhase.FORM -> {
                Text(stringResource(title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                Text(stringResource(Res.string.signin_why), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AppButton(
                    text = stringResource(Res.string.account_google),
                    onClick = guarded(actions.onGoogle),
                    modifier = Modifier.fillMaxWidth(),
                    variant = AppButtonVariant.OUTLINED,
                    enabled = idle,
                )
                AppDividerWithText(stringResource(Res.string.account_or_email))
                EmailField(state, actions)
                AppTextField(
                    value = state.password,
                    onValueChange = actions.onPasswordChanged,
                    label = stringResource(Res.string.signin_password),
                    password = true,
                    enabled = idle,
                    contentType = ContentType.Password,
                )
            }
            SignInPhase.CONFIRM -> CodeStepContent(
                title = stringResource(title),
                sent = stringResource(Res.string.code_sent, state.email.trim()),
                code = state.code,
                error = state.codeError?.text(null),
                enabled = idle,
                onCodeChanged = actions.onCodeChanged,
            )
            SignInPhase.RESET_REQUEST -> {
                Text(stringResource(title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                Text(stringResource(Res.string.reset_why), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                EmailField(state, actions)
            }
            SignInPhase.RESET_CODE -> {
                CodeStepContent(
                    title = stringResource(title),
                    sent = stringResource(Res.string.code_sent, state.email.trim()),
                    code = state.code,
                    error = state.codeError?.text(null),
                    enabled = idle,
                    onCodeChanged = actions.onCodeChanged,
                )
                AppTextField(
                    value = state.newPassword,
                    onValueChange = actions.onNewPasswordChanged,
                    label = stringResource(Res.string.reset_new_password),
                    helper = stringResource(Res.string.account_password_hint),
                    error = state.passwordError?.text(AccountField.PASSWORD),
                    password = true,
                    enabled = idle,
                    contentType = ContentType.NewPassword,
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
        enabled = !state.busy,
        contentType = ContentType.EmailAddress,
    )
}

@Composable
@Preview
private fun SignInContentPreview() {
    AppTheme(darkTheme = false) { SignInContent(SignInUiState(email = "anna@example.com"), SignInActions()) }
}
