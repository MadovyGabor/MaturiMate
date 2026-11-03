package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.madovygabor.maturimate.core.presentation.theme.extendedColors
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.utils.richText

/** A line of the feedback: an optional bold [label] ("Jó válasz:") and a [text] of the content. */
data class FeedbackLine(val label: String?, val text: String)

/**
 * The sheet at the bottom of a task after the answer is checked: green and "Helyes!" for a right answer, red and
 * "Nem egészen." for a wrong one, with the right answer, the explanation and the button to go on
 * (docs/design-system/components/FeedbackSheet.md).
 */
@Composable
fun AppFeedbackSheet(
    correct: Boolean,
    title: String,
    lines: List<FeedbackLine>,
    buttonText: String,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val extended = MaterialTheme.extendedColors
    val spacing = MaterialTheme.spacing
    val container = if (correct) extended.successContainer else colors.errorContainer
    val content = if (correct) extended.onSuccessContainer else colors.onErrorContainer
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = CornerSize(0f), bottomEnd = CornerSize(0f)),
        color = container,
        contentColor = content,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.space4, vertical = spacing.space4),
            verticalArrangement = Arrangement.spacedBy(spacing.space3),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.space2)) {
                Icon(if (correct) Icons.Rounded.Check else Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(MaterialTheme.layout.icon))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            lines.forEach { line ->
                Text(
                    buildAnnotatedString {
                        if (line.label != null) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(line.label + " ") }
                        append(richText(line.text))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth().heightIn(min = MaterialTheme.layout.touchTarget),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (correct) extended.success else colors.error,
                    contentColor = if (correct) extended.onSuccess else colors.onError,
                ),
            ) { Text(buttonText, style = MaterialTheme.typography.labelLarge) }
        }
    }
}
