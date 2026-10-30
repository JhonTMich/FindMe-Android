package com.example.findme

import com.example.findme.data.model.ConnectionSignal
import com.example.findme.domain.model.MessageNotificationTracker
import org.junit.Assert.*
import org.junit.Test

class MessageNotificationTrackerTest {
    private fun message(id: String) = ConnectionSignal(id, "sender", "recipient", "Message")
    @Test fun initialHistoryIsQuietAndNewMessagesNotifyOnlyOnce() {
        val tracker = MessageNotificationTracker()
        val first = message("first")
        val second = message("second")
        assertTrue(tracker.update("account", listOf(first), true).isEmpty())
        assertEquals(listOf(second), tracker.update("account", listOf(second, first), true))
        assertTrue(tracker.update("account", listOf(second, first), true).isEmpty())
    }
    @Test fun enablingNotificationsDoesNotReplayMessagesReceivedWhileMuted() {
        val tracker = MessageNotificationTracker()
        tracker.update("account", emptyList(), false)
        val messages = listOf(message("muted"))
        assertTrue(tracker.update("account", messages, false).isEmpty())
        assertTrue(tracker.update("account", messages, true).isEmpty())
    }
    @Test fun changingAccountsSeedsASeparateQuietInbox() {
        val tracker = MessageNotificationTracker()
        tracker.update("first-account", listOf(message("first")), true)
        assertTrue(tracker.update("second-account", listOf(message("second")), true).isEmpty())
    }
}
