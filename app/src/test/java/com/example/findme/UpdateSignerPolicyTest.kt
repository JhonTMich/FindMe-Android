package com.example.findme

import com.example.findme.util.SignerSet
import com.example.findme.util.UpdateSignerPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateSignerPolicyTest {
    private val old = "a".repeat(64)
    private val new = "b".repeat(64)
    private val foreign = "c".repeat(64)

    @Test fun acceptsTheSameSigner() {
        assertTrue(UpdateSignerPolicy.accepts(SignerSet(setOf(old), setOf(old)), SignerSet(setOf(old), setOf(old))))
        assertTrue(UpdateSignerPolicy.accepts(SignerSet(setOf(old)), SignerSet(setOf(old))))
    }

    @Test fun acceptsARotationWhoseLineageIncludesTheInstalledKey() {
        assertTrue(UpdateSignerPolicy.accepts(SignerSet(setOf(new), setOf(old, new)), SignerSet(setOf(old), setOf(old))))
        // Later updates are signed only with the new key, which is now the installed one.
        assertTrue(UpdateSignerPolicy.accepts(SignerSet(setOf(new), setOf(old, new)), SignerSet(setOf(new), setOf(old, new))))
    }

    @Test fun rejectsForeignOrUnprovenSigners() {
        assertFalse(UpdateSignerPolicy.accepts(SignerSet(setOf(foreign), setOf(foreign)), SignerSet(setOf(old), setOf(old))))
        assertFalse(UpdateSignerPolicy.accepts(SignerSet(setOf(new)), SignerSet(setOf(old))))
        assertFalse(UpdateSignerPolicy.accepts(SignerSet(setOf(new), setOf(foreign, new)), SignerSet(setOf(old))))
        assertFalse(UpdateSignerPolicy.accepts(SignerSet(emptySet()), SignerSet(setOf(old))))
        assertFalse(UpdateSignerPolicy.accepts(SignerSet(setOf(old)), SignerSet(emptySet())))
        // Rolling back to an older key is not an accepted upgrade.
        assertFalse(UpdateSignerPolicy.accepts(SignerSet(setOf(old), setOf(old)), SignerSet(setOf(new), setOf(old, new))))
    }
}
