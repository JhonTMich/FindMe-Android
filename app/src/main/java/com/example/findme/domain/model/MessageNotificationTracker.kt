package com.example.findme.domain.model

import com.example.findme.data.model.ConnectionSignal

/** Seed the initial inbox quietly; changing preferences must not replay old messages. */
class MessageNotificationTracker {
    private var owner: String? = null
    private var seen: Set<String>? = null

    fun update(accountId: String, messages: List<ConnectionSignal>, enabled: Boolean): List<ConnectionSignal> {
        val previous = if (owner == accountId) seen else null
        val incoming = if (enabled && previous != null) messages.filter { it.id !in previous } else emptyList()
        owner = accountId
        seen = messages.map { it.id }.toSet()
        return incoming
    }
}
