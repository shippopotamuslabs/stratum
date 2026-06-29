package com.stratum.domain.program.generation.prescription

import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.context.VolumeLandmarkContext
import com.stratum.domain.program.generation.context.VolumeLandmarkSource
import com.stratum.domain.program.generation.reference.SESSION_MUSCLE_SET_CAP
import com.stratum.domain.program.generation.split.resolveSplit
import com.stratum.domain.program.generation.volume.planWeeklyVolume
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class SlotSetAllocatorTest {
    private val liftCatalog = listOf(
        LiftContext("bench", "Bench Press", "HorizontalPress", listOf("HorizontalPress"), listOf("Chest"), listOf("Tricep"), proven = true),
        LiftContext("row", "Row", "HorizontalPull", listOf("HorizontalPull"), listOf("Back"), listOf("Bicep"), proven = true),
        LiftContext("squat", "Squat", "SquatPattern", listOf("SquatPattern"), listOf("Quad"), listOf("Glute"), proven = true),
        LiftContext("rdl", "RDL", "HipHinge", listOf("HipHinge"), listOf("Hamstring"), listOf("Glute"), proven = true),
        LiftContext("curl", "Curl", "ElbowFlexion", listOf("ElbowFlexion"), listOf("Bicep"), emptyList(), proven = true),
        LiftContext("tricep-ext", "Tricep Extension", "ElbowExtension", listOf("ElbowExtension"), listOf("Tricep"), emptyList(), proven = true),
        LiftContext("leg-ext", "Leg Extension", "KneeExtension", listOf("KneeExtension"), listOf("Quad"), emptyList(), proven = true),
        LiftContext("leg-curl", "Leg Curl", "KneeFlexion", listOf("KneeFlexion"), listOf("Hamstring"), emptyList(), proven = true),
        LiftContext("lat-raise", "Lateral Raise", "ShoulderAbduction", listOf("ShoulderAbduction"), listOf("LateralDeltoid"), emptyList(), proven = true),
        LiftContext("calf-raise", "Calf Raise", "PlantarFlexion", listOf("PlantarFlexion"), listOf("Calf"), emptyList(), proven = true),
    )

    private val landmarks = listOf(
        landmark("Chest", 8.0, 14.0, 20.0, 11.0),
        landmark("Back", 8.0, 14.0, 20.0, 11.0),
        landmark("Quad", 8.0, 14.0, 20.0, 11.0),
        landmark("Hamstring", 6.0, 10.0, 16.0, 8.0),
        landmark("Bicep", 4.0, 8.0, 14.0, 6.0),
        landmark("Tricep", 4.0, 8.0, 14.0, 6.0),
        landmark("LateralDeltoid", 4.0, 8.0, 14.0, 6.0),
        landmark("Calf", 6.0, 10.0, 16.0, 8.0),
    )

    private fun landmark(
        muscle: String,
        mev: Double,
        mav: Double,
        mrv: Double,
        pv: Double,
    ): VolumeLandmarkContext =
        VolumeLandmarkContext(
            muscle = muscle,
            estimatedMev = mev,
            estimatedMav = mav,
            estimatedMrv = mrv,
            estimatedPv = pv,
            source = VolumeLandmarkSource.Literature,
        )

    private fun contextFor(days: Int): ProgramGenerationContext {
        val weekdays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday").take(days)
        val liftsById = liftCatalog.associateBy { it.id }
        return ProgramGenerationContext(
            trainingAge = "Advanced",
            lastTrained = null,
            sessionLengthMinutes = 75,
            trainingDaysPerWeek = days,
            weekdays = weekdays,
            specialization = mapOf("Bicep" to "Specialize", "Tricep" to "Specialize"),
            liftsById = liftsById,
            volumeLandmarksByMuscle = landmarks.associateBy { it.muscle },
            volumeResponsesByMuscle = emptyMap(),
            enabledLiftIds = liftsById.keys.toSet(),
            movementPatternByLiftId = liftsById.mapValues { it.value.movementPattern },
            liftEffectivenessByLiftId = emptyMap(),
        )
    }

    private fun primaryMuscleCreditsInSession(plan: SessionSlotPlan, muscle: String): Int =
        plan.filledSlots
            .filter { it.primaryMuscle == muscle }
            .sumOf { it.setCount }

    @Test
    @DisplayName("keeps per-session primary-muscle sets at or below SESSION_MUSCLE_SET_CAP")
    fun keepsPerSessionPrimaryMuscleSetsAtOrBelowCap() {
        val context = contextFor(4)
        val targets = planWeeklyVolume(context)
        val resolved = resolveSplit(context, targets)
        val plans = allocateSlotSetCounts(resolved.sessions, targets, context)
        for (plan in plans) {
            val muscles = plan.filledSlots.map { it.primaryMuscle }.toSet()
            for (muscle in muscles) {
                assertTrue(
                    primaryMuscleCreditsInSession(plan, muscle) <= SESSION_MUSCLE_SET_CAP,
                    "${plan.name} exceeds cap for $muscle",
                )
            }
        }
    }
}
