package com.madovygabor.maturimate.features.appOnboarding.presentation.profileSetup

import com.madovygabor.maturimate.core.presentation.utils.UiText
import com.madovygabor.maturimate.features.appOnboarding.domain.MaturitaLanguage
import com.madovygabor.maturimate.features.appOnboarding.domain.MaturitaLevel
import maturimate.composeapp.generated.resources.Res
import maturimate.composeapp.generated.resources.maturitaLanguage_hu
import maturimate.composeapp.generated.resources.maturitaLanguage_sk
import maturimate.composeapp.generated.resources.maturitaLevel_b1
import maturimate.composeapp.generated.resources.maturitaLevel_b2

fun MaturitaLanguage.toUiText(): UiText {
    return when (this) {
        MaturitaLanguage.SK -> UiText.StringRes(Res.string.maturitaLanguage_sk)
        MaturitaLanguage.HU -> UiText.StringRes(Res.string.maturitaLanguage_hu)
    }
}

fun MaturitaLevel.toUiText(): UiText {
    return when (this) {
        MaturitaLevel.B1 -> UiText.StringRes(Res.string.maturitaLevel_b1)
        MaturitaLevel.B2-> UiText.StringRes(Res.string.maturitaLevel_b2)
    }
}