package com.stratum.core.infrastructure.inference

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FirebasePlayEntitlementSyncAdapterTest {
    @Test
    @DisplayName("parseResponse maps entitled boolean from callable payload")
    fun parseEntitledTrue() {
        assertTrue(parsePlayEntitlementSyncResponse(mapOf("entitled" to true)))
    }

    @Test
    @DisplayName("parseResponse maps entitled false")
    fun parseEntitledFalse() {
        assertFalse(parsePlayEntitlementSyncResponse(mapOf("entitled" to false)))
    }

    @Test
    @DisplayName("parseResponse rejects missing entitled")
    fun parseMissingEntitled() {
        val error = assertThrows<IllegalStateException> {
            parsePlayEntitlementSyncResponse(emptyMap<String, Any>())
        }
        assertEquals("syncPlayEntitlement response missing entitled", error.message)
    }
}
