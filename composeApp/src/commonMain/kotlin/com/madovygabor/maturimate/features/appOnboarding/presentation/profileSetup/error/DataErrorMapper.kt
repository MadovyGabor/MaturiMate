package com.madovygabor.maturimate.features.appOnboarding.presentation.profileSetup.error

import com.madovygabor.maturimate.core.presentation.utils.UiText
import com.madovygabor.maturimate.features.appOnboarding.domain.DataError
import maturimate.composeapp.generated.resources.Res
import maturimate.composeapp.generated.resources.error_io
import maturimate.composeapp.generated.resources.error_storage_full
import maturimate.composeapp.generated.resources.error_unexpected_save

/**
 * Maps domain-level [DataError] to localized [UiText].
 * This ensures that persistence failures are presented in a user-friendly way.
 */
fun DataError.toUiText(): UiText {
    return when (this) {
        DataError.STORAGE_FULL -> UiText.StringRes(Res.string.error_storage_full)
        DataError.IO_ERROR -> UiText.StringRes(Res.string.error_io)
        DataError.UNKNOWN -> UiText.StringRes(Res.string.error_unexpected_save)
    }
}