package com.example.findme.domain.model

import java.security.SecureRandom
import java.util.Locale

object InvitationCode {
    // 40 random bits; excludes ambiguous I, O, 0 and 1.
    private const val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val random = SecureRandom()
    fun generate(): String = buildString { repeat(8) { append(alphabet[random.nextInt(alphabet.length)]) } }
    fun normalize(value: String): String = value.filterNot { it.isWhitespace() || it == '-' }.uppercase(Locale.ROOT)
    fun isValid(value: String): Boolean {
        val code = normalize(value)
        return (code.length == 8 && code.all { it in alphabet }) || Regex("[0-9A-F]{32}").matches(code)
    }
    fun display(value: String): String = if (value.length == 8) value.chunked(4).joinToString("-") else value
}
