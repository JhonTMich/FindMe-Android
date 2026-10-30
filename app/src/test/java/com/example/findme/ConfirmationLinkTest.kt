package com.example.findme

import com.example.findme.domain.model.ConfirmationLink
import org.junit.Assert.*
import org.junit.Test

class ConfirmationLinkTest {
    @Test fun recognizesOnlyTheConfiguredCallback() {
        assertEquals(ConfirmationLink.Opened, ConfirmationLink.parse("findme://auth/confirmed#access_token=ignored&refresh_token=ignored"))
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
