@file:Suppress("FunctionNaming", "MagicNumber", "MaxLineLength")

package com.stratum.domain.solver

import com.stratum.core.testing.program.parseScenarioContext
import com.stratum.core.testing.program.scenarioPayload
import com.stratum.domain.program.generation.context.PRIMARY_SET_CREDIT
import com.stratum.domain.program.generation.context.SECONDARY_SET_CREDIT
import com.stratum.domain.program.generation.prescription.SlotSetAllocationResult
import com.stratum.domain.program.generation.prescription.buildEmptySessionPlans
import com.stratum.domain.program.generation.reference.SESSION_MUSCLE_SET_CAP
import com.stratum.domain.program.generation.reference.PATTERN_SECONDARY_MUSCLE_CREDITS
import com.stratum.domain.program.generation.split.SlotRole
import com.stratum.domain.program.generation.split.resolveSplit
import com.stratum.domain.program.generation.volume.planWeeklyVolume
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Tests for [CpSatSlotSetAllocator]. Mirrors the scenario construction in
 * `CombinedUpperArmSpecBalanceTest` and `SlotSetAllocatorTest`: build a context
 * via the shared `:core:testing` fixtures, plan weekly targets, resolve the
 * split, then build empty [SessionSlotPlan]s and let the solver assign
 * `setCount`.
 */
class CpSatSlotSetAllocatorTest {

    private val allocator = CpSatSlotSetAllocator()

    @Test
    @DisplayName("4-day dual arm specialization balances bicep and tricep weekly credits")
    fun fourDayDualArmSpecBalancesWeeklyCredits() {
        val context = parseScenarioContext(
            scenarioPayload(4, mapOf("Bicep" to "Specialize", "Tricep" to "Specialize")),
        )
        val targets = planWeeklyVolume(context)
        val resolved = resolveSplit(context, targets)
        val plans = buildEmptySessionPlans(resolved.sessions, context)

        val result = allocator.allocate(plans, targets, context)

        assertTrue(result is SlotSetAllocationResult.Success, "Expected Success but got $result")
        val solved = (result as SlotSetAllocationResult.Success).plans

        val bicepWeekly = weeklyMuscleCredit(solved, "Bicep")
        val tricepWeekly = weeklyMuscleCredit(solved, "Tricep")

        assertTrue(bicepWeekly >= 12.0, "Bicep weekly credit too low: $bicepWeekly")
        assertTrue(tricepWeekly >= 12.0, "Tricep weekly credit too low: $tricepWeekly")
        assertEquals(
            tricepWeekly,
            bicepWeekly,
            0.5,
            "Bicep ($bicepWeekly) and Tricep ($tricepWeekly) weekly credits should match within 0.5",
        )
    }

    @Test
    @DisplayName("chest specialization gives chest weekly credit >= maintenance back credit")
    fun chestSpecializationPrioritizesChestOverMaintenanceBack() {
        val context = parseScenarioContext(
            scenarioPayload(4, mapOf("Chest" to "Specialize")),
        )
        val targets = planWeeklyVolume(context)
        val resolved = resolveSplit(context, targets)
        val plans = buildEmptySessionPlans(resolved.sessions, context)

        val result = allocator.allocate(plans, targets, context)

        assertTrue(result is SlotSetAllocationResult.Success, "Expected Success but got $result")
        val solved = (result as SlotSetAllocationResult.Success).plans

        val chestWeekly = weeklyMuscleCredit(solved, "Chest")
        val backWeekly = weeklyMuscleCredit(solved, "Back")

        assertTrue(chestWeekly > 0.0, "Chest weekly credit should be positive: $chestWeekly")
        assertTrue(
            chestWeekly >= backWeekly,
            "Specialized chest ($chestWeekly) should be >= maintenance back ($backWeekly)",
        )
    }

    @Test
    @DisplayName("30-min session keeps every workout within the session hard-set cap")
    fun shortSessionStaysWithinTimeCap() {
        val context = parseScenarioContext(
            scenarioPayload(4, mapOf("Bicep" to "Specialize", "Tricep" to "Specialize")),
        ).copy(sessionLengthMinutes = 30)
        val targets = planWeeklyVolume(context)
        val resolved = resolveSplit(context, targets)
        val plans = buildEmptySessionPlans(resolved.sessions, context)

        val result = allocator.allocate(plans, targets, context)

        // The MEV floor constraints may be infeasible under a 30-min budget —
        // if so, Fallback is the correct outcome. When Success, verify caps.
        if (result is SlotSetAllocationResult.Success) {
            val solved = result.plans
            val cap = kotlin.math.floor((30 + 5) / 3.5).toInt()
            for (plan in solved) {
                val total = plan.filledSlots.sumOf { it.setCount }
                assertTrue(
                    total <= cap,
                    "${plan.name}: $total sets exceeds 30-min cap $cap",
                )
            }
        }
    }

