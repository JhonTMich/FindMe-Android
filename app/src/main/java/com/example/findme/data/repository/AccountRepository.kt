package com.example.findme.data.repository

import com.example.findme.data.model.*
import com.example.findme.data.network.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** The new account model is isolated from the legacy family-code tables. */
class AccountRepository {
    private val client get() = SupabaseClientProvider.client
    val auth get() = client.auth
    fun userId(): String = requireNotNull(auth.currentUserOrNull()?.id) { "Inicia sesión para continuar." }

    suspend fun signIn(email: String, password: String) {
        auth.signInWith(Email) { this.email = email.trim(); this.password = password }
    }

    suspend fun register(name: String, email: String, password: String) {
        require(name.trim().length in 1..80) { "Introduce un nombre de hasta 80 caracteres." }
        require(password.length >= 8) { "La contraseña debe tener al menos 8 caracteres." }
        auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
            data = buildJsonObject {
                put("display_name", name.trim())
                put("timezone", ZoneId.systemDefault().id)
            }
        }
    }

    suspend fun ensureProfile(): AccountProfile {
        val id = userId()
        val existing = client.postgrest["account_profiles"].select { filter { eq("id", id) } }
            .decodeList<AccountProfile>().firstOrNull()
        if (existing != null) return existing
        val user = requireNotNull(auth.currentUserOrNull())
        val name = user.userMetadata?.get("display_name")?.jsonPrimitive?.contentOrNull.orEmpty()
            .ifBlank { "Mi cuenta" }.take(80)
        val profile = AccountProfile(id, name, ZoneId.systemDefault().id)
        client.postgrest["account_profiles"].insert(profile)
        return profile
    }

    suspend fun profiles() = client.postgrest["account_profiles"].select().decodeList<AccountProfile>()
    suspend fun connections() = client.postgrest["person_connections"].select().decodeList<PersonConnection>()
    suspend fun schedules(id: String) = client.postgrest["personal_schedule"].select { filter { eq("user_id", id) } }.decodeList<ScheduleDto>()
    suspend fun overrides(id: String) = client.postgrest["personal_status"].select { filter { eq("user_id", id) } }.decodeList<StatusOverrideDto>()

    suspend fun saveSchedule(schedule: ScheduleDto) {
        require(schedule.userId == userId())
        client.postgrest["personal_schedule"].upsert(schedule)
    }

    suspend fun deleteSchedule(id: String) {
        client.postgrest["personal_schedule"].delete { filter { eq("id", id); eq("user_id", userId()) } }
    }

    suspend fun setStatus(text: String, minutes: Long) {
        require(text.trim().length in 1..120)
        require(minutes in 1..1440)
        val id = userId()
        client.postgrest["personal_status"].upsert(StatusOverrideDto(
            id = id, userId = id, isActive = true, customLocation = text.trim(),
            expiresAt = Instant.now().plusSeconds(minutes * 60).toString()
        ))
    }

    suspend fun clearStatus() {
        client.postgrest["personal_status"].delete { filter { eq("user_id", userId()) } }
    }

    suspend fun createInvitation(): ConnectionInvitation {
        val invitation = ConnectionInvitation(userId(), UUID.randomUUID().toString().replace("-", ""),
            Instant.now().plusSeconds(86400).toString())
        client.postgrest["connection_invitations"].upsert(invitation)
        return invitation
    }

    suspend fun acceptInvitation(code: String) {
        require(code.trim().length == 32) { "Introduce el código completo de invitación." }
        client.postgrest.rpc("accept_connection_invitation", buildJsonObject { put("invitation_code", code.trim().lowercase()) })
    }

    suspend fun disconnect(id: String) {
        client.postgrest["person_connections"].delete { filter { eq("id", id) } }
    }

    suspend fun quickMessages() = client.postgrest["quick_messages"].select { filter { eq("user_id", userId()) } }.decodeList<QuickMessage>()
    suspend fun saveQuickMessage(label: String, message: String) {
        require(label.trim().length in 1..30 && message.trim().length in 1..240) { "Completa el nombre y el mensaje (máximo 240 caracteres)." }
        client.postgrest["quick_messages"].insert(QuickMessage(UUID.randomUUID().toString(), userId(), label.trim(), message.trim()))
    }
    suspend fun deleteQuickMessage(id: String) {
        client.postgrest["quick_messages"].delete { filter { eq("id", id); eq("user_id", userId()) } }
    }
    suspend fun sendSignal(recipientId: String, message: String) {
        client.postgrest["connection_signals"].insert(buildJsonObject {
            put("id", UUID.randomUUID().toString())
            put("sender_id", userId())
            put("recipient_id", recipientId)
            put("message", message)
        })
    }
    suspend fun receivedSignals() = client.postgrest["connection_signals"].select {
        filter { eq("recipient_id", userId()) }
        order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
        limit(20)
    }.decodeList<ConnectionSignal>()
}
