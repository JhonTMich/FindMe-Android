package com.example.findme

import com.example.findme.data.model.ScheduleDto
import com.example.findme.data.model.StatusOverrideDto
import com.example.findme.data.repository.StatusRepository
import com.example.findme.domain.model.UserCurrentStatus
import java.time.ZonedDateTime
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Assert.*
import org.junit.Test

class StatusClockTest {
    @Test fun timeTransitionsAreAppliedWithoutAnyDatabaseEvents() = runBlocking {
        withTimeout(5_000) {
            val ticks = Channel<ZonedDateTime>()
            val results = Channel<UserCurrentStatus>()
            val activity = ScheduleDto(id = "thursday", dayOfWeek = 4, startTime = "09:00:00", endTime = "18:00:00",
                title = "Actividad del jueves", location = "Aula")
            val travel = StatusOverrideDto(isActive = true, customLocation = "En camino a casa", expiresAt = "2026-10-29T23:15:00Z")
            // These streams stay open without emitting changes, like an idle Realtime connection.
            val schedules = MutableStateFlow(listOf(activity))
            val announcements = MutableStateFlow(listOf(travel))
            val collector = launch {
                StatusRepository.observeStatusWithClock("Persona", schedules, announcements, 4, ticks.receiveAsFlow())
                    .collect { results.send(it) }
            }
            val base = ZonedDateTime.parse("2026-10-29T17:00:00-05:00[America/Lima]")
            try {
                ticks.send(base.withHour(8).withMinute(59))
                assertTrue(results.receive() is UserCurrentStatus.Override)
                ticks.send(base.withHour(9))
                assertTrue(results.receive() is UserCurrentStatus.InClass)
                ticks.send(base)
                assertEquals(60L, (results.receive() as UserCurrentStatus.InClass).remainingMinutes)
                ticks.send(base.withHour(18))
                assertTrue(results.receive() is UserCurrentStatus.Override)
                ticks.send(base.withHour(18).withMinute(15))
                assertTrue(results.receive() is UserCurrentStatus.FreeTime)
            } finally {
                collector.cancelAndJoin()
                ticks.close()
                results.close()
            }
        }
    }
}
