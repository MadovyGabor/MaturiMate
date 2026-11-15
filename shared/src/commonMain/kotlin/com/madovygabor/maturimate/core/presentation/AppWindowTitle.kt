package com.madovygabor.maturimate.core.presentation

import androidx.compose.runtime.Composable
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.app_name
import org.jetbrains.compose.resources.stringResource

/** The name of the app for the title of the desktop window (the `app_name` resource; `Res` is internal to this module). */
@Composable
fun appWindowTitle(): String = stringResource(Res.string.app_name)
