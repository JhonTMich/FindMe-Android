package com.example.findme.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileDto(
    val id: String = "",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("family_code") val familyCode: String = "",
    @SerialName("created_at") val createdAt: String? = null
)
