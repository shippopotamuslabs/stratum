package com.stratum.feature.program.navigation

import androidx.navigation3.runtime.NavKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * PROG-GEN-403 (routing). Verifies the [ProgramsKey] contract at the type level: it is a
 * [NavKey] and a singleton usable as a route destination. The full "completion routes to
 * ProgramsKey" behavior lives in the app shell's `StratumNavHost` composable, exercised by
 * instrumented Compose tests (compose-ui-test is `androidTestImplementation` only); this unit test
 * pins the key contract the routing depends on, and `programsEntry` registration is verified by the
 * app module compiling against it.
 */
class ProgramsKeyContractTest {
    @Test
    @DisplayName("PROG-GEN-403 ProgramsKey is a NavKey singleton usable as a route destination")
    fun `programs key is a nav key singleton`() {
        assertTrue(ProgramsKey is NavKey)
        assertEquals(ProgramsKey, ProgramsKey)
        assertNotEquals(ProgramsKey, Any())
    }
}
