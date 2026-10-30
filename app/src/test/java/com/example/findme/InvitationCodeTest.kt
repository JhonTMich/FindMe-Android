package com.example.findme

import com.example.findme.domain.model.InvitationCode
import org.junit.Assert.*
import org.junit.Test

class InvitationCodeTest {
    @Test fun acceptsCopiedAndTypedShortCodes() {
        assertTrue(InvitationCode.isValid("abcd-efgh"))
        assertEquals("ABCDEFGH", InvitationCode.normalize(" abcd - efgh\n"))
        assertEquals("ABCD-EFGH", InvitationCode.display("ABCDEFGH"))
        assertFalse(InvitationCode.isValid("ABCD-0000"))
        assertFalse(InvitationCode.isValid("ABCD"))
    }
    @Test fun retainsCompatibilityWithPreviousInvitations() {
        assertTrue(InvitationCode.isValid("abcdef0123456789abcdef0123456789"))
    }
    @Test fun generatedCodesAreShortAndUnambiguous() {
        repeat(100) {
            val code = InvitationCode.generate()
            assertEquals(8, code.length)
            assertTrue(InvitationCode.isValid(code))
            assertFalse(code.any { it in "IO01" })
        }
    }
}
