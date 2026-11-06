package com.stratum.application.workout

import com.stratum.core.testing.workout.FakeRepRangeShiftProposalPreferences
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.coroutines.cancellation.CancellationException

class RepRangeShiftPreferencesUseCasesTest {

    private val fake = FakeRepRangeShiftProposalPreferences()

    @Test
    @DisplayName("setEnabled writes the flag and returns Success (STR-243)")
    fun setEnabledSuccess() = runTest {
        val result = SetRepRangeShiftProposalsEnabledUseCase(fake)(false)
        assertEquals(RepRangeShiftPreferenceResult.Success, result)
        assertTrue(!fake.isEnabled())
    }

    @Test
    @DisplayName("setEnabled returns PersistenceFailed when the write throws (STR-243)")
    fun setEnabledPersistenceFailure() = runTest {
        val throwing =
            object : RepRangeShiftProposalPreferences by fake {
                override suspend fun setEnabled(enabled: Boolean) {
                    throw RuntimeException("store unavailable")
                }
            }
        val result = SetRepRangeShiftProposalsEnabledUseCase(throwing)(false)
        // STR-237: the failure record rides the branch exactly once.
        val failed = result as RepRangeShiftPreferenceResult.PersistenceFailed
        val diagnostic = failed.diagnostics.single()
        assertEquals("SetRepRangeShiftEnabled", diagnostic.tag)
        assertEquals(
            "Failed to set rep-range shift proposals enabled: store unavailable",
            diagnostic.message,
        )
        assertEquals("store unavailable", diagnostic.cause.message)
    }

    @Test
    @DisplayName("setEnabled propagates CancellationException instead of capturing it (STR-243)")
    fun setEnabledCancellationPropagates() {
        val cancelling =
            object : RepRangeShiftProposalPreferences by fake {
                override suspend fun setEnabled(enabled: Boolean) {
                    throw CancellationException("cancelled")
                }
            }
        val subject = SetRepRangeShiftProposalsEnabledUseCase(cancelling)
        assertThrows(CancellationException::class.java) {
            runBlocking { subject(false) }
        }
    }
}
