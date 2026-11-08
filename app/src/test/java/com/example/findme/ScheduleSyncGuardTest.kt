package com.example.findme

import com.example.findme.domain.model.ScheduleSyncGuard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleSyncGuardTest {
    @Test fun aSnapshotStartedBeforeDeletionCannotRestoreTheDeletedActivity() {
        val sync = ScheduleSyncGuard()
        val readBeforeDelete = sync.snapshotRevision()
        sync.beginWrite()
        sync.finishWrite()
        assertFalse(sync.canApplySnapshot(readBeforeDelete))
        assertTrue(sync.canApplySnapshot(sync.snapshotRevision()))
    }

    @Test fun readsStartedDuringCreationCannotReplaceTheConfirmedActivity() {
        val sync = ScheduleSyncGuard()
        sync.beginWrite()
        val readDuringSave = sync.snapshotRevision()
        assertFalse(sync.canApplySnapshot(readDuringSave))
        sync.finishWrite()
        assertFalse(sync.canApplySnapshot(readDuringSave))
        assertTrue(sync.canApplySnapshot(sync.snapshotRevision()))
    }

    @Test fun anotherWriteInvalidatesAnOtherwiseFreshRead() {
        val sync = ScheduleSyncGuard()
        sync.beginWrite()
        sync.finishWrite()
        val read = sync.snapshotRevision()
        sync.beginWrite()
        assertFalse(sync.canApplySnapshot(read))
        sync.finishWrite()
        assertFalse(sync.canApplySnapshot(read))
    }
}
