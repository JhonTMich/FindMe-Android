package com.example.findme.domain.model

import java.util.Locale

object Username {
    fun normalize(value: String) = value.trim().lowercase(Locale.ROOT)
    fun isValid(value: String) = Regex("^[a-z0-9_]{3,24}$").matches(normalize(value))
}
