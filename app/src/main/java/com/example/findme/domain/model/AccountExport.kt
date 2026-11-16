package com.example.findme.domain.model

import com.example.findme.data.model.*
import kotlinx.serialization.json.*

/** A readable copy of everything the account owns or receives, for the "Export my data" option. */
object AccountExport {
    private val json = Json { prettyPrint = true }

    fun build(
        profile: AccountProfile,
        schedules: List<ScheduleDto>,
        statuses: List<StatusOverrideDto>,
        quickMessages: List<QuickMessage>,
        connections: List<PersonConnection>,
        people: List<AccountProfile>,
        received: List<ConnectionSignal>,
        exportedAt: String
    ): String {
        val names = people.associate { it.id to it.displayName }
        return json.encodeToString(JsonObject.serializer(), buildJsonObject {
            put("exported_at", exportedAt)
            putJsonObject("profile") {
                put("display_name", profile.displayName)
                put("username", profile.username)
                put("timezone", profile.timezone)
            }
            putJsonArray("schedule") {
                schedules.filter { it.userId == profile.id }.sortedWith(compareBy({ it.dayOfWeek }, { it.startTime })).forEach { row ->
                    addJsonObject {
                        put("day_of_week", row.dayOfWeek); put("start_time", row.startTime); put("end_time", row.endTime)
                        put("ends_next_day", row.endsNextDay); put("title", row.title); put("location", row.location)
                        put("category", row.category); put("modality", row.modality)
                    }
                }
            }
            putJsonArray("shared_status") {
                statuses.filter { it.userId == profile.id }.forEach { status ->
                    addJsonObject { put("text", status.customLocation); put("message", status.customMessage); put("expires_at", status.expiresAt) }
                }
            }
            putJsonArray("quick_messages") {
                quickMessages.forEach { addJsonObject { put("label", it.label); put("message", it.message) } }
            }
            putJsonArray("connections") {
                connections.forEach { connection ->
                    addJsonObject { put("person", names[connection.otherPerson(profile.id)] ?: "Persona sin nombre visible") }
                }
            }
            putJsonArray("received_messages") {
                received.forEach { signal ->
                    addJsonObject { put("from", names[signal.senderId] ?: "Tu gente"); put("message", signal.message); put("sent_at", signal.createdAt) }
                }
            }
        })
    }
}
