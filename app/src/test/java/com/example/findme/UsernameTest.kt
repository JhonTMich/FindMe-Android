package com.example.findme

import com.example.findme.domain.model.Username
import org.junit.Assert.*
import org.junit.Test

class UsernameTest {
    @Test fun normalizesWithoutCaseDistinction() {
        assertEquals("jhontmich", Username.normalize("  JhonTMich  "))
        assertTrue(Username.isValid("JhonTMich"))
    }
    @Test fun rejectsEmailAndUnsupportedIdentifiers() {
        listOf("ab", "ábc", "has space", "person@mail.test", "x".repeat(25)).forEach {
            assertFalse(it, Username.isValid(it))
        }
        assertTrue(Username.isValid("user_123"))
    }
}
