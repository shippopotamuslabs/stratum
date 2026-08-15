package com.stratum.feature.settings

import org.junit.jupiter.api.Assertions.assertEquals
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

    @Test
    @DisplayName("write vs read grant helpers split Health Connect capabilities")
    fun writeAndReadGrantHelpers() {
        val writeOnly = setOf(HealthConnectLaunchPermissions.writeExercise)
        assertTrue(healthConnectWriteGranted(writeOnly))
        assertFalse(healthConnectReadsGranted(writeOnly))

        val readsOnly = setOf(HealthConnectLaunchPermissions.readSteps)
        assertFalse(healthConnectWriteGranted(readsOnly))
        assertTrue(healthConnectReadsGranted(readsOnly))
    }

    @Test
    @DisplayName("permission focus maps to write, reads, or the full set")
    fun permissionFocusRequestedSet() {
        assertEquals(
            HealthConnectLaunchPermissions.all,
            HealthConnectPermissionFocus.All.requestedPermissions(),
        )
        assertEquals(
            setOf(HealthConnectLaunchPermissions.writeExercise),
            HealthConnectPermissionFocus.Write.requestedPermissions(),
        )
        assertEquals(
            HealthConnectLaunchPermissions.reads,
            HealthConnectPermissionFocus.Reads.requestedPermissions(),
        )
    }
}
