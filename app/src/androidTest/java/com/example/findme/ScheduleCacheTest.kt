package com.example.findme

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.findme.data.local.AppDatabase
import com.example.findme.data.local.ScheduleEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ScheduleCacheTest {
    private fun activity(id: String, user: String) = ScheduleEntity(id, user, 1, "08:00", "09:00", "Actividad", "Aula", "university", "Presencial")
    @Test fun replacementRemovesDeletedActivitiesAndPreservesOtherUsers() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val dao = db.scheduleDao()
            dao.insertSchedules(listOf(activity("old", "first"), activity("other", "second")))
            dao.replaceUserSchedules("first", listOf(activity("new", "first")))
            assertEquals(listOf("new"), dao.getAllSchedulesFlow("first").first().map { it.id })
            assertEquals(listOf("other"), dao.getAllSchedulesFlow("second").first().map { it.id })
            dao.replaceUserSchedules("first", emptyList())
            assertTrue(dao.getAllSchedulesFlow("first").first().isEmpty())
            assertEquals(1, dao.getAllSchedulesFlow("second").first().size)
        } finally { db.close() }
    }
}
