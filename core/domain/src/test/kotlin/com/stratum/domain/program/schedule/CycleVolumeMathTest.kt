package com.stratum.domain.program.schedule

import com.stratum.domain.onboarding.MuscleGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CycleVolumeMathTest {
    @Test
    @DisplayName("weeklyEquivalentFactor is identity at cycleLength 7")
    fun factor_sevenIsIdentity() {
        assertEquals(1.0, CycleVolumeMath.weeklyEquivalentFactor(7), 1e-9)
        assertEquals(10.0, CycleVolumeMath.toWeeklyEquivalent(10.0, 7), 1e-9)
    }

    @Test
    @DisplayName("weeklyEquivalentFactor scales 8-day cycle by 7/8")
    fun factor_eightDay() {
        assertEquals(7.0 / 8.0, CycleVolumeMath.weeklyEquivalentFactor(8), 1e-9)
        assertEquals(14.0 * 7.0 / 8.0, CycleVolumeMath.toWeeklyEquivalent(14.0, 8), 1e-9)
    }

    @Test
    @DisplayName("map overload scales each muscle volume")
    fun mapScale() {
        val cycle =
            mapOf(
                MuscleGroup.Chest to 16.0,
                MuscleGroup.Back to 20.0,
            )
        val weekly = CycleVolumeMath.toWeeklyEquivalent(cycle, cycleLength = 8)
        assertEquals(16.0 * 7.0 / 8.0, weekly.getValue(MuscleGroup.Chest), 1e-9)
        assertEquals(20.0 * 7.0 / 8.0, weekly.getValue(MuscleGroup.Back), 1e-9)
    }

    @Test
    @DisplayName("non-positive cycleLength is rejected")
    fun rejectsNonPositive() {
        assertThrows<IllegalArgumentException> {
            CycleVolumeMath.weeklyEquivalentFactor(0)
        }
    }
}
