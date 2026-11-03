package com.madovygabor.maturimate.core.presentation.utils

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals

class RichTextTest {

    @Test
    fun plainTextIsUnchanged() {
        val text = richText("Egy mondat.")
        assertEquals("Egy mondat.", text.text)
        assertEquals(emptyList(), text.spanStyles)
    }

    @Test
    fun boldAndItalicBecomeSpansAndTheMarkersDisappear() {
        val text = richText("A **szókép** egy *átvitt* használat.")

        assertEquals("A szókép egy átvitt használat.", text.text)
        val (bold, italic) = text.spanStyles
        assertEquals("szókép", text.text.substring(bold.start, bold.end))
        assertEquals(FontWeight.SemiBold, bold.item.fontWeight)
        assertEquals("átvitt", text.text.substring(italic.start, italic.end))
        assertEquals(FontStyle.Italic, italic.item.fontStyle)
    }

    @Test
    fun anUnclosedMarkerStaysAsWritten() {
        assertEquals("a *b", richText("a *b").text)
    }

    @Test
    fun wordsAreCountedWithoutTheMarkers() {
        assertEquals(4, wordCount("A **szókép** egy *átvitt*"))
        assertEquals(0, wordCount("  "))
    }
}
