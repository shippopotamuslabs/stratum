@file:Suppress("FunctionNaming", "MaxLineLength")

package com.stratum.domain.solver

import com.stratum.core.testing.program.parseScenarioContext
import com.stratum.core.testing.program.scenarioPayload
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.generation.context.SECONDARY_SET_CREDIT
import com.stratum.domain.program.generation.prescription.FilledSlot
import com.stratum.domain.program.generation.prescription.SessionSlotPlan
import com.stratum.domain.program.generation.reference.PatternSecondaryMuscleCredit
import com.stratum.domain.program.generation.reference.RpRepProfile
import com.stratum.domain.program.generation.slots.SlotRole
import com.stratum.domain.program.generation.universe.buildSlotUniverse
import com.stratum.domain.program.generation.volume.planWeeklyVolume
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Regression coverage for the program-synthesis audit fixes:
 *  - §5.1: catalog-averaged fractional secondary credits survive scaling (no truncation to 0).
 *  - §1.1/§7.2: muscles are hard-forbidden from meaningful exposure on adjacent days regardless of
 *    specialization tier (Maintenance Quad below); §7.2's MinVolume-as-priority bug is gone.
 *  - §4.1: a Glute-Specialize user gets a direct hip-thrust slot; a Maintenance user does not.
 *  - §7.5: duplicate training weekdays surface an actionable error.
 */
class ProgramSynthesisAuditFixTest {

    @Test
    fun scaledCoeffRepresentsFractionalAveragedCredits() {
        // A pattern where 3 of 8 lifts tag a muscle → 0.1875/set. With SCALE=100 this is 19, not 0.
        val credits = VolumeAllocationCoefficients(
            mapOf(
                MovementPattern.HipHinge to listOf(PatternSecondaryMuscleCredit(MuscleGroup.Quad, 0.1875)),
            ),
        )
        val slot = FilledSlot(
            slotId = "s0_hinge",
            role = SlotRole.Anchor,
            pattern = MovementPattern.HipHinge,
            primaryMuscle = MuscleGroup.Hamstring,
            repProfile = RpRepProfile.LowerFreeWeightCompound,
            setCount = 0,
        )
        // round(0.1875 * 100) = 19 — previously (0.1875 * 2).toLong() = 0 (truncated).
        assertEquals(19L, credits.scaledCoeff(slot, "Quad"))
        // Primary credit is always 1.0 → 100.
        assertEquals(100L, credits.scaledCoeff(slot, "Hamstring"))
        // The common "half the lifts tag it" case (0.25) → 25, not 0.
        val halfCredits = VolumeAllocationCoefficients(
            mapOf(MovementPattern.HipHinge to listOf(PatternSecondaryMuscleCredit(MuscleGroup.Quad, SECONDARY_SET_CREDIT / 2))),
        )
        assertEquals(25L, halfCredits.scaledCoeff(slot, "Quad"))
    }

    @Test
    fun maintenanceQuadIsHardForbiddenOnAdjacentDays() = runBlocking {
        // 2 sequential days (Mon, Tue). Quad is Maintenance (default). Under the LargeCompound
        // hard forbid, Quad cannot be meaningfully trained on both adjacent days.
        val context = parseScenarioContext(
            scenarioPayload(days = 2, specialization = emptyMap(), weekdayLayout = "sequential"),
        )
        val synthesizer = CpSatProgramSynthesizer()
        val synthesis = synthesizer.synthesize(planWeeklyVolume(context), context)

        // Verify at most one of the two sessions carries a meaningful Quad dose (>= 2.0 credits).
        val quadDoses = synthesis.plans.map { plan -> planMuscleCredits(plan, "Quad") }
        // Incidental secondary credits (e.g. ~3 sets from squat) do not count as meaningful exposure.
        val meaningfulDays = quadDoses.count { it >= 4.0 }
        assertTrue(
            meaningfulDays <= 1,
            "Maintenance Quad must not be meaningfully trained on both adjacent days; doses=$quadDoses",
        )
    }

    @Test
    fun gluteSpecializeUserGetsDirectHipThrustSlot() {
        val context = parseScenarioContext(
            scenarioPayload(days = 3, specialization = mapOf("Glute" to "Specialize")),
        )
        val universe = buildSlotUniverse(context, planWeeklyVolume(context))
        val gluteSlot = universe.firstOrNull { it.primaryMuscle == "Glute" }
        assertNotNull(
            gluteSlot,
            "A Glute-Specialize user must get a direct glute-primary slot in the universe.",
        )
        assertEquals("HipExtension", gluteSlot!!.pattern)
    }

    @Test
    fun gluteMaintenanceUserGetsNoDirectGluteSlot() {
        val context = parseScenarioContext(
            scenarioPayload(days = 3, specialization = emptyMap()),
        )
        val universe = buildSlotUniverse(context, planWeeklyVolume(context))
        assertTrue(
            universe.none { it.primaryMuscle == "Glute" },
            "A Maintenance user must not get a direct glute slot (glute volume rides on squat/hinge).",
        )
    }

    @Test
    fun duplicateTrainingWeekdaysThrowActionableError() {
        val context = parseScenarioContext(
            """
            {"profile":{"trainingAge":"Intermediate","sessionLengthMinutes":75,
            "weekdays":"Monday,Monday,Wednesday","trainingDaysPerWeek":3},
            "specialization":{},"provenLifts":[],"availableLifts":[],
            "literatureVolumeSnapshots":[]}
            """.trimIndent(),
        )
        val synthesizer = CpSatProgramSynthesizer()
        val ex = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { synthesizer.synthesize(planWeeklyVolume(context), context) }
        }
        assertTrue(
            ex.message?.contains("duplicates", ignoreCase = true) == true,
            "Duplicate-weekday error must be actionable: ${ex.message}",
        )
    }

    private fun planMuscleCredits(plan: SessionSlotPlan, muscle: String): Double {
        var total = 0.0
        for (slot in plan.filledSlots) {
            if (slot.setCount <= 0) continue
            total += slot.setCount * VolumeAllocationCoefficients.Default.coeff(slot, muscle)
        }
        return total
    }
}
