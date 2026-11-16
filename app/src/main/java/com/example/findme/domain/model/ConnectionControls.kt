package com.example.findme.domain.model

import com.example.findme.data.model.AccountProfile
import com.example.findme.data.model.ConnectionPreference
import com.example.findme.data.model.ConnectionSignal
import com.example.findme.data.model.PersonConnection

/** The signed-in person's own choices about each connection: nickname, sharing and silence. */
class ConnectionControls(
    private val ownerId: String,
    private val connections: List<PersonConnection>,
    private val preferences: List<ConnectionPreference>
) {
    private val byPerson: Map<String, ConnectionPreference> = connections.associate { connection ->
        connection.otherPerson(ownerId) to (preferences.find { it.connectionId == connection.id && it.ownerId == ownerId }
            ?: ConnectionPreference(connection.id, ownerId))
    }

    /** Defaults when the person is not (or no longer) connected. */
    fun forPerson(personId: String): ConnectionPreference? = byPerson[personId]

    fun nameOf(person: AccountProfile): String =
        byPerson[person.id]?.nickname?.trim()?.takeIf { it.isNotEmpty() } ?: person.displayName

    fun isMuted(personId: String): Boolean = byPerson[personId]?.muted == true

    /** New messages worth an alert: not from a silenced person and not already read elsewhere. */
    fun notifiable(signals: List<ConnectionSignal>): List<ConnectionSignal> =
        signals.filter { it.readAt == null && !isMuted(it.senderId) }
}
