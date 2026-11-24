package com.madovygabor.maturimate.core.network

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopAuthPageTest {

    // The library's default page had the Supabase logo and English text.
    @Test
    fun thePageAfterTheGoogleSignInIsTheAppsOwnInSlovakAndHungarian() {
        val page = signInDonePage()

        assertTrue("MaturiMate" in page)
        assertTrue("lang=\"sk\"" in page && "Prihlásenie prebehlo úspešne." in page, page)
        assertTrue("lang=\"hu\"" in page && "Sikeresen bejelentkeztél." in page, page)
        assertFalse(page.contains("supabase", ignoreCase = true), page)
        assertFalse("Logged in" in page, page)
        // nothing loaded from elsewhere
        assertFalse("http" in page, page)
    }

    @Test
    fun thePageAfterAFailedSignInIsTheAppsOwnToo() {
        val page = signInFailedPage()

        assertTrue("lang=\"sk\"" in page && "Prihlásenie sa nepodarilo." in page, page)
        assertTrue("lang=\"hu\"" in page && "A bejelentkezés nem sikerült." in page, page)
        assertFalse(page.contains("supabase", ignoreCase = true), page)
        assertFalse("http" in page, page)
    }
}
