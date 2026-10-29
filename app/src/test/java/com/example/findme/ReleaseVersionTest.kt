package com.example.findme

import com.example.findme.data.repository.ReleaseVersion
import org.junit.Assert.*
import org.junit.Test

class ReleaseVersionTest {
    @Test fun comparesNumericComponentsInsteadOfText() {
        assertTrue(ReleaseVersion.isNewer("v1.10.0", "1.9.9"))
        assertFalse(ReleaseVersion.isNewer("v1.2.0", "1.10.0"))
    }
    @Test fun ignoresPrefixesPaddingAndBuildMetadata() {
        assertFalse(ReleaseVersion.isNewer("V1.0.0", "1.0"))
        assertTrue(ReleaseVersion.isNewer("v1.0.1+42", "1.0"))
    }
    @Test fun rejectsPreReleasesInvalidTagsAndDowngrades() {
        listOf("v2.0-beta", "release-new", "", "99999999999999999.0").forEach {
            assertFalse(ReleaseVersion.isNewer(it, "1.0"))
        }
        assertFalse(ReleaseVersion.isNewer("0.9", "1.0"))
        assertFalse(ReleaseVersion.isNewer("1.0.0", "1.0.0"))
    }
}
