package com.madovygabor.maturimate.features.appOnboarding.presentation.profileSetup.error

import com.madovygabor.maturimate.core.presentation.utils.UiText
import com.madovygabor.maturimate.features.appOnboarding.domain.OnBoardingDataValidator
import maturimate.composeapp.generated.resources.Res
import maturimate.composeapp.generated.resources.error_date_future
import maturimate.composeapp.generated.resources.error_date_null
import maturimate.composeapp.generated.resources.error_date_past

/**
 * Maps validation-level [OnBoardingDataValidator.MaturitaDateError] to localized [UiText].
 * Provides specific feedback for date selection issues during onboarding.
 */
fun OnBoardingDataValidator.MaturitaDateError.toUiText(): UiText {
    return when (this) {
        OnBoardingDataValidator.MaturitaDateError.DATE_IN_PAST ->
            UiText.StringRes(Res.string.error_date_past)

        OnBoardingDataValidator.MaturitaDateError.DATE_TOO_FAR_IN_FUTURE ->
            UiText.StringRes(Res.string.error_date_future)

        OnBoardingDataValidator.MaturitaDateError.DATE_IS_NULL ->
            UiText.StringRes(Res.string.error_date_null)
    }
}