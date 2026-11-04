package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.spacing

/**
 * A gap in a sentence that is filled by typing (docs/design-system/components/GapField.md): a small field that sits
 * in the line of text.
 *
 * @param description What a screen reader says, for example "gap 2".
 * @param state `DEFAULT` while writing, `CORRECT` or `WRONG` after checking (then [readOnly]).
 */
@Composable
fun AppGapField(
    value: String,
    onValueChange: (String) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    state: AnswerState = AnswerState.DEFAULT,
    readOnly: Boolean = false,
) {
    val look = answerColors(state)
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .width(MaterialTheme.layout.gapFieldWidth)
            .height(MaterialTheme.layout.gapSlotHeight)
            .semantics { contentDescription = description },
        readOnly = readOnly,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = look.content),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        decorationBox = { inner ->
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = MaterialTheme.shapes.small,
                color = if (state == AnswerState.DEFAULT) colors.surfaceContainerLowest else look.container,
                contentColor = look.content,
                border = look.border,
            ) {
                Box(Modifier.padding(horizontal = MaterialTheme.spacing.space2), contentAlignment = Alignment.CenterStart) { inner() }
            }
        },
    )
}
