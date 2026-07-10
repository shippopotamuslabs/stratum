package com.stratum.domain.identity

import com.stratum.domain.lift.identity.IncrementTierId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.identity.GymPreferenceId
import com.stratum.domain.onboarding.identity.OnboardingProgressId
import com.stratum.domain.onboarding.identity.SpecializationPreferenceId
import com.stratum.domain.onboarding.identity.UserProfileId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SyncReadyIdsTest {
    @Test
    fun `singleton defaults are stable non-blank document ids`() {
        assertEquals("profile", UserProfileId.Default.value)
        assertEquals("onboarding_progress", OnboardingProgressId.Default.value)
        assertEquals("gym_preference", GymPreferenceId.Default.value)
    }

    @Test
    fun `specialization id is deterministic per muscle`() {
        val a = SpecializationPreferenceId.forMuscle(MuscleGroup.Chest)
        val b = SpecializationPreferenceId.forMuscle(MuscleGroup.Chest)
        val c = SpecializationPreferenceId.forMuscle(MuscleGroup.Back)
        assertEquals(a, b)
        assertEquals("specialization_Chest", a.value)
        assertNotEquals(a, c)
    }

    @Test
    fun `increment tier id is deterministic fixed-point for lift and lower bound`() {
        val lift = LiftId("leg-press")
        val id = IncrementTierId.forBand(lift, 40.0)
        assertEquals("leg-press_40", id.value)
        assertEquals(id, IncrementTierId.forBand(lift, 40.0))
        assertEquals("leg-press_2.5", IncrementTierId.forBand(lift, 2.5).value)
        // Binary noise near 2.5 still maps to the same fixed-point id.
        assertEquals("leg-press_2.5", IncrementTierId.forBand(lift, 2.5000000000000004).value)
    }

    @Test
    fun `blank ids are rejected`() {
        assertThrows<IllegalArgumentException> { UserProfileId("  ") }
        assertThrows<IllegalArgumentException> { IncrementTierId("") }
    }
}
