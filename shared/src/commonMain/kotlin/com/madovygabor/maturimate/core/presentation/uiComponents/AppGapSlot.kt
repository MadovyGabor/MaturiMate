package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing

/**
 * A gap in a sentence that is filled from a word list (docs/design-system/components/GapSlot.md). Empty it shows
 * its number; filled it shows the word, and a tap on it empties the gap again.
 *
 * @param number The number of the gap in the sentence (`[[n]]`).
 * @param word The word in the gap; null while it is empty.
 * @param state `DEFAULT` while empty, `SELECTED` when filled; `CORRECT` or `WRONG` after checking.
 */
@Composable
fun AppGapSlot(
    number: Int,
    word: String?,
    state: AnswerState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val look = answerColors(state)
    Surface(
        modifier = modifier
            .defaultMinSize(minWidth = MaterialTheme.layout.gapSlotMinWidth)
            .height(MaterialTheme.layout.gapSlotHeight)
            .selectable(selected = word != null, enabled = enabled, onClick = onClick, role = Role.Button),
        shape = MaterialTheme.shapes.small,
        color = if (word == null) MaterialTheme.colorScheme.surfaceContainerLowest else look.container,
        contentColor = if (word == null) MaterialTheme.colorScheme.onSurfaceVariant else look.content,
        border = look.border,
    ) {
        Box(Modifier.padding(horizontal = MaterialTheme.spacing.space3), contentAlignment = Alignment.Center) {
            Text(word ?: number.toString(), style = MaterialTheme.typography.labelLarge)
        }
    }
}
