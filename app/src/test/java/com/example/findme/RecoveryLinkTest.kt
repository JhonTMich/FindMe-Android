package com.example.findme

import com.example.findme.domain.model.RecoveryLink
import org.junit.Assert.*
import org.junit.Test

class RecoveryLinkTest {
    @Test fun extractsThePkceCodeFromTheConfiguredLinkOnly() {
        assertEquals(RecoveryLink.Code("7c1f4b2e-0d9a-4f6e-9c3b-2a5d8e1f0b47"),
            RecoveryLink.parse("findme://auth/reset?code=7c1f4b2e-0d9a-4f6e-9c3b-2a5d8e1f0b47"))
        listOf("findme://auth/confirmed?code=abc", "https://auth/reset?code=abc", "findme://other/reset?code=abc",
            "findme://user@auth/reset?code=abc", "findme://auth:1/reset?code=abc", null).forEach {
            assertNull(it, RecoveryLink.parse(it))
        }
    }

    @Test fun expiredMissingOrMalformedCodesFail() {
        assertEquals(RecoveryLink.Failed, RecoveryLink.parse("findme://auth/reset#error=access_denied&error_code=otp_expired"))
        assertEquals(RecoveryLink.Failed, RecoveryLink.parse("findme://auth/reset"))
        assertEquals(RecoveryLink.Failed, RecoveryLink.parse("findme://auth/reset?code=%3Cscript%3E"))
        assertEquals(RecoveryLink.Failed, RecoveryLink.parse("findme://auth/reset?code=" + "a".repeat(513)))
        assertNull(RecoveryLink.parse("findme://auth/reset?code=" + "a".repeat(9000)))
    }
}
