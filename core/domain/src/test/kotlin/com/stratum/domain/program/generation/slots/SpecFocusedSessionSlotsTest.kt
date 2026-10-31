package com.stratum.domain.program.generation.slots

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.core.testing.program.GenerationContextTestFixtures
import com.stratum.domain.program.generation.context.LiftContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

class SpecFocusedSessionSlotsTest {

    private val pressLift =
        LiftContext(
            id = "ohp",
            name = "Overhead Press",
            movementPattern = MovementPattern.VerticalPress,
            movementPatterns = setOf(MovementPattern.VerticalPress),
            primaryMuscles = setOf(MuscleGroup.AnteriorDeltoid),
            secondaryMuscles = emptySet(),
        )

    @Test
    fun `unrecognized spec-focus pattern records SpecFocusSlotSkipped and drops the slot`() {
        val diagnostics = mutableListOf<SlotGenerationDiagnostic>()

        val parsed = diagnostics.parseSpecFocusPatternOrRecord("NotAPattern", MuscleGroup.Chest)

        assertNull(parsed)
        assertEquals(
            listOf(SlotGenerationDiagnostic.SpecFocusSlotSkipped("NotAPattern", MuscleGroup.Chest)),
            diagnostics,
        )
    }

    @Test
    fun `recognized spec-focus pattern parses without recording a diagnostic`() {
        val diagnostics = mutableListOf<SlotGenerationDiagnostic>()

        val parsed = diagnostics.parseSpecFocusPatternOrRecord("VerticalPress", MuscleGroup.AnteriorDeltoid)

        assertEquals(MovementPattern.VerticalPress, parsed)
        assertTrue(diagnostics.isEmpty())
    }

    @Test
    fun `unrecognized spec-focus muscle records SpecFocusMuscleUnrecognized and excludes lifts`() {
        val diagnostics = mutableListOf<SlotGenerationDiagnostic>()

        val parsed = diagnostics.parseSpecFocusMuscleOrRecord("NotAMuscle", "ElbowFlexion")

        assertNull(parsed)
        assertEquals(
            listOf(SlotGenerationDiagnostic.SpecFocusMuscleUnrecognized("NotAMuscle", "ElbowFlexion")),
            diagnostics,
        )
    }

    @Test
    fun `recognized spec-focus muscle parses without recording a diagnostic`() {
        val diagnostics = mutableListOf<SlotGenerationDiagnostic>()

        val parsed = diagnostics.parseSpecFocusMuscleOrRecord("Bicep", "ElbowFlexion")

        assertEquals(MuscleGroup.Bicep, parsed)
        assertTrue(diagnostics.isEmpty())
    }

    @Test
    fun `spec-focused upper adds direct isolation slot with no diagnostics on happy path`() {
        val context =
            GenerationContextTestFixtures.contextWith(
                specialization = mapOf("AnteriorDeltoid" to "Specialize"),
                provenLifts = GenerationContextTestFixtures.volumePlannerLifts + pressLift,
            )
        val diagnostics = mutableListOf<SlotGenerationDiagnostic>()

        val slots =
            slotsForSessionType(
                sessionType = SessionTypeId.Upper,
                context = context,
                composition = SessionComposition.UpperSpecFocused,
                diagnostics = diagnostics,
            )

        val specSlot =
            slots.singleOrNull { it.slotId == "spec-focus-anteriordeltoid" }
                ?: fail("expected spec-focus slot for AnteriorDeltoid")
        assertEquals(MovementPattern.VerticalPress, specSlot.pattern)
        assertTrue(diagnostics.isEmpty())
    }
}
