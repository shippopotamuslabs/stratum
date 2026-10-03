package com.stratum.domain.telemetry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ResolveAppVersionTest {
    @Test
    @DisplayName("keeps real versions and falls back for null or blank versions")
    fun versionFallback() {
        assertEquals("1.2.3", resolveAppVersion("1.2.3"))
        assertEquals(ClientLogBatch.APP_VERSION_UNKNOWN, resolveAppVersion(null))
        assertEquals(ClientLogBatch.APP_VERSION_UNKNOWN, resolveAppVersion(""))
        assertEquals(ClientLogBatch.APP_VERSION_UNKNOWN, resolveAppVersion("  "))
    }
}
