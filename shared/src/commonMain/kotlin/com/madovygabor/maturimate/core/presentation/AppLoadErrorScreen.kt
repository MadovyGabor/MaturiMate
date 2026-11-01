package com.madovygabor.maturimate.core.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.madovygabor.maturimate.core.presentation.theme.AppTheme
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.uiComponents.AppButton
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.app_load_error_text
import maturimate.shared.generated.resources.app_load_error_title
import maturimate.shared.generated.resources.retry
import org.jetbrains.compose.resources.stringResource

/**
 * Shown instead of any screen when the saved profile cannot be read at the start, even after
 * the retries; the app does not know yet whether to show the onboarding or the home screen.
 */
@Composable
fun AppLoadErrorScreen(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(MaterialTheme.spacing.space6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space3, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(Res.string.app_load_error_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = MaterialTheme.layout.contentMaxWidth),
        )
        Text(
            text = stringResource(Res.string.app_load_error_text),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = MaterialTheme.layout.contentMaxWidth),
        )
        AppButton(
            text = stringResource(Res.string.retry),
            onClick = onRetry,
            modifier = Modifier.widthIn(min = MaterialTheme.layout.buttonMinWidth),
        )
    }
}

@Composable
@Preview
private fun AppLoadErrorScreenPreview() {
    AppTheme { AppLoadErrorScreen(onRetry = {}) }
}
