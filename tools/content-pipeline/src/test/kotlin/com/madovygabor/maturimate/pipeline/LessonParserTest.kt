package com.madovygabor.maturimate.pipeline

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LessonParserTest {

    private fun parse(text: String) = LessonParser.parse(text.trimIndent())

    @Test
    fun textWithoutHeadingIsOnePageWithoutTitle() {
        val lesson = parse("""
            First paragraph,
            wrapped over two lines.

            Second paragraph.
        """)
        assertEquals(emptyList(), lesson.errors)
        assertEquals(1, lesson.pages.size)
        assertEquals(null, lesson.pages[0].title)
        assertEquals(
            listOf("First paragraph, wrapped over two lines.", "Second paragraph."),
            lesson.pages[0].blocks.map { it.text },
        )
    }

    @Test
    fun listItemsStayOnSeparateLines() {
        val lesson = parse("""
            Intro line.
            - first item,
              continued here
            - second item
            1. a numbered one

            After the list.
        """)
        assertEquals(emptyList(), lesson.errors)
        assertEquals(
            listOf("Intro line.", "- first item, continued here\n- second item\n1. a numbered one", "After the list."),
            lesson.pages.single().blocks.map { it.text },
        )
    }

    @Test
    fun aListInsideABoxIsAllowed() {
        val lesson = parse("""
            :::tip
            - one
            - two
            :::
        """)
        assertEquals(emptyList(), lesson.errors)
        assertEquals("- one\n- two", lesson.pages.single().blocks.single().text)
    }

    @Test
    fun headingsStartPagesAndBoxesAreBlocks() {
        val lesson = parse("""
            ## One

            Text.

            :::example
            An example.

            Another line of it.
            :::

            ## Two

            :::tip
            Remember this.
            :::
        """)
        assertEquals(emptyList(), lesson.errors)
        assertEquals(listOf("One", "Two"), lesson.pages.map { it.title })
        assertEquals(listOf("PARAGRAPH", "EXAMPLE"), lesson.pages[0].blocks.map { it.type })
        assertEquals("An example.\n\nAnother line of it.", lesson.pages[0].blocks[1].text)
        assertEquals(listOf("TIP"), lesson.pages[1].blocks.map { it.type })
    }

    @Test
    fun allBoxTypesAreKnownAndASpaceAfterTheColonsIsAllowed() {
        val lesson = parse("""
            :::note
            a
            :::
            ::: warning
            b
            :::
            :::quote
            c
            :::
        """)
        assertEquals(emptyList(), lesson.errors)
        assertEquals(listOf("NOTE", "WARNING", "QUOTE"), lesson.pages.single().blocks.map { it.type })
    }

    @Test
    fun unknownBoxTypeIsAnError() {
        val lesson = parse("""
            :::joke
            a
            :::
        """)
        assertTrue(lesson.errors.single().contains("unknown box type"), lesson.errors.toString())
    }

    @Test
    fun boxInsideBoxIsAnError() {
        val lesson = parse("""
            :::tip
            a
            :::example
            b
            :::
        """)
        assertTrue(lesson.errors.any { "a box inside a box" in it }, lesson.errors.toString())
    }

    @Test
    fun boxThatIsNotClosedIsAnError() {
        assertTrue(parse(":::tip\na").errors.any { "not closed" in it })
        assertTrue(parse(":::tip\na\n## Next").errors.any { "a page starts while a box is open" in it })
    }

    @Test
    fun strayCloserIsAnError() {
        assertTrue(parse("a\n:::").errors.any { "was not opened" in it })
    }

    @Test
    fun onlyLevelTwoHeadingsAreAllowed() {
        assertTrue(parse("# One").errors.any { "only" in it && "Title" in it })
        assertTrue(parse("### Three").errors.any { "only" in it && "Title" in it })
    }
}
