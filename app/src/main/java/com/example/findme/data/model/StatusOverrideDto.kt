package com.example.findme.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StatusOverrideDto(
    val id: String = "",
    @SerialName("user_id") val userId: String? = null,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("custom_location") val customLocation: String,
    @SerialName("custom_message") val customMessage: String? = null,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("created_at") val createdAt: String? = null
)
