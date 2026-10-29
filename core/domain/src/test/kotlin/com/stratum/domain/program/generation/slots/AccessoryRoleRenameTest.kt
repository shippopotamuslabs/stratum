package com.stratum.domain.program.generation.slots

import com.stratum.domain.lift.FatigueProfile
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.program.generation.reference.RpRepProfile

/**
 * FPR-S4-001 / FPR-S4-005 / FPR-S4-007 — Accessory hard rename for slot roles and allow-lists.
 */
class AccessoryRoleRenameTest {

    @Test
    @DisplayName("FPR-S4-001 isolation-lane templates use SlotRole.Accessory")
    fun fprS4001_slotRoleAccessory() {
        val roles =
            LOWER_SLOTS
                .filter { it.slotId == "quad-iso" || it.slotId == "ham-iso" }
                .map { it.role }
        assertEquals(2, roles.size)
        assertTrue(roles.all { it.name == "Accessory" }, "quad-iso/ham-iso role must be Accessory")
        assertFalse(
            SlotRole.entries.any { it.name == "Isolation" },
            "SlotRole must not declare Isolation",
        )
    }

    @Test
    @DisplayName("FPR-S4-005 quad-iso and ham-iso allow Low and Medium only")
    fun fprS4005_allowListLowAndMediumOnly() {
        val templates =
            LOWER_SLOTS.filter { it.slotId == "quad-iso" || it.slotId == "ham-iso" }
        assertEquals(2, templates.size)
        for (template in templates) {
            val allowedNames = template.allowedFatigueProfiles?.map { it.name }?.toSet()
            assertEquals(
                setOf(FatigueProfile.Low.name, FatigueProfile.Medium.name),
                allowedNames,
            )
            assertFalse(allowedNames!!.contains(FatigueProfile.High.name))
        }
    }

    @Test
    @DisplayName("FPR-S4-007 spec-focused promotion rewrites Optional to Accessory")
    fun fprS4007_optionalPromotesToAccessory() {
        val slots =
            mutableListOf(
                WorkoutSlotTemplate(
                    slotId = "optional-bicep",
                    role = SlotRole.Optional,
                    pattern = com.stratum.domain.lift.MovementPattern.ElbowFlexion,
                    primaryMuscle = MuscleGroup.Bicep,
                    repProfile = com.stratum.domain.program.generation.reference.RpRepProfile.Isolation,
                ),
            )
        val context =
            ProgramGenerationContext(
                trainingAge = "Intermediate",
                lastTrained = null,
                sessionLengthMinutes = 60,
                trainingDaysPerWeek = 4,
                specialization = mapOf(MuscleGroup.Bicep to SpecializationLevel.Specialize),
                liftsById = emptyMap(),
                muscleVolumeSnapshotsByMuscle = emptyMap(),
                volumeCorrelationsByMuscle = emptyMap(),
                enabledLiftIds = emptySet(),
                movementPatternByLiftId = emptyMap(),
                liftEffectivenessByLiftId = emptyMap(),
            )
        applySpecFocusedUpperSlots(slots, context)
        val promoted = slots.single { it.slotId == "optional-bicep" }
        assertEquals("Accessory", promoted.role.name)
        assertFalse(promoted.role.name == "Optional")
        assertFalse(promoted.role.name == "Isolation")
    }
}
