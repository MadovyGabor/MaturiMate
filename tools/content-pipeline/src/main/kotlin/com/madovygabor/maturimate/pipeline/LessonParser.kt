package com.madovygabor.maturimate.pipeline

/** One block of a lesson page: a paragraph or a box. [type] is a `page_block.block_type`. */
data class LessonBlockData(val type: String, val text: String)

/** One page of a lesson; [title] is null for text before the first `##`. */
data class LessonPageData(val title: String?, val blocks: List<LessonBlockData>)

/** What [LessonParser] found: the pages and every problem of the text. */
data class ParsedLesson(val pages: List<LessonPageData>, val errors: List<String>)

/**
 * Splits the text of a lesson file (the part after the front matter) into pages and blocks.
 *
 * * `## Title` starts a page; text before the first `##` is a page without a title.
 * * `:::type` ... `:::` is a box (`example`, `tip`, `note`, `warning`); it ends with a line of its own.
 * * Blank lines separate paragraphs; the lines of one paragraph are joined with a space.
 * * Lines that start with `- ` or `1. ` form a list: the items stay on separate lines of the block text.
 * * Other heading levels, unknown box types, boxes inside boxes and boxes that are not closed are errors.
 */
object LessonParser {

    private val BOX_TYPES = mapOf("example" to "EXAMPLE", "tip" to "TIP", "note" to "NOTE", "warning" to "WARNING")
    private val BOX_START = Regex("""^:::\s*(\S+)\s*$""")
    private val PAGE_TITLE = Regex("""^##\s+(.+?)\s*$""")
    private val OTHER_HEADING = Regex("""^#{1,6}(\s|$)""")

    /** The start of a list item: `- ` (bullet) or `1. ` (numbered). */
    val LIST_ITEM = Regex("""^(-|\d+\.)\s+\S""")

    fun parse(text: String): ParsedLesson {
        val errors = mutableListOf<String>()
        val pages = mutableListOf<LessonPageData>()
        var title: String? = null
        var blocks = mutableListOf<LessonBlockData>()
        var paragraph = mutableListOf<String>()
        var boxType: String? = null
        var boxParagraphs = mutableListOf<String>()

        fun flushParagraph() {
            if (paragraph.isEmpty()) return
            // A list keeps one line per item (a line without a marker continues the item before it);
            // any other paragraph is one line.
            val joined = if (LIST_ITEM.containsMatchIn(paragraph.first())) {
                paragraph.fold(mutableListOf<String>()) { items, line ->
                    if (LIST_ITEM.containsMatchIn(line) || items.isEmpty()) items += line else items[items.lastIndex] += " $line"
                    items
                }.joinToString("\n")
            } else {
                paragraph.joinToString(" ")
            }
            paragraph = mutableListOf()
            if (boxType != null) boxParagraphs += joined else blocks += LessonBlockData("PARAGRAPH", joined)
        }
        fun flushPage() {
            if (title != null || blocks.isNotEmpty()) pages += LessonPageData(title, blocks.toList())
            title = null
            blocks = mutableListOf()
        }

        text.lines().forEachIndexed { index, raw ->
            val line = raw.trim()
            val lineNo = index + 1
            val start = BOX_START.matchEntire(line)
            when {
                line == ":::" -> {
                    if (boxType == null) {
                        errors += "line $lineNo: ':::' closes a box that was not opened"
                    } else {
                        flushParagraph()
                        blocks += LessonBlockData(boxType, boxParagraphs.joinToString("\n\n"))
                        boxType = null
                        boxParagraphs = mutableListOf()
                    }
                }
                start != null -> {
                    if (boxType != null) {
                        errors += "line $lineNo: a box inside a box (the box before it is not closed with ':::')"
                    } else {
                        flushParagraph()
                        val kind = BOX_TYPES[start.groupValues[1]]
                        if (kind == null) errors += "line $lineNo: unknown box type '${start.groupValues[1]}' (use ${BOX_TYPES.keys.joinToString()})"
                        boxType = kind ?: "NOTE"
                    }
                }
                PAGE_TITLE.matches(line) -> {
                    if (boxType != null) {
                        errors += "line $lineNo: a page starts while a box is open (close it with ':::')"
                        boxType = null
                        boxParagraphs = mutableListOf()
                    }
                    flushParagraph()
                    flushPage()
                    title = PAGE_TITLE.matchEntire(line)!!.groupValues[1]
                }
                OTHER_HEADING.containsMatchIn(line) ->
                    errors += "line $lineNo: only '## Title' headings are allowed in a lesson"
                line.isEmpty() -> flushParagraph()
                else -> {
                    // A list item right after ordinary text starts a new paragraph, and so does text after a list only
                    // when a blank line comes between (a plain line right after an item continues the item).
                    if (LIST_ITEM.containsMatchIn(line) && paragraph.isNotEmpty() && !LIST_ITEM.containsMatchIn(paragraph.first())) flushParagraph()
                    paragraph += line
                }
            }
        }
        if (boxType != null) errors += "a box is not closed at the end of the text (missing ':::')"
        flushParagraph()
        flushPage()
        return ParsedLesson(pages, errors)
    }
}
