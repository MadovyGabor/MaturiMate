package com.madovygabor.maturimate.features.appOnboarding.presentation.profileSetup

import com.madovygabor.maturimate.core.presentation.utils.UiText
import com.madovygabor.maturimate.features.userProfile.domain.TeachingLanguage
import maturimate.composeapp.generated.resources.Res
import maturimate.composeapp.generated.resources.maturitaLanguage_hu
import maturimate.composeapp.generated.resources.maturitaLanguage_sk

fun TeachingLanguage.toUiText(): UiText {
    return when (this) {
        TeachingLanguage.SK -> UiText.StringRes(Res.string.maturitaLanguage_sk)
        TeachingLanguage.HU -> UiText.StringRes(Res.string.maturitaLanguage_hu)
    }
}
