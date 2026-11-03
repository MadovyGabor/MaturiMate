package com.madovygabor.maturimate.core.presentation.utils

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.madovygabor.maturimate.core.presentation.theme.spacing

// The text format of the content (tools/content-pipeline/README.md): `**bold**`, `*italic*`, and
// paragraphs separated by a blank line. Gap markers `[[n]]` belong to the tasks.

private val INLINE = Regex("""\*\*(.+?)\*\*|\*(.+?)\*""")

/** The text of the content as styled text: `**bold**` and `*italic*` become spans, the markers disappear. */
fun richText(
    text: String,
    bold: SpanStyle = SpanStyle(fontWeight = FontWeight.SemiBold),
    italic: SpanStyle = SpanStyle(fontStyle = FontStyle.Italic),
): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (match in INLINE.findAll(text)) {
        append(text.substring(last, match.range.first))
        val boldText = match.groups[1]?.value
        if (boldText != null) withStyle(bold) { append(boldText) } else withStyle(italic) { append(match.groups[2]!!.value) }
        last = match.range.last + 1
    }
    append(text.substring(last))
}

/** The words of a text without the markers, for the reading time. */
fun wordCount(text: String): Int = text.replace("*", "").split(Regex("""\s+""")).count { it.isNotBlank() }

/**
 * Text of the content in the given [style]: paragraphs (blank line) one under the other, `**bold**` and
 * `*italic*` styled.
 */
@Composable
fun AppRichText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space3)) {
        text.split("\n\n").filter { it.isNotBlank() }.forEach { paragraph ->
            Text(richText(paragraph), style = style, color = color)
        }
    }
}
