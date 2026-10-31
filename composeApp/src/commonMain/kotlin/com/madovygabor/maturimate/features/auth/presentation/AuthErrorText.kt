package com.madovygabor.maturimate.features.auth.presentation

import com.madovygabor.maturimate.core.presentation.utils.UiText
import com.madovygabor.maturimate.features.auth.domain.AuthError
import maturimate.composeapp.generated.resources.Res
import maturimate.composeapp.generated.resources.error_code_invalid
import maturimate.composeapp.generated.resources.error_email_invalid
import maturimate.composeapp.generated.resources.error_email_not_confirmed
import maturimate.composeapp.generated.resources.error_email_taken
import maturimate.composeapp.generated.resources.error_network
import maturimate.composeapp.generated.resources.error_password_weak
import maturimate.composeapp.generated.resources.error_same_password
import maturimate.composeapp.generated.resources.error_signin
import maturimate.composeapp.generated.resources.error_too_many
import maturimate.composeapp.generated.resources.error_unknown

/** The sentence for an [AuthError]: what is wrong and what to do. */
fun AuthError.toUiText(): UiText = UiText.StringRes(
    when (this) {
        AuthError.INVALID_CREDENTIALS -> Res.string.error_signin
        AuthError.EMAIL_NOT_CONFIRMED -> Res.string.error_email_not_confirmed
        AuthError.EMAIL_TAKEN -> Res.string.error_email_taken
        AuthError.WEAK_PASSWORD -> Res.string.error_password_weak
        AuthError.SAME_PASSWORD -> Res.string.error_same_password
        AuthError.INVALID_EMAIL -> Res.string.error_email_invalid
        AuthError.INVALID_CODE -> Res.string.error_code_invalid
        AuthError.TOO_MANY_REQUESTS -> Res.string.error_too_many
        AuthError.NETWORK -> Res.string.error_network
        AuthError.UNKNOWN -> Res.string.error_unknown
    }
)
