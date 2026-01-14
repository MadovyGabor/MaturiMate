package com.madovygabor.maturimate.ui.screens


import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier

actual fun Modifier.androidSystemPadding(): Modifier = this.systemBarsPadding()