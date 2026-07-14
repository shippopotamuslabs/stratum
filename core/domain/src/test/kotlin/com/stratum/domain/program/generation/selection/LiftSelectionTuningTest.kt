package com.stratum.domain.program.generation.selection

import com.stratum.domain.lift.KinematicProfile
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.MuscleLengthBias
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.slots.MuscleLengthBiasPreference
import com.stratum.domain.program.generation.slots.SiblingVariant
import com.stratum.domain.program.generation.slots.SlotRole
import com.stratum.domain.program.generation.slots.WorkoutSlotTemplate
import org.junit.jupiter.api.Test
import java.io.File

class LiftSelectionTuningTest {

    @Test
    fun benchmarkLiftSelection() {
        val weightsProp = System.getProperty("stratum.lift.weights")
        if (!weightsProp.isNullOrBlank()) {
            val overrides = weightsProp.split(",")
                .associate {
                    val parts = it.split(":")
                    parts[0].trim() to parts[1].trim().toDouble()
                }
            LiftSelectionWeights.setOverrides(overrides)
            println("Benchmark Selection: Running with overrides: $overrides")
        } else {
            LiftSelectionWeights.setOverrides(null)
            println("Benchmark Selection: Running with default weights")
        }

        var totalLoss = 0.0
        val details = mutableMapOf<String, Double>()

        // CASE 1: Anchor slot should treat compound machines (e.g. Leverage Chest Press / Hammer Strength) on equal footing
        val case1Lifts = listOf(
            lift("barbell-bench", "Barbell Bench Press", "HorizontalPress", listOf("Chest"), proven = true, mainstream = true, equipment = "Barbell", kinematicProfile = "NonAxialCompound"),
            lift("db-bench", "Dumbbell Bench Press", "HorizontalPress", listOf("Chest"), proven = true, mainstream = true, equipment = "Dumbbell", kinematicProfile = "NonAxialCompound"),
            lift("hammer-smith", "Hammer Strength Chest Press", "HorizontalPress", listOf("Chest"), proven = true, mainstream = true, equipment = "Machine", kinematicProfile = "NonAxialCompound")
        )
        val context1 = testContext(case1Lifts)
        val slot1 = slot(role = SlotRole.Anchor, pattern = "HorizontalPress", primary = "Chest")
        val ranked1 = rankLiftsForSlot(case1Lifts, slot1, context1, LiftSelectionState())
        val barbellIdx = ranked1.indexOfFirst { it.id == "barbell-bench" }
        val dbIdx = ranked1.indexOfFirst { it.id == "db-bench" }
        val declineIdx = ranked1.indexOfFirst { it.id == "hammer-smith" }
        
        val case1Loss = (barbellIdx * 100.0) + (dbIdx * 50.0) + (if (declineIdx < barbellIdx || declineIdx < dbIdx) 1000.0 else 0.0)
        totalLoss += case1Loss
        details["Case1_AnchorRole"] = case1Loss

        // CASE 2: Specialized muscle length bias selection
        val case2Lifts = listOf(
            lift("incline-db-curl", "Incline Dumbbell Curl", "ElbowFlexion", listOf("Bicep"), lengthBias = "Lengthened"),
            lift("preacher-curl", "Preacher Curl", "ElbowFlexion", listOf("Bicep"), lengthBias = "Shortened")
        )
        val context2 = testContext(case2Lifts)
        val slot2 = slot(role = SlotRole.Isolation, pattern = "ElbowFlexion", primary = "Bicep", lengthBias = MuscleLengthBiasPreference.Lengthened)
        val ranked2 = rankLiftsForSlot(case2Lifts, slot2, context2, LiftSelectionState())
        val inclineIdx = ranked2.indexOfFirst { it.id == "incline-db-curl" }
        val preacherIdx = ranked2.indexOfFirst { it.id == "preacher-curl" }
        
        val case2Loss = if (preacherIdx < inclineIdx) 500.0 else 0.0
        totalLoss += case2Loss
        details["Case2_LengthBias"] = case2Loss

        // CASE 3: Isolation Accessory Cable/Machine Priority
        val case3Lifts = listOf(
            lift("cable-pushdown", "Cable Triceps Pushdown", "ElbowExtension", listOf("Tricep"), equipment = "Cable"),
            lift("barbell-tricep-ext", "Standing Overhead Barbell Triceps Extension", "ElbowExtension", listOf("Tricep"), equipment = "Barbell")
        )
        val context3 = testContext(case3Lifts)
        val slot3 = slot(role = SlotRole.Isolation, pattern = "ElbowExtension", primary = "Tricep")
        val ranked3 = rankLiftsForSlot(case3Lifts, slot3, context3, LiftSelectionState())
        val cableIdx = ranked3.indexOfFirst { it.id == "cable-pushdown" }
        val barbellTricepIdx = ranked3.indexOfFirst { it.id == "barbell-tricep-ext" }
        
        val case3Loss = if (barbellTricepIdx < cableIdx) 300.0 else 0.0
        totalLoss += case3Loss
        details["Case3_IsolationRole"] = case3Loss

        // CASE 4: Cycle Variety Rotation (avoid repeating squat immediately)
        val case4Lifts = listOf(
            lift("squat", "Barbell Back Squat", "SquatPattern", listOf("Quad")),
            lift("front-squat", "Barbell Front Squat", "SquatPattern", listOf("Quad"))
        )
        val context4 = testContext(case4Lifts)
        val slot4 = slot(role = SlotRole.Anchor, pattern = "SquatPattern", primary = "Quad")
        val selectionState4 = LiftSelectionState()
        selectionState4.cycleUsedLiftIdsByPattern.getOrPut(MovementPattern.SquatPattern) { mutableSetOf() }.add("squat")
        val ranked4 = rankLiftsForSlot(case4Lifts, slot4, context4, selectionState4)
        val squatIdx = ranked4.indexOfFirst { it.id == "squat" }
        val frontSquatIdx = ranked4.indexOfFirst { it.id == "front-squat" }
        
        val case4Loss = if (squatIdx < frontSquatIdx) 500.0 else 0.0
        totalLoss += case4Loss
        details["Case4_CycleVariety"] = case4Loss

        // CASE 5: Proven vs Available Lifts
        val case5Lifts = listOf(
            lift("bench-proven", "Bench Press", "HorizontalPress", listOf("Chest"), proven = true),
            lift("bench-available", "Bench Press Machine", "HorizontalPress", listOf("Chest"), proven = false)
        )
        val context5 = testContext(case5Lifts)
        val slot5 = slot(role = SlotRole.Anchor, pattern = "HorizontalPress", primary = "Chest")
        val ranked5 = rankLiftsForSlot(case5Lifts, slot5, context5, LiftSelectionState())
        val provenIdx = ranked5.indexOfFirst { it.id == "bench-proven" }
        val availableIdx = ranked5.indexOfFirst { it.id == "bench-available" }
        
        val case5Loss = if (availableIdx < provenIdx) 400.0 else 0.0
        totalLoss += case5Loss
        details["Case5_ProvenTier"] = case5Loss

        // CASE 6: Hip Hinge Selection should prefer RDL (high-SFR) over Conventional Deadlift (low-SFR, systemic fatigue)
        val case6Lifts = listOf(
            lift("barbell-rdl", "Barbell Romanian Deadlift", "HipHinge", listOf("Hamstring"), secondary = listOf("Glute", "LowerBack"), kinematicProfile = "AxialCompound"),
            lift("barbell-deadlift", "Barbell Deadlift", "HipHinge", listOf("LowerBack"), secondary = listOf("Calf", "Forearm", "Glute", "Hamstring", "Back", "Quad", "Trap"), kinematicProfile = "AxialCompound")
        )
        val context6 = testContext(case6Lifts)
        val slot6 = slot(role = SlotRole.Anchor, pattern = "HipHinge", primary = "Hamstring")
        val ranked6 = rankLiftsForSlot(case6Lifts, slot6, context6, LiftSelectionState())
        val rdlIdx = ranked6.indexOfFirst { it.id == "barbell-rdl" }
        val dlIdx = ranked6.indexOfFirst { it.id == "barbell-deadlift" }
        
        val case6Loss = if (dlIdx < rdlIdx) 500.0 else 0.0
        totalLoss += case6Loss
        details["Case6_SfrHinge"] = case6Loss

        println("Benchmark Selection Complete. Total Loss: $totalLoss")
        println("Details: $details")

        val buildDir = File("build")
        if (!buildDir.exists()) buildDir.mkdirs()
        val lossFile = File(buildDir, "lift_selection_loss.json")
        lossFile.writeText("{\"loss\": $totalLoss}")
        println("Loss saved to ${lossFile.absolutePath}")
    }

