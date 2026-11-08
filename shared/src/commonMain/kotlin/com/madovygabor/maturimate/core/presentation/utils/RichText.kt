package com.madovygabor.maturimate.core.presentation.utils

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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

// The text format of the content (tools/content-pipeline/README.md): `**bold**`, `*italic*`,
// paragraphs separated by a blank line, and in lessons lists (`- item` / `1. item`, one item per line).
// Gap markers `[[n]]` belong to the tasks.

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

/** A piece of the text of a gap task: some text, or the gap with the number written in `[[n]]`. */
sealed interface TextPart {
    data class Text(val text: String) : TextPart
    data class Gap(val number: Int) : TextPart
}

private val GAP = Regex("""\[\[(\d+)]]""")

/** Splits the text of a gap task at its `[[n]]` markers, in reading order. */
fun splitGaps(text: String): List<TextPart> = buildList {
    var last = 0
    for (match in GAP.findAll(text)) {
        if (match.range.first > last) add(TextPart.Text(text.substring(last, match.range.first)))
        add(TextPart.Gap(match.groupValues[1].toInt()))
        last = match.range.last + 1
    }
    if (last < text.length) add(TextPart.Text(text.substring(last)))
}

private val LIST_MARKER = Regex("""(?m)^(-|\d+\.)\s+""")

/** A text as plain words, for a line in a list: the markers are removed and a `[[n]]` gap becomes an ellipsis. */
fun plainText(text: String): String =
    text.replace(GAP, "…").replace(LIST_MARKER, "").replace("*", "").replace(Regex("""\s+"""), " ").trim()

/** The words of a text without the markers, for the reading time. */
fun wordCount(text: String): Int =
    text.replace(LIST_MARKER, "").replace("*", "").split(Regex("""\s+""")).count { it.isNotBlank() }

/** A block of a lesson text: a paragraph, or a list whose items are written one per line (`- ` or `1. `). */
sealed interface TextBlock {
    data class Paragraph(val text: String) : TextBlock
    data class Bullets(val items: List<String>) : TextBlock
    data class Numbered(val items: List<String>) : TextBlock
}

private val BULLET_LINE = Regex("""^-\s+(.+)$""")
private val NUMBERED_LINE = Regex("""^\d+\.\s+(.+)$""")

/**
 * Splits a text at its blank lines into blocks. A block whose lines all start with `- ` is a bulleted list, one whose
 * lines all start with `1. ` (any number) a numbered list; the numbers shown follow the position, not the written number.
 */
fun textBlocks(text: String): List<TextBlock> = text.split("\n\n").filter { it.isNotBlank() }.map { paragraph ->
    val lines = paragraph.lines().map { it.trim() }.filter { it.isNotEmpty() }
    val bullets = lines.map { BULLET_LINE.matchEntire(it)?.groupValues?.get(1) }
    val numbered = lines.map { NUMBERED_LINE.matchEntire(it)?.groupValues?.get(1) }
    when {
        bullets.all { it != null } -> TextBlock.Bullets(bullets.filterNotNull())
        numbered.all { it != null } -> TextBlock.Numbered(numbered.filterNotNull())
        else -> TextBlock.Paragraph(paragraph)
    }
}

/**
 * Text of the content in the given [style]: paragraphs (blank line) and lists one under the other, `**bold**` and
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
        textBlocks(text).forEach { block ->
            when (block) {
                is TextBlock.Paragraph -> Text(richText(block.text), style = style, color = color)
                is TextBlock.Bullets -> ListItems(block.items, style, color) { "•" }
                is TextBlock.Numbered -> ListItems(block.items, style, color) { "${it + 1}." }
            }
        }
    }
}

@Composable
private fun ListItems(items: List<String>, style: TextStyle, color: Color, marker: (Int) -> String) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space1)) {
        items.forEachIndexed { index, item ->
            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.space2)) {
                Text(marker(index), style = style, color = color)
                Text(richText(item), style = style, color = color, modifier = Modifier.weight(1f))
            }
        }
    }
}
