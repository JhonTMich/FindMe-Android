package com.example.findme

import com.example.findme.data.model.*
import com.example.findme.domain.model.ConnectionControls
import org.junit.Assert.*
import org.junit.Test

class ConnectionControlsTest {
    private val ana = AccountProfile("ana", "Ana")
    private val bo = AccountProfile("bo", "Bo")
    private val connections = listOf(PersonConnection("c1", "ana", "me"), PersonConnection("c2", "bo", "me"))

    @Test fun defaultsShareEverythingWithoutNicknameOrSilence() {
        val controls = ConnectionControls("me", connections, emptyList())
        assertEquals("Ana", controls.nameOf(ana))
        assertFalse(controls.isMuted("ana"))
        val defaults = controls.forPerson("ana")!!
        assertTrue(defaults.shareSchedule && defaults.shareStatus)
        assertEquals("c1", defaults.connectionId)
        assertNull(controls.forPerson("stranger"))
    }

    @Test fun usesOnlyMyOwnPreferences() {
        val controls = ConnectionControls("me", connections, listOf(
            ConnectionPreference("c1", "me", nickname = "  Anita ", muted = true),
            // The other side's row (never returned by the server, but must be ignored anyway).
            ConnectionPreference("c2", "bo", nickname = "Yo", muted = true)
        ))
        assertEquals("Anita", controls.nameOf(ana))
        assertEquals("Bo", controls.nameOf(bo))
        assertTrue(controls.isMuted("ana"))
        assertFalse(controls.isMuted("bo"))
    }

    @Test fun blankNicknameFallsBackToTheName() {
        val controls = ConnectionControls("me", connections, listOf(ConnectionPreference("c1", "me", nickname = " ")))
        assertEquals("Ana", controls.nameOf(ana))
    }

    @Test fun silencedOrReadMessagesAreNotNotified() {
        val controls = ConnectionControls("me", connections, listOf(ConnectionPreference("c1", "me", muted = true)))
        val signals = listOf(
            ConnectionSignal("1", "ana", "me", "Hola"),
            ConnectionSignal("2", "bo", "me", "Hola"),
            ConnectionSignal("3", "bo", "me", "Leído", readAt = "2026-11-16T10:00:00Z")
        )
        assertEquals(listOf("2"), controls.notifiable(signals).map { it.id })
    }
}
