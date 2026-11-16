package com.example.findme

import com.example.findme.data.local.AccountCache
import com.example.findme.data.local.AccountSnapshot
import com.example.findme.data.model.*
import org.junit.Assert.*
import org.junit.Test

class AccountCacheTest {
    private val me = AccountProfile("me", "Jhon", "America/Lima", "jhon")
    private val snapshot = AccountSnapshot("me", me, listOf(me), listOf(ScheduleDto("1", "me", 1, "08:00:00", "09:00:00", "Clase", "")),
        emptyList(), listOf(PersonConnection("c", "friend", "me")), emptyList(), emptyList(), "2026-11-16T10:00:00Z")

    @Test fun roundTripsTheSnapshotForItsOwner() {
        assertEquals(snapshot, AccountCache.decode(AccountCache.encode(snapshot), "me"))
    }

    @Test fun neverReturnsAnotherAccountsDataOrCorruptContent() {
        assertNull(AccountCache.decode(AccountCache.encode(snapshot), "someone-else"))
        assertNull(AccountCache.decode(AccountCache.encode(snapshot.copy(profile = me.copy(id = "other"))), "me"))
        assertNull(AccountCache.decode("{not json", "me"))
        assertNull(AccountCache.decode(null, "me"))
    }
}
