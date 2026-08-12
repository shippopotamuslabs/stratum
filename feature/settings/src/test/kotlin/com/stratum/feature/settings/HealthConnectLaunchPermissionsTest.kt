package com.stratum.feature.settings

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class HealthConnectLaunchPermissionsTest {

    @Test
    @DisplayName("opt-in is true when any requested permission is granted")
    fun optInWhenAnyRequestedGranted() {
        val requested = setOf("write", "read_steps")
        assertTrue(healthConnectOptInFromGrants(setOf("read_steps"), requested))
        assertTrue(healthConnectOptInFromGrants(setOf("write"), requested))
        assertTrue(healthConnectOptInFromGrants(setOf("write", "other"), requested))
    }

    @Test
    @DisplayName("opt-in is false when the sheet grants nothing requested")
    fun optInFalseWhenNoneGranted() {
        val requested = setOf("write", "read_steps")
        assertFalse(healthConnectOptInFromGrants(emptySet(), requested))
        assertFalse(healthConnectOptInFromGrants(setOf("unrelated"), requested))
    }
}
