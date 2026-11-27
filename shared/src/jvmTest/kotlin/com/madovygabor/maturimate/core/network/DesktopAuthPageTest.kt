package com.madovygabor.maturimate.core.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopAuthPageTest {

    @Test
    fun donePageRendersSingleAppLanguageWithDesignTokensAndNoExternalAssets() {
        val sk = signInDonePage("sk")
        assertTrue("lang=\"sk\"" in sk, sk.take(1_500))
        assertTrue("MaturiMate" in sk, sk.take(1_500))
        assertTrue("Prihlásenie prebehlo úspešne" in sk, sk.take(1_500))
        assertTrue("Môžeš sa vrátiť do aplikácie MaturiMate." in sk, sk.take(1_500))
        assertFalse("Sikeresen bejelentkeztél" in sk, sk.take(1_500))
        assertSelfContainedAndThemed(sk)

        val hu = signInDonePage("hu")
        assertTrue("lang=\"hu\"" in hu, hu.take(1_500))
        assertTrue("MaturiMate" in hu, hu.take(1_500))
        assertTrue("Sikeresen bejelentkeztél" in hu, hu.take(1_500))
        assertTrue("Visszatérhetsz a MaturiMate alkalmazásba." in hu, hu.take(1_500))
        assertFalse("Prihlásenie prebehlo úspešne" in hu, hu.take(1_500))
        assertSelfContainedAndThemed(hu)
    }

    @Test
    fun failedPageRendersSingleAppLanguageWithDesignTokensAndNoExternalAssets() {
        val sk = signInFailedPage("sk")
        assertTrue("lang=\"sk\"" in sk, sk.take(1_500))
        assertTrue("Prihlásenie sa nepodarilo" in sk, sk.take(1_500))
        assertTrue("Vráť sa do aplikácie MaturiMate a skús to znova." in sk, sk.take(1_500))
        assertFalse("A bejelentkezés nem sikerült" in sk, sk.take(1_500))
        assertSelfContainedAndThemed(sk)

        val hu = signInFailedPage("hu")
        assertTrue("lang=\"hu\"" in hu, hu.take(1_500))
        assertTrue("A bejelentkezés nem sikerült" in hu, hu.take(1_500))
        assertTrue("Térj vissza a MaturiMate alkalmazásba, és próbáld újra." in hu, hu.take(1_500))
        assertFalse("Prihlásenie sa nepodarilo" in hu, hu.take(1_500))
        assertSelfContainedAndThemed(hu)
    }

    @Test
    fun languageTagNormalizationSelectsHungarianForHuTagsAndSlovakOtherwise() {
        assertTrue("lang=\"hu\"" in signInDonePage("hu-HU"))
        assertTrue("lang=\"hu\"" in signInDonePage("HU_SK"))
        assertTrue("lang=\"sk\"" in signInDonePage("sk-SK"))
        assertTrue("lang=\"sk\"" in signInDonePage("en-US"))
        assertTrue("lang=\"sk\"" in signInDonePage(null))
        assertTrue("lang=\"sk\"" in signInFailedPage(""))
    }

    @Test
    fun escapeHtmlEscapesAllMarkupCharacters() {
        assertEquals(
            "&lt;script&gt;alert(&quot;x&quot; &amp; &#39;y&#39;)&lt;/script&gt;",
            escapeHtml("<script>alert(\"x\" & 'y')</script>"),
        )
    }

    private fun assertSelfContainedAndThemed(html: String) {
        // the page embeds fonts and the logo (large base64): failure messages show only its start
        val head = html.take(1_500)
        assertTrue("prefers-color-scheme: dark" in html, "Missing dark mode media query")
        assertTrue("#fcf8f1" in html, "Missing light background token")
        assertTrue("#17130e" in html, "Missing dark background token")
        assertTrue("#7b5800" in html && "#f4c04e" in html, "Missing primary (deep gold) tokens")
        // the app's own fonts and logo, embedded from its resources
        assertTrue("font-family: \"Literata\"" in html && "font-family: \"Manrope\"" in html, "Missing embedded fonts")
        assertTrue("src=\"data:image/png;base64," in html, "Missing embedded logo")
        // nothing is loaded from elsewhere: no address at all, every image and font is a data: URI, and the policy forbids the rest
        assertFalse("http:" in html || "https:" in html || "//" in html.replace(Regex("base64,[A-Za-z0-9+/=]+"), ""), head)
        assertTrue("default-src 'none'" in html, head)
        assertFalse(Regex("<img[^>]+src=\"(?!data:)").containsMatchIn(html), head)
        assertFalse(html.contains("supabase", ignoreCase = true), head)
        assertFalse("Logged in" in html, head)
        assertFalse("<script" in html.lowercase(), head)
    }
}
