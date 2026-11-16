package com.example.findme.domain.model

import java.net.URI
import java.net.URLDecoder

/**
 * Return link of a password-recovery email. The code is useless without the PKCE verifier that
 * stayed on the phone that requested the email, so another app opening this link gains nothing.
 */
sealed interface RecoveryLink {
    data class Code(val value: String) : RecoveryLink
    data object Failed : RecoveryLink

    companion object {
        const val REDIRECT = "findme://auth/reset"
        private val validCode = Regex("[A-Za-z0-9_-]{1,512}")

        fun parse(value: String?): RecoveryLink? {
            if (value == null || value.length > 8192) return null
            val uri = runCatching { URI(value) }.getOrNull() ?: return null
            if (uri.scheme != "findme" || uri.host != "auth" || uri.path != "/reset" || uri.userInfo != null || uri.port != -1) return null
            val parameters = listOfNotNull(uri.rawQuery, uri.rawFragment).flatMap { it.split('&') }.mapNotNull { parameter ->
                val pair = parameter.split('=', limit = 2)
                val key = runCatching { URLDecoder.decode(pair[0], "UTF-8") }.getOrNull() ?: return@mapNotNull null
                key to runCatching { URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8") }.getOrDefault("")
            }
            if (parameters.any { (key, value) -> key in listOf("error", "error_code") && value.isNotBlank() }) return Failed
            val code = parameters.firstOrNull { it.first == "code" }?.second ?: return Failed
            return if (validCode.matches(code)) Code(code) else Failed
        }
    }
}
