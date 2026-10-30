package com.example.findme.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AccountProfile(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val timezone: String = "America/Lima"
)

@Serializable
data class PersonConnection(
    val id: String,
    @SerialName("person_a") val personA: String,
    @SerialName("person_b") val personB: String
) {
    fun otherPerson(userId: String) = if (personA == userId) personB else personA
}

@Serializable
data class ConnectionInvitation(
    @SerialName("owner_id") val ownerId: String,
    val code: String,
    @SerialName("expires_at") val expiresAt: String
)

@Serializable
data class QuickMessage(
    val id: String,
    @SerialName("user_id") val userId: String,
    val label: String,
    val message: String
)

@Serializable
data class ConnectionSignal(
    val id: String,
    @SerialName("sender_id") val senderId: String,
    @SerialName("recipient_id") val recipientId: String,
    val message: String,
    @SerialName("created_at") val createdAt: String? = null
)
