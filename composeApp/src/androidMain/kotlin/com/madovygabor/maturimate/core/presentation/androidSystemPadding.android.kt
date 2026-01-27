package com.madovygabor.maturimate.core.presentation

import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier

actual fun Modifier.systemPadding(): Modifier = this.systemBarsPadding()