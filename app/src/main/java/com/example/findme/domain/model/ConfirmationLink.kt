package com.example.findme.domain.model

import java.net.URI
import java.net.URLDecoder

enum class ConfirmationLink {
    Opened, Failed;

    companion object {
        const val REDIRECT = "findme://auth/confirmed"

        /** A callback never authenticates a user: passwords are checked by Auth on login. */
        fun parse(value: String?): ConfirmationLink? {
            if (value == null || value.length > 8192) return null
            val uri = runCatching { URI(value) }.getOrNull() ?: return null
            if (uri.scheme != "findme" || uri.host != "auth" || uri.path != "/confirmed" || uri.userInfo != null || uri.port != -1) return null
            val failed = listOfNotNull(uri.rawQuery, uri.rawFragment).any { parameters ->
                parameters.split('&').any { parameter ->
                    val pair = parameter.split('=', limit = 2)
                    val key = runCatching { URLDecoder.decode(pair[0], "UTF-8") }.getOrNull()
                    key in listOf("error", "error_code") && pair.getOrNull(1)?.isNotBlank() == true
                }
            }
            return if (failed) Failed else Opened
        }
    }
}
