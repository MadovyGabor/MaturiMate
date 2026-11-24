package com.madovygabor.maturimate.core.presentation.uiComponents

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import com.madovygabor.maturimate.core.presentation.theme.extendedTypography
import com.madovygabor.maturimate.core.presentation.theme.layout
import com.madovygabor.maturimate.core.presentation.theme.motion
import com.madovygabor.maturimate.core.presentation.theme.spacing
import maturimate.shared.generated.resources.Res
import maturimate.shared.generated.resources.code_field_state
import org.jetbrains.compose.resources.stringResource

/** How many digits a one-time code of the app has. */
const val CODE_LENGTH = 6

/**
 * What the code field holds after the text changed from [old] to [raw]: only the digits 0 to 9, at most [length].
 *
 * Typing adds a digit and backspace removes the last one. A pasted (or auto-filled) code that came on top of digits
 * already typed replaces them: "12" then a paste of "123456" is "123456", not "121234".
 */
fun applyCodeInput(old: String, raw: String, length: Int = CODE_LENGTH): String {
    val digits = raw.filter { it in '0'..'9' }
    if (digits.length <= length) return digits
    val oldDigits = old.filter { it in '0'..'9' }
    val pasted = if (raw.startsWith(old)) digits.drop(oldDigits.length) else digits
    return (if (pasted.length >= length) pasted else digits).take(length)
}

/** The box that takes the next digit: the first empty one, or the last when the code is complete. */
fun activeCodeBox(code: String, length: Int = CODE_LENGTH): Int = code.length.coerceIn(0, length - 1)

/**
 * The 6-digit code of an e-mail as six boxes (docs/design-system/components/CodeField.md).
 *
 * One hidden text field takes the input, so the number keyboard, backspace, paste, auto-fill of the code and the
 * screen reader work as with any field; the boxes only show it. A digit moves on to the next box, backspace back to
 * the one before, a pasted code fills all six.
 *
 * @param label What the field is, read by the screen reader ("6 jegyű kód"); not shown.
 * @param error What is wrong and what to do; the boxes get the error border and an icon and the text appears below.
 * @param autoFocus Focuses the field (and so opens the keyboard) when it appears.
 * @param onImeAction Runs when the keyboard's action key is pressed.
 */
@Composable
fun AppCodeField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    enabled: Boolean = true,
    autoFocus: Boolean = false,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
) {
    val code = applyCodeInput("", value)
    val spacing = MaterialTheme.spacing
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (autoFocus) focusRequester.requestFocus() }
    val state = stringResource(Res.string.code_field_state, code.length, CODE_LENGTH)
    // the field keeps the cursor at the end: the boxes show no cursor, so a digit is always added or removed there
    val text = remember(code) { TextFieldValue(code, TextRange(code.length)) }

    BasicTextField(
        value = text,
        onValueChange = { onValueChange(applyCodeInput(code, it.text)) },
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .semantics {
                contentDescription = label
                stateDescription = state
                contentType = ContentType.SmsOtpCode
                if (error != null) error(error)
            },
        enabled = enabled,
        singleLine = true,
        cursorBrush = SolidColor(Color.Transparent),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
        keyboardActions = if (onImeAction != null) {
            KeyboardActions(onNext = { onImeAction() }, onDone = { onImeAction() }, onGo = { onImeAction() })
        } else KeyboardActions.Default,
        decorationBox = {
            // the digits are shown by the boxes; the hidden field is not drawn at all
            Column(verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clearAndSetSemantics {}
                        .clickable(
                            enabled = enabled,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            focusRequester.requestFocus()
                            keyboard?.show()
                        },
                    horizontalArrangement = Arrangement.spacedBy(spacing.space2, Alignment.CenterHorizontally),
                ) {
                    val active = if (focused && enabled) activeCodeBox(code) else -1
                    repeat(CODE_LENGTH) { index ->
                        CodeBox(
                            digit = code.getOrNull(index)?.toString().orEmpty(),
                            active = index == active,
                            error = error != null,
                            enabled = enabled,
                            modifier = Modifier.weight(1f, fill = true),
                        )
                    }
                }
                if (error != null) ErrorLine(error)
            }
        },
    )
}

@Composable
private fun CodeBox(digit: String, active: Boolean, error: Boolean, enabled: Boolean, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val layout = MaterialTheme.layout
    val motion = MaterialTheme.motion
    val highlighted = active || error
    val borderColor by animateColorAsState(
        targetValue = when {
            error -> colors.error
            active -> colors.primary
            else -> colors.outline
        },
        animationSpec = motion.spec(motion.durationShort),
        label = "codeBoxBorder",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (highlighted) layout.borderThick else layout.borderThin,
        animationSpec = motion.spec(motion.durationShort),
        label = "codeBoxBorderWidth",
    )
    Box(modifier.widthIn(max = layout.codeBoxMaxWidth), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(layout.codeBoxHeight),
            shape = MaterialTheme.shapes.medium,
            color = if (enabled) colors.surfaceContainerLowest else colors.surfaceContainerHigh,
            contentColor = colors.onBackground,
            border = BorderStroke(borderWidth, borderColor),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = digit,
                    // the monospaced code style, without its letter spacing: one digit sits in the middle of its box
                    style = MaterialTheme.extendedTypography.code.copy(letterSpacing = TextUnit.Unspecified),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** The error text under the boxes, with an icon: the error is not told by color alone. */
@Composable
private fun ErrorLine(text: String) {
    val spacing = MaterialTheme.spacing
    Row(
        modifier = Modifier.padding(horizontal = spacing.space1),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(spacing.space2),
    ) {
        Icon(Icons.Rounded.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(MaterialTheme.layout.iconSmall))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}
