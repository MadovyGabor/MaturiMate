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
    fun gapsSplitTheText() {
        assertEquals(
            listOf(
                TextPart.Text("A metafora két dolgot "), TextPart.Gap(1), TextPart.Text(", a hasonlat "), TextPart.Gap(2), TextPart.Text("."),
            ),
            splitGaps("A metafora két dolgot [[1]], a hasonlat [[2]]."),
        )
        assertEquals(listOf(TextPart.Gap(1), TextPart.Gap(2)), splitGaps("[[1]][[2]]"))
        assertEquals(listOf(TextPart.Text("Nincs hézag.")), splitGaps("Nincs hézag."))
    }

    @Test
    fun wordsAreCountedWithoutTheMarkers() {
        assertEquals(4, wordCount("A **szókép** egy *átvitt*"))
        assertEquals(0, wordCount("  "))
        assertEquals(4, wordCount("- egy két\n- három négy"))
    }

    @Test
    fun listsAreRecognisedByTheirLines() {
        assertEquals(
            listOf(
                TextBlock.Paragraph("Bevezető."),
                TextBlock.Bullets(listOf("egy", "**kettő**")),
                TextBlock.Numbered(listOf("első", "második")),
                TextBlock.Paragraph("- egy\n1. vegyes"),
            ),
            textBlocks("Bevezető.\n\n- egy\n- **kettő**\n\n1. első\n3. második\n\n- egy\n1. vegyes"),
        )
    }

    @Test
    fun aDashWithoutSpaceIsNotAListMarker() {
        assertEquals(listOf(TextBlock.Paragraph("-5 fok")), textBlocks("-5 fok"))
        assertEquals("egy kettő", plainText("- egy\n- kettő"))
    }
}
