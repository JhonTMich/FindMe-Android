package com.example.findme

import com.example.findme.domain.model.Registration
import org.junit.Assert.*
import org.junit.Test

class RegistrationTest {
    @Test fun allowsSelfChosenDetailsWithMatchingPassword() {
        assertNull(Registration.error("Chosen name", "someone@example.test", "My_User", "test-password", "test-password"))
    }
    @Test fun rejectsMismatchedPasswordsAndInvalidIdentity() {
        assertNotNull(Registration.error("Name", "someone@example.test", "my_user", "password123", "different"))
        assertFalse(Registration.isEmail("someone @example.test"))
        assertNotNull(Registration.error("Mi cuenta", "someone@example.test", "my_user", "password123", "password123"))
        assertNotNull(Registration.error("Name", "someone@example.test", "bad user", "password123", "password123"))
    }
}
