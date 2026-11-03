package com.madovygabor.maturimate.core.presentation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

actual fun Modifier.systemPadding(): Modifier = this.systemBarsPadding()

actual fun Modifier.systemPaddingExceptBottom(): Modifier =
    this.composed { windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)) }

actual fun Modifier.systemBottomPadding(): Modifier = this.navigationBarsPadding()
