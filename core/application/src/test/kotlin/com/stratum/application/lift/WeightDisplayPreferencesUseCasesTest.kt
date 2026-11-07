package com.stratum.application.lift

import com.stratum.core.testing.lift.FakeWeightDisplayPreferencesRepository
import com.stratum.domain.lift.Weight
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.coroutines.cancellation.CancellationException

class WeightDisplayPreferencesUseCasesTest {

    private val fake = FakeWeightDisplayPreferencesRepository()

    @Test
    @DisplayName("setDisplayUnit writes the unit and returns Success (STR-243)")
    fun setUnitSuccess() = runTest {
        val result = SetWeightDisplayUnitUseCase(fake)(Weight.WeightUnit.Kg)
        assertEquals(WeightDisplayPreferenceResult.Success, result)
        assertEquals(Weight.WeightUnit.Kg, fake.current.displayUnit)
    }

    @Test
    @DisplayName("setDisplayUnit returns PersistenceFailed when the write throws (STR-243)")
    fun setUnitPersistenceFailure() = runTest {
        val failure = RuntimeException("store unavailable")
        val throwing =
            object : WeightDisplayPreferencesRepository by fake {
                override suspend fun setDisplayUnit(unit: Weight.WeightUnit) {
                    throw failure
                }
            }
        val result = SetWeightDisplayUnitUseCase(throwing)(Weight.WeightUnit.Kg)
        val failed = result as WeightDisplayPreferenceResult.PersistenceFailed
        assertEquals(1, failed.diagnostics.size)
        val diagnostic = failed.diagnostics.single()
        assertEquals("SetWeightDisplayUnit", diagnostic.tag)
        assertEquals("Failed to set weight display unit: store unavailable", diagnostic.message)
        assertSame(failure, diagnostic.cause)
    }

    @Test
    @DisplayName("setDisplayUnit propagates CancellationException instead of capturing it (STR-243)")
    fun setUnitCancellationPropagates() {
        val cancelling =
            object : WeightDisplayPreferencesRepository by fake {
                override suspend fun setDisplayUnit(unit: Weight.WeightUnit) {
                    throw CancellationException("cancelled")
                }
            }
        val subject = SetWeightDisplayUnitUseCase(cancelling)
        assertThrows(CancellationException::class.java) {
            runBlocking { subject(Weight.WeightUnit.Kg) }
        }
    }
}
