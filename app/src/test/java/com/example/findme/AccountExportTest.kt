package com.example.findme

import com.example.findme.data.model.*
import com.example.findme.domain.model.AccountExport
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AccountExportTest {
    private val me = AccountProfile("me", "Jhon", "America/Lima", "jhon")
    private val friend = AccountProfile("friend", "Ana", "Europe/Madrid", "ana")

    @Test fun exportsOwnDataAndNamesInsteadOfInternalIds() {
        val text = AccountExport.build(
            profile = me,
            schedules = listOf(
                ScheduleDto("2", "me", 2, "09:00:00", "10:00:00", "Clase", "Aula 4"),
                ScheduleDto("1", "me", 1, "08:00:00", "09:00:00", "Turno", "", "work"),
                ScheduleDto("3", "friend", 1, "08:00:00", "09:00:00", "Ajeno", "")
            ),
            statuses = listOf(StatusOverrideDto("me", "me", true, "En la biblioteca", null, "2026-11-16T12:00:00Z"),
                StatusOverrideDto("friend", "friend", true, "Privado", null, "2026-11-16T12:00:00Z")),
            quickMessages = listOf(QuickMessage("q", "me", "Llegué", "Ya estoy aquí")),
            connections = listOf(PersonConnection("c", "friend", "me")),
            people = listOf(me, friend),
            received = listOf(ConnectionSignal("s", "friend", "me", "Hola", "2026-11-16T10:00:00Z")),
            exportedAt = "2026-11-16T11:00:00Z"
        )
        val json = Json.parseToJsonElement(text).jsonObject
        assertEquals("jhon", json["profile"]!!.jsonObject["username"]!!.jsonPrimitive.content)
        val schedule = json["schedule"]!!.jsonArray.map { it.jsonObject["title"]!!.jsonPrimitive.content }
        assertEquals(listOf("Turno", "Clase"), schedule)
        assertEquals(1, json["shared_status"]!!.jsonArray.size)
        assertEquals("Ana", json["connections"]!!.jsonArray.single().jsonObject["person"]!!.jsonPrimitive.content)
        assertEquals("Ana", json["received_messages"]!!.jsonArray.single().jsonObject["from"]!!.jsonPrimitive.content)
        for (internal in listOf("\"friend\"", "\"me\"", "Ajeno", "Privado")) assertFalse("Leaked $internal", text.contains(internal))
    }
}