    private fun rankLiftsForSlot(
        candidates: List<LiftContext>,
        slot: WorkoutSlotTemplate,
        context: ProgramGenerationContext,
        selectionState: LiftSelectionState,
        siblingIndex: Int = 0,
        setCount: Int = 3
    ): List<LiftContext> {
        return candidates.sortedWith { a, b ->
            val sa = scoreLiftForSlot(a, slot, context, siblingIndex, setCount, emptyMap()) +
                (if (a.id in selectionState.cycleUsedLiftIdsByPattern[slot.pattern].orEmpty()) {
                    LiftSelectionWeights.CYCLE_VARIETY_USED_PENALTY
                } else {
                    LiftSelectionWeights.CYCLE_VARIETY_FRESH_BONUS
                })
            val sb = scoreLiftForSlot(b, slot, context, siblingIndex, setCount, emptyMap()) +
                (if (b.id in selectionState.cycleUsedLiftIdsByPattern[slot.pattern].orEmpty()) {
                    LiftSelectionWeights.CYCLE_VARIETY_USED_PENALTY
                } else {
                    LiftSelectionWeights.CYCLE_VARIETY_FRESH_BONUS
                })
            if (sb != sa) {
                return@sortedWith when {
                    sb > sa -> 1
                    sb < sa -> -1
                    else -> 0
                }
            }
            compareLiftCandidates(
                LiftCandidate(
                    liftId = a.id,
                    name = a.name,
                    pattern = a.movementPattern,
                    priority = sa,
                    priorityTier = if (a.proven == true) "proven" else "available",
                    isCompound = a.movementPatterns.size > 1,
                    primaryMuscles = a.primaryMuscles,
                    secondaryMuscles = a.secondaryMuscles,
                ),
                LiftCandidate(
                    liftId = b.id,
                    name = b.name,
                    pattern = b.movementPattern,
                    priority = sb,
                    priorityTier = if (b.proven == true) "proven" else "available",
                    isCompound = b.movementPatterns.size > 1,
                    primaryMuscles = b.primaryMuscles,
                    secondaryMuscles = b.secondaryMuscles,
                ),
            )
        }
    }

    private fun lift(
        id: String,
        name: String,
        pattern: String,
        primary: List<String>,
        secondary: List<String> = emptyList(),
        proven: Boolean = true,
        mainstream: Boolean = true,
        equipment: String? = null,
        lengthBias: String? = null,
        kinematicProfile: String = "Isolation"
    ): LiftContext =
        LiftContext(
            id = id,
            name = name,
            movementPattern = runCatching { MovementPattern.valueOf(pattern) }.getOrNull(),
            movementPatterns = setOfNotNull(runCatching { MovementPattern.valueOf(pattern) }.getOrNull()),
            primaryMuscles = primary.mapNotNull { runCatching { MuscleGroup.valueOf(it) }.getOrNull() }.toSet(),
            secondaryMuscles = secondary.mapNotNull { runCatching { MuscleGroup.valueOf(it) }.getOrNull() }.toSet(),
            proven = proven,
            mainstream = mainstream,
            equipment = equipment,
            muscleLengthBias = lengthBias?.let { MuscleLengthBias.valueOf(it) },
            kinematicProfile = runCatching { KinematicProfile.valueOf(kinematicProfile) }.getOrNull() ?: KinematicProfile.Isolation
        )

    private fun slot(
        role: SlotRole,
        pattern: String,
        primary: String,
        lengthBias: MuscleLengthBiasPreference = MuscleLengthBiasPreference.Any
    ): WorkoutSlotTemplate =
        WorkoutSlotTemplate(
            slotId = "test-slot",
            role = role,
            pattern = runCatching { MovementPattern.valueOf(pattern) }.getOrNull() ?: MovementPattern.ElbowFlexion,
            primaryMuscle = runCatching { MuscleGroup.valueOf(primary) }.getOrNull() ?: MuscleGroup.Chest,
            repProfile = com.stratum.domain.program.generation.reference.RpRepProfile.Isolation,
            lengthBiasPreference = lengthBias,
            siblingVariant = SiblingVariant.A,
            isCrossDomainTouch = false,
            allowedKinematicProfiles = null
        )

    private fun testContext(lifts: List<LiftContext>): ProgramGenerationContext {
        val liftsById = lifts.associateBy { it.id }
        return ProgramGenerationContext(
            trainingAge = "Intermediate",
            lastTrained = null,
            sessionLengthMinutes = 75,
            trainingDaysPerWeek = 3,
            specialization = emptyMap(),
            liftsById = liftsById,
            muscleVolumeSnapshotsByMuscle = emptyMap(),
            volumeCorrelationsByMuscle = emptyMap(),
            enabledLiftIds = liftsById.keys,
            movementPatternByLiftId = liftsById.mapValues { it.value.movementPattern },
            liftEffectivenessByLiftId = emptyMap()
        )
    }
}
