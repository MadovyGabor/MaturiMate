package com.madovygabor.maturimate.features.auth.domain

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The same checks as Supabase's (docs/supabase-setup.md), so the form catches them first. */
class AccountRulesTest {

    @Test
    fun emailNeedsANameADomainAndAnEnding() {
        assertTrue(AccountRules.isEmail("anna@example.com"))
        assertTrue(AccountRules.isEmail("  anna@example.sk  "))
        assertFalse(AccountRules.isEmail("anna@example"))
        assertFalse(AccountRules.isEmail("anna example.com"))
        assertFalse(AccountRules.isEmail("@example.com"))
        assertFalse(AccountRules.isEmail(""))
    }

    @Test
    fun passwordNeedsEightCharactersBothCasesAndADigit() {
        assertTrue(AccountRules.isStrongPassword("Passw0rd"))
        assertFalse(AccountRules.isStrongPassword("Pass0rd"))
        assertFalse(AccountRules.isStrongPassword("password1"))
        assertFalse(AccountRules.isStrongPassword("PASSWORD1"))
        assertFalse(AccountRules.isStrongPassword("Password"))
    }

    @Test
    fun codeIsSixDigits() {
        assertTrue(AccountRules.isCode("012345"))
        assertFalse(AccountRules.isCode("12345"))
        assertFalse(AccountRules.isCode("1234567"))
        assertFalse(AccountRules.isCode("12a456"))
    }
}