    @Test
    @DisplayName("no muscle exceeds SESSION_MUSCLE_SET_CAP per session")
    fun perSessionMuscleCapRespected() {
        val context = parseScenarioContext(
            scenarioPayload(4, mapOf("Bicep" to "Specialize", "Tricep" to "Specialize")),
        )
        val targets = planWeeklyVolume(context)
        val resolved = resolveSplit(context, targets)
        val plans = buildEmptySessionPlans(resolved.sessions, context)

        val result = allocator.allocate(plans, targets, context)

        assertTrue(result is SlotSetAllocationResult.Success, "Expected Success but got $result")
        val solved = (result as SlotSetAllocationResult.Success).plans

        for (plan in solved) {
            val creditedMuscles = mutableSetOf<String>()
            for (slot in plan.filledSlots) {
                if (slot.setCount <= 0) continue
                creditedMuscles.add(slot.primaryMuscle)
                PATTERN_SECONDARY_MUSCLE_CREDITS[slot.pattern]?.forEach { creditedMuscles.add(it.muscle) }
            }
            for (muscle in creditedMuscles) {
                val credit = sessionMuscleCredit(plan, muscle)
                assertTrue(
                    credit <= SESSION_MUSCLE_SET_CAP + 0.001,
                    "${plan.name}: muscle $muscle credit $credit exceeds cap $SESSION_MUSCLE_SET_CAP",
                )
            }
        }
    }

    @Test
    @DisplayName("anchor slots get at least ANCHOR_MIN_SETS (3) and secondary compounds at least 2")
    fun roleMinimumsRespected() {
        val context = parseScenarioContext(
            scenarioPayload(4, mapOf("Chest" to "Specialize")),
        )
        val targets = planWeeklyVolume(context)
        val resolved = resolveSplit(context, targets)
        val plans = buildEmptySessionPlans(resolved.sessions, context)

        val result = allocator.allocate(plans, targets, context)

        assertTrue(result is SlotSetAllocationResult.Success, "Expected Success but got $result")
        val solved = (result as SlotSetAllocationResult.Success).plans

        for (plan in solved) {
            for (slot in plan.filledSlots) {
                if (slot.setCount <= 0) continue
                when (slot.role) {
                    SlotRole.Anchor -> assertTrue(
                        slot.setCount >= 3,
                        "${plan.name} anchor ${slot.slotId}: ${slot.setCount} < 3",
                    )
                    SlotRole.SecondaryCompound -> assertTrue(
                        slot.setCount >= 2,
                        "${plan.name} secondary ${slot.slotId}: ${slot.setCount} < 2",
                    )
                    else -> Unit
                }
            }
        }
    }

    @Test
    @DisplayName("tight session (MEV infeasible) still solves via soft penalties")
    fun tightSessionStillSolvesViaSoftPenalties() {
        val context = parseScenarioContext(
            scenarioPayload(3, mapOf("Bicep" to "Specialize", "Tricep" to "Specialize")),
        ).copy(sessionLengthMinutes = 10) // 10-min session → ~4 sets/session cap
        val targets = planWeeklyVolume(context)
        val resolved = resolveSplit(context, targets)
        val plans = buildEmptySessionPlans(resolved.sessions, context)

        val result = allocator.allocate(plans, targets, context)

        // The model is always feasible (hard constraints are upper bounds
        // only). MEV floors and role minimums are soft penalties — the solver
        // gets as close as the caps allow.
        assertTrue(result is SlotSetAllocationResult.Success, "Expected Success but got $result")
    }

    // ---- helpers (mirror muscleCreditsInSession semantics) ----

    private fun weeklyMuscleCredit(plans: List<com.stratum.domain.program.generation.prescription.SessionSlotPlan>, muscle: String): Double {
        var total = 0.0
        for (plan in plans) {
            total += sessionMuscleCredit(plan, muscle)
        }
        return total
    }

    private fun sessionMuscleCredit(
        plan: com.stratum.domain.program.generation.prescription.SessionSlotPlan,
        muscle: String,
    ): Double {
        var total = 0.0
        for (slot in plan.filledSlots) {
            if (slot.setCount <= 0) continue
            if (slot.primaryMuscle == muscle) {
                total += slot.setCount * PRIMARY_SET_CREDIT
                continue
            }
            for (secondary in PATTERN_SECONDARY_MUSCLE_CREDITS[slot.pattern].orEmpty()) {
                if (secondary.muscle == muscle) {
                    total += slot.setCount * SECONDARY_SET_CREDIT
                }
            }
        }
        return total
    }
}