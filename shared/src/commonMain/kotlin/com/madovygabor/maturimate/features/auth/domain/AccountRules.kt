package com.madovygabor.maturimate.features.auth.domain

/** The checks of the account form, the same ones Supabase applies (docs/supabase-setup.md). */
object AccountRules {
    private val email = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    /** Length of the code in the sign-up and password reset e-mails. */
    const val CODE_LENGTH = 6

    fun isEmail(value: String): Boolean = email.matches(value.trim())

    /** The three parts of the password rule, so the form can show which of them are met while the user types. */
    fun passwordHasLength(value: String): Boolean = value.length >= 8
    fun passwordHasBothCases(value: String): Boolean = value.any { it.isLowerCase() } && value.any { it.isUpperCase() }
    fun passwordHasDigit(value: String): Boolean = value.any { it.isDigit() }

    /** At least 8 characters, with a lower case letter, an upper case letter and a digit. */
    fun isStrongPassword(value: String): Boolean =
        passwordHasLength(value) && passwordHasBothCases(value) && passwordHasDigit(value)

    fun isCode(value: String): Boolean = value.length == CODE_LENGTH && value.all { it.isDigit() }
}
