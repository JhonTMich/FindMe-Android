package com.example.findme.domain.model

/** Accessed on the ViewModel's main dispatcher. Reads may overlap a schedule write. */
internal class ScheduleSyncGuard {
    private var revision = 0L
    private var writing = false

    fun snapshotRevision(): Long = revision

    fun beginWrite() {
        writing = true
        revision++
    }

    fun finishWrite() {
        writing = false
        revision++
    }

    fun canApplySnapshot(startRevision: Long): Boolean = !writing && revision == startRevision
}
