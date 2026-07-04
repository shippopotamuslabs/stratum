package com.stratum.domain.program.generation.reference

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Golden contract for the class-aware recovery model (literature matrix §4.1).
 * Submaximal rows use [minRealizedRir] = null; failure rows document deferred wiring.
 */
class RecoveryModelTest {

    @Test
    @DisplayName("isolation low dose allows next day (24h, gap 1)")
    fun isolationLowDoseAllowsNextDay() {
        assertGap(1, LiftStressClass.Isolation, 3.0)
        assertHours(24, LiftStressClass.Isolation, 2.0)
    }

    @Test
    @DisplayName("isolation moderate dose requires two days (48h, gap 2)")
    fun isolationModerateDoseRequiresTwoDays() {
        assertGap(2, LiftStressClass.Isolation, 4.0)
        assertGap(2, LiftStressClass.Isolation, 7.0)
    }

    @Test
    @DisplayName("isolation high dose requires three days (72h, gap 3)")
    fun isolationHighDoseRequiresThreeDays() {
        assertGap(3, LiftStressClass.Isolation, 8.0)
    }

    @Test
    @DisplayName("failure adder deferred: submaximal gap unchanged; failure row documented")
    fun failureAdderDeferredReturnsSubmaximalGap() {
        val stress = MuscleSessionStress(5.0, LiftStressClass.Isolation)
        assertEquals(2, minimumRecoveryCalendarGap(stress, minRealizedRir = null))
        assertEquals(3, minimumRecoveryCalendarGap(stress, minRealizedRir = 0))
    }

    @Test
    @DisplayName("upper compound low dose requires two days (48h, gap 2)")
    fun upperCompoundLowDoseRequiresTwoDays() {
        assertGap(2, LiftStressClass.UpperCompound, 3.0)
    }

    @Test
    @DisplayName("upper compound moderate dose requires three days (72h, gap 3)")
    fun upperCompoundModerateDoseRequiresThreeDays() {
        assertGap(3, LiftStressClass.UpperCompound, 5.0)
        assertGap(3, LiftStressClass.UpperCompound, 8.0)
    }

    @Test
    @DisplayName("heavy axial low dose requires two days (48h, gap 2)")
    fun heavyAxialLowDoseRequiresTwoDays() {
        assertGap(2, LiftStressClass.HeavyAxial, 3.0)
    }

    @Test
    @DisplayName("heavy axial moderate dose requires three days (72h, gap 3)")
    fun heavyAxialModerateDoseRequiresThreeDays() {
        assertGap(3, LiftStressClass.HeavyAxial, 5.0)
    }

    @Test
    @DisplayName("heavy axial high dose requires four days (96h, gap 4)")
    fun heavyAxialHighDoseRequiresFourDays() {
        assertGap(4, LiftStressClass.HeavyAxial, 8.0)
    }

    @Test
    @DisplayName("sub-meaningful dose returns zero gap")
    fun subMeaningfulDoseReturnsZero() {
        assertEquals(0, minimumRecoveryCalendarGap(MuscleSessionStress(1.5, LiftStressClass.Isolation)))
    }

    private fun assertGap(expectedGap: Int, stressClass: LiftStressClass, dose: Double) {
        assertEquals(
            expectedGap,
            minimumRecoveryCalendarGap(MuscleSessionStress(dose, stressClass)),
            "$stressClass dose=$dose",
        )
    }

    private fun assertHours(expectedHours: Int, stressClass: LiftStressClass, dose: Double) {
        assertEquals(expectedHours, recoveryHours(dose, stressClass))
    }
}
