package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import com.madovygabor.maturimate.core.presentation.theme.extendedColors
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.utils.AppRichText

/**
 * How an answer looks. After checking, the answer the student gave is [CORRECT] or [WRONG], the right one is
 * [CORRECT] too, and the rest is [DIMMED].
 */
enum class AnswerState { DEFAULT, SELECTED, CORRECT, WRONG, DIMMED }

/** The colors of an answer in [state], shared by the answer components. */
internal data class AnswerColors(val container: Color, val content: Color, val border: BorderStroke, val badge: Color, val onBadge: Color)

@Composable
internal fun answerColors(state: AnswerState): AnswerColors {
    val colors = MaterialTheme.colorScheme
    val extended = MaterialTheme.extendedColors
    val layout = MaterialTheme.layout
    return when (state) {
        AnswerState.DEFAULT, AnswerState.DIMMED ->
            AnswerColors(colors.surfaceContainerLowest, colors.onBackground, BorderStroke(layout.borderThin, colors.outline), colors.surfaceContainerHigh, colors.onSurfaceVariant)
        AnswerState.SELECTED ->
            AnswerColors(colors.primaryContainer, colors.onPrimaryContainer, BorderStroke(layout.borderThick, colors.primary), colors.primary, colors.onPrimary)
        AnswerState.CORRECT ->
            AnswerColors(extended.successContainer, extended.onSuccessContainer, BorderStroke(layout.borderThick, extended.success), extended.success, extended.onSuccess)
        AnswerState.WRONG ->
            AnswerColors(colors.errorContainer, colors.onErrorContainer, BorderStroke(layout.borderThick, colors.error), colors.error, colors.onError)
    }
}

/**
 * One option of a multiple-choice task, with a letter (docs/design-system/components/AnswerOption.md).
 * A right or wrong option also shows a check or an X, so the state is not shown by color alone.
 *
 * @param letter The label of the option, for example "A".
 * @param text The option text (the text format of the content).
 */
@Composable
fun AppAnswerOption(
    letter: String,
    text: String,
    state: AnswerState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val spacing = MaterialTheme.spacing
    val layout = MaterialTheme.layout
    val look = answerColors(state)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (state == AnswerState.DIMMED) 0.55f else 1f)
            .heightIn(min = layout.answerOptionMinHeight)
            .selectable(selected = state == AnswerState.SELECTED, enabled = enabled, onClick = onClick, role = Role.RadioButton),
        shape = MaterialTheme.shapes.medium,
        color = look.container,
        contentColor = look.content,
        border = look.border,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = spacing.space3, vertical = spacing.space2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.space3),
        ) {
            Surface(
                modifier = Modifier.size(layout.stepDot + spacing.space1),
                shape = MaterialTheme.shapes.small,
                color = look.badge,
                contentColor = look.onBadge,
            ) {
                Box(contentAlignment = Alignment.Center) { Text(letter, style = MaterialTheme.typography.labelMedium) }
            }
            Box(Modifier.weight(1f)) { AppRichText(text, MaterialTheme.typography.bodyLarge) }
            when (state) {
                AnswerState.CORRECT -> Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(layout.iconSmall))
                AnswerState.WRONG -> Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(layout.iconSmall))
                else -> Unit
            }
        }
    }
}
