package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.ui.semantics.Role
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing
import com.madovygabor.maturimate.core.presentation.utils.AppRichText

/**
 * An item of a matching task and the answer matched to it (docs/design-system/components/MatchPair.md). A tap makes
 * the item the active one; the answer is then picked from the chips under the list.
 *
 * @param prompt The item on the left of the pair.
 * @param answer The answer matched to it; null while it has none.
 * @param placeholder What an empty answer says, for example "válassz…".
 * @param active This item gets the next answer.
 * @param state `DEFAULT` before checking; `CORRECT` or `WRONG` after it.
 * @param correction For a wrong pair, what belongs there ("Helyes: metafora").
 */
@Composable
fun AppMatchPair(
    prompt: String,
    answer: String?,
    placeholder: String,
    active: Boolean,
    state: AnswerState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    correction: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val layout = MaterialTheme.layout
    val spacing = MaterialTheme.spacing
    val look = answerColors(state)
    val checked = state == AnswerState.CORRECT || state == AnswerState.WRONG
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = layout.choiceCardMinHeight)
            .selectable(selected = active, enabled = enabled, onClick = onClick, role = Role.RadioButton),
        shape = MaterialTheme.shapes.medium,
        color = if (checked) look.container else colors.surfaceContainerLow,
        contentColor = if (checked) look.content else colors.onBackground,
        border = when {
            checked -> look.border
            active -> BorderStroke(layout.borderThick, colors.primary)
            else -> BorderStroke(layout.borderThin, colors.outlineVariant)
        },
    ) {
        Column(Modifier.padding(spacing.space3), verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
            AppRichText(prompt, MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.space2)) {
                Surface(
                    modifier = Modifier.defaultMinSize(minWidth = layout.gapSlotMinWidth + layout.gapSlotMinWidth / 2),
                    shape = MaterialTheme.shapes.small,
                    color = if (answer == null) colors.surfaceContainerLowest else if (checked) look.container else colors.primaryContainer,
                    contentColor = if (answer == null) colors.onSurfaceVariant else if (checked) look.content else colors.onPrimaryContainer,
                    border = BorderStroke(layout.borderThin, if (answer == null) colors.outline else colors.primary),
                ) {
                    Box(Modifier.padding(horizontal = spacing.space3, vertical = spacing.space2), contentAlignment = Alignment.Center) {
                        Text(answer ?: placeholder, style = MaterialTheme.typography.labelLarge)
                    }
                }
                // a right or wrong pair also shows a check or an X
                when (state) {
                    AnswerState.CORRECT -> Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(layout.iconSmall))
                    AnswerState.WRONG -> Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(layout.iconSmall))
                    else -> Unit
                }
            }
            if (correction != null) {
                Text(correction, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}
