package com.stratum.domain.program.generation.prescription

import com.stratum.core.testing.program.parseScenarioContext
import com.stratum.core.testing.program.scenarioPayload
import com.stratum.core.testing.program.testProgramGenerationPipeline
import com.stratum.domain.program.generation.draft.ProgramWorkoutDraft
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.pipeline.PipelineOptions
import com.stratum.domain.program.generation.schema.toDraft
import com.stratum.domain.program.generation.schema.toProgramNormalizationRequest
import com.stratum.domain.program.generation.split.SessionTypeId
import com.stratum.domain.program.generation.split.resolveSplit
import com.stratum.domain.program.generation.volume.planWeeklyVolume
import com.stratum.domain.solver.CpSatSlotSetAllocator
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CalfSpecializationLowerDayTest {

    @Test
    fun calfAndArmSpecLowerDaysRetainSquatAndHingeVolume() = runTest {
        val specialization =
            mapOf(
                "Calf" to "Specialize",
                "Bicep" to "Specialize",
                "Tricep" to "Specialize",
            )
        val context = parseScenarioContext(scenarioPayload(4, specialization))
        assertLowerWorkoutsHaveLegAnchors(context, specialization)
    }

    @Test
    fun sixDayCalfAndArmSpecLowerDaysRetainSquatAndHingeVolume() = runTest {
        val specialization =
            mapOf(
                "Calf" to "Specialize",
                "Bicep" to "Specialize",
                "Tricep" to "Specialize",
            )
        val context = parseScenarioContext(scenarioPayload(6, specialization))
        assertLowerWorkoutsHaveLegAnchors(context, specialization)
    }

    @Test
    fun thirtyMinuteSessionCalfSpecLowerDaysRetainLegAnchors() = runTest {
        val specialization =
            mapOf(
                "Calf" to "Specialize",
                "Bicep" to "Specialize",
            )
        val context = parseScenarioContext(scenarioPayload(6, specialization, sessionLengthMinutes = 30))
        assertLowerWorkoutsHaveLegAnchors(context, specialization)
    }

    @Test
    fun calfOnlySpecLowerSlotPlanKeepsLegAnchorsAtMinimum() {
        val specialization = mapOf("Calf" to "Specialize")
        val context = parseScenarioContext(scenarioPayload(4, specialization))
        val targets = planWeeklyVolume(context)
        val resolved = resolveSplit(context, targets)
        val plans = allocateSlotSetCounts(resolved.sessions, targets, context, CpSatSlotSetAllocator())
        val lowerPlans = plans.filter { it.sessionType == SessionTypeId.Lower }
        assertTrue(lowerPlans.isNotEmpty())
        for (plan in lowerPlans) {
            val squat = plan.filledSlots.first { it.pattern == "SquatPattern" }
            val hinge = plan.filledSlots.first { it.pattern == "HipHinge" }
            assertTrue(squat.setCount >= 3, "${plan.name} squat sets=${squat.setCount}")
            assertTrue(hinge.setCount >= 3, "${plan.name} hinge sets=${hinge.setCount}")
        }
    }

    @Test
    fun thirtyMinuteSessionCalfSpecSlotPlanKeepsLegAnchorsAtMinimum() {
        val specialization = mapOf("Calf" to "Specialize", "Bicep" to "Specialize")
        val context = parseScenarioContext(scenarioPayload(6, specialization, sessionLengthMinutes = 30))
        val targets = planWeeklyVolume(context)
        val resolved = resolveSplit(context, targets)
        val plans = allocateSlotSetCounts(resolved.sessions, targets, context, CpSatSlotSetAllocator())
        val lowerPlans = plans.filter { it.sessionType == SessionTypeId.Lower }
        assertTrue(lowerPlans.isNotEmpty())
        for (plan in lowerPlans) {
            val squat = plan.filledSlots.first { it.pattern == "SquatPattern" }
            val hinge = plan.filledSlots.first { it.pattern == "HipHinge" }
            assertTrue(squat.setCount >= 3, "${plan.name} squat sets=${squat.setCount}")
            assertTrue(hinge.setCount >= 3, "${plan.name} hinge sets=${hinge.setCount}")
        }
    }

    private suspend fun assertLowerWorkoutsHaveLegAnchors(
        context: ProgramGenerationContext,
        specialization: Map<String, String>,
    ) {
        val program = testProgramGenerationPipeline().generate(
            context = context,
            normalizationRequest = context.toProgramNormalizationRequest(),
            options = PipelineOptions(skipLiftSwap = true),
        )
        val draft = program.toDraft()
        val lowerWorkouts = draft.workouts.filter { it.name.orEmpty().contains("Lower", ignoreCase = true) }
        assertTrue(lowerWorkouts.isNotEmpty())
        for (workout in lowerWorkouts) {
            val squatSets = primarySetsForPattern(workout, context, "SquatPattern")
            val hingeSets = primarySetsForPattern(workout, context, "HipHinge")
            assertTrue(
                squatSets >= 3,
                "${workout.name} squat-pattern sets=$squatSets (expected ≥3) spec=$specialization",
            )
            assertTrue(
                hingeSets >= 3,
                "${workout.name} hinge-pattern sets=$hingeSets (expected ≥3) spec=$specialization",
            )
        }
    }

    private fun primarySetsForPattern(
        workout: ProgramWorkoutDraft,
        context: ProgramGenerationContext,
        pattern: String,
    ): Int {
        var total = 0
        for (lift in workout.workoutLifts.orEmpty()) {
            val meta = context.liftsById[lift.liftId.orEmpty()] ?: continue
            if (meta.movementPattern != pattern && pattern !in meta.movementPatterns) continue
            total += lift.workoutLiftSets?.size ?: 0
        }
        return total
    }
}
