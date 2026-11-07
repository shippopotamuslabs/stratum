package com.stratum.application.lift

import com.stratum.core.testing.onboarding.FakeEquipmentProposalPreferences
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.coroutines.cancellation.CancellationException

class EquipmentProposalPreferencesUseCasesTest {

    private val fake = FakeEquipmentProposalPreferences()

    @Test
    @DisplayName("setEnabled writes the flag and returns Success (STR-243)")
    fun setEnabledSuccess() = runTest {
        val result = SetEquipmentProposalsEnabledUseCase(fake)(false)
        assertEquals(EquipmentProposalPreferenceResult.Success, result)
        assertTrue(!fake.isEnabled())
    }

    @Test
    @DisplayName("setEnabled returns PersistenceFailed when the write throws (STR-243)")
    fun setEnabledPersistenceFailure() = runTest {
        val failure = RuntimeException("store unavailable")
        val throwing =
            object : EquipmentProposalPreferences by fake {
                override suspend fun setEnabled(enabled: Boolean) {
                    throw failure
                }
            }
        val result = SetEquipmentProposalsEnabledUseCase(throwing)(false)
        val failed = result as EquipmentProposalPreferenceResult.PersistenceFailed
        assertEquals(1, failed.diagnostics.size)
        val diagnostic = failed.diagnostics.single()
        assertEquals("SetEquipmentProposalsEnabled", diagnostic.tag)
        assertEquals("Failed to set equipment proposals enabled: store unavailable", diagnostic.message)
        assertSame(failure, diagnostic.cause)
    }

    @Test
    @DisplayName("setEnabled propagates CancellationException instead of capturing it (STR-243)")
    fun setEnabledCancellationPropagates() {
        val cancelling =
            object : EquipmentProposalPreferences by fake {
                override suspend fun setEnabled(enabled: Boolean) {
                    throw CancellationException("cancelled")
                }
            }
        val subject = SetEquipmentProposalsEnabledUseCase(cancelling)
        assertThrows(CancellationException::class.java) {
            runBlocking { subject(false) }
        }
    }
}
