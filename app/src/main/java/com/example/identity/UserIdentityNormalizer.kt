package com.example.identity

object UserIdentityNormalizer {
    fun phone10(raw: String): String = raw.filter { it.isDigit() }.takeLast(10)

    fun email(raw: String): String = raw.trim().lowercase()

    fun phoneE164India(phone10: String): String = "+91${phone10(phone10)}"

    fun isValidPhone10(phone10: String): Boolean = phone10.length == 10

    fun isValidEmail(email: String): Boolean =
        email.isNotBlank() && email.contains("@") && !email.startsWith("@") && !email.endsWith("@")
}
