package com.stratum.application.lift

import com.stratum.core.testing.lift.FakeWeightDisplayPreferencesRepository
import com.stratum.core.testing.logging.FakeLoggingPort
import com.stratum.domain.lift.Weight
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.coroutines.cancellation.CancellationException

class WeightDisplayPreferencesUseCasesTest {

    private val fake = FakeWeightDisplayPreferencesRepository()

    @Test
    @DisplayName("setDisplayUnit writes the unit and returns Success (STR-243)")
    fun setUnitSuccess() = runTest {
        val result = SetWeightDisplayUnitUseCase(fake, FakeLoggingPort())(Weight.WeightUnit.Kg)
        assertEquals(WeightDisplayPreferenceResult.Success, result)
        assertEquals(Weight.WeightUnit.Kg, fake.current.displayUnit)
    }

    @Test
    @DisplayName("setDisplayUnit returns PersistenceFailed when the write throws (STR-243)")
    fun setUnitPersistenceFailure() = runTest {
        val throwing =
            object : WeightDisplayPreferencesRepository by fake {
                override suspend fun setDisplayUnit(unit: Weight.WeightUnit) {
                    throw RuntimeException("store unavailable")
                }
            }
        val result = SetWeightDisplayUnitUseCase(throwing, FakeLoggingPort())(Weight.WeightUnit.Kg)
        assertEquals(WeightDisplayPreferenceResult.PersistenceFailed, result)
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
        val subject = SetWeightDisplayUnitUseCase(cancelling, FakeLoggingPort())
        assertThrows(CancellationException::class.java) {
            runBlocking { subject(Weight.WeightUnit.Kg) }
        }
    }
}
