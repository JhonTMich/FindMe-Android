package com.example.findme

import com.example.findme.domain.model.ConfirmationLink
import org.junit.Assert.*
import org.junit.Test

class ConfirmationLinkTest {
    @Test fun recognizesOnlyTheConfiguredCallback() {
        assertEquals(ConfirmationLink.Opened, ConfirmationLink.parse("findme://auth/confirmed#access_token=ignored&refresh_token=ignored"))
        // PKCE callback: the code cannot be redeemed without the verifier the server discarded.
        assertEquals(ConfirmationLink.Opened, ConfirmationLink.parse("findme://auth/confirmed?code=7c1f4b2e-0d9a-4f6e-9c3b-2a5d8e1f0b47"))
        listOf("https://auth/confirmed", "findme://other/confirmed", "findme://auth/other", "findme://user@auth/confirmed", "findme://auth:123/confirmed").forEach {
            assertNull(ConfirmationLink.parse(it))
        }
    }
    @Test fun detectsExpiredAndFailedLinksWithoutDisplayingRemoteDetails() {
        assertEquals(ConfirmationLink.Failed, ConfirmationLink.parse("findme://auth/confirmed#error_code=otp_expired"))
        assertEquals(ConfirmationLink.Failed, ConfirmationLink.parse("findme://auth/confirmed?error=access_denied"))
        assertNull(ConfirmationLink.parse(null))
        assertNull(ConfirmationLink.parse("findme://auth/confirmed#" + "x".repeat(8192)))
    }
}
