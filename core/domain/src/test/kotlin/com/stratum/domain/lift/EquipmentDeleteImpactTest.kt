package com.stratum.domain.lift

import com.stratum.core.testing.onboarding.FakeEquipmentRepository
import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.program.FakeProgramRepository
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("GetEquipmentDeleteImpactUseCase")
class EquipmentDeleteImpactTest {

    private val deletedId = EquipmentId("deleted-eq")
    private val liveId = EquipmentId("live-eq")

    private fun lift(id: String, equipmentId: EquipmentId?) = Lift(
        id = LiftId(id),
        name = "Lift $id",
        movementPatterns = setOf(MovementPattern.HorizontalPress),
        primaryMuscles = setOf(MuscleGroup.Chest),
        secondaryMuscles = emptySet(),
        incrementTiers = emptyList(),
        presetTags = emptySet(),
        equipmentId = equipmentId,
        enabled = true,
        isCustom = true,
    )

    private fun program(name: String, slots: List<EquipmentId?>): Program {
        val workoutId = WorkoutId("w-$name")
        return Program(
            id = ProgramId("p-$name"),
            name = name,
            createdAt = Instant.EPOCH,
            cycleLength = 1,
            workouts = listOf(
                Workout(
                    id = workoutId,
                    programId = ProgramId("p-$name"),
                    name = "Day $name",
                    weekday = null,
                    position = 0,
                    workoutLifts = slots.mapIndexed { index, equipmentId ->
                        WorkoutLift(
                            id = WorkoutLiftId("wl-$name-$index"),
                            workoutId = workoutId,
                            liftId = LiftId("lift-$index"),
                            progressionScheme = ProgressionScheme.Double,
                            position = index,
                            sets = emptyList(),
                            equipmentId = equipmentId,
                        )
                    },
                ),
            ),
        )
    }

    @Test
    @DisplayName("Impact lists affected lifts, slot counts, and live candidates only")
    fun computesImpact() = runTest {
        val equipmentRepo = FakeEquipmentRepository(
            initial = listOf(
                Equipment(id = deletedId, name = "Old", incrementTiers = emptyList(), isCustom = true),
                Equipment(id = liveId, name = "Live", incrementTiers = emptyList(), isCustom = true),
                Equipment(id = SeedEquipmentIds.Bodyweight, name = "Bodyweight", incrementTiers = emptyList()),
            ),
        )
        val liftRepo = FakeLiftRepository(listOf(lift("a", deletedId), lift("b", liveId)))
        val programRepo = FakeProgramRepository(program("Main", listOf(deletedId, deletedId, null)))
        val useCase = GetEquipmentDeleteImpactUseCase(equipmentRepo, liftRepo, programRepo)

        val impact = useCase(deletedId)!!

        assertEquals(listOf("a"), impact.affectedLifts.map { it.id.value })
        assertEquals(1, impact.affectedWorkouts.size)
        assertEquals(2, impact.affectedWorkouts.single().slotCount)
        assertEquals(listOf(liveId), impact.replacementCandidates.map { it.id })
        assertTrue(!impact.usesBodyweightFallback)
    }

    @Test
    @DisplayName("No live candidate with pointers means Bodyweight fallback")
    fun fallbackWhenNoCandidate() = runTest {
        val equipmentRepo = FakeEquipmentRepository(
            initial = listOf(Equipment(id = deletedId, name = "Old", incrementTiers = emptyList(), isCustom = true)),
        )
        val liftRepo = FakeLiftRepository(listOf(lift("a", deletedId)))
        val programRepo = FakeProgramRepository(null)
        val useCase = GetEquipmentDeleteImpactUseCase(equipmentRepo, liftRepo, programRepo)

        val impact = useCase(deletedId)!!

        assertTrue(impact.hasAffectedPointers)
        assertTrue(impact.usesBodyweightFallback)
    }
}
