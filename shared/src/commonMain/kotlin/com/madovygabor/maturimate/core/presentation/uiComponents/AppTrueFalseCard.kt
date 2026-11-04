package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing

/**
 * One of the two big answers of a true-or-false task: an icon in a circle and the word
 * (docs/design-system/components/TrueFalseCard.md). Before checking the icons are neutral, so the colors do
 * not give the answer away.
 *
 * @param icon A check for true, an X for false.
 */
@Composable
fun AppTrueFalseCard(
    label: String,
    icon: ImageVector,
    state: AnswerState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val spacing = MaterialTheme.spacing
    val layout = MaterialTheme.layout
    val look = answerColors(state)
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .alpha(if (state == AnswerState.DIMMED) 0.55f else 1f)
            .heightIn(min = layout.trueFalseCardMinHeight)
            .selectable(selected = state == AnswerState.SELECTED, enabled = enabled, onClick = onClick, role = Role.RadioButton),
        shape = MaterialTheme.shapes.large,
        color = look.container,
        contentColor = look.content,
        border = look.border,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(spacing.space3),
            verticalArrangement = Arrangement.spacedBy(spacing.space3, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier.size(layout.badge),
                shape = CircleShape,
                // neutral until the student chooses or the answer is checked
                color = if (state == AnswerState.DEFAULT || state == AnswerState.DIMMED) colors.tertiaryContainer else look.badge,
                contentColor = if (state == AnswerState.DEFAULT || state == AnswerState.DIMMED) colors.onTertiaryContainer else look.onBadge,
            ) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, modifier = Modifier.size(layout.icon)) }
            }
            Text(label, style = MaterialTheme.typography.titleLarge)
        }
    }
}
