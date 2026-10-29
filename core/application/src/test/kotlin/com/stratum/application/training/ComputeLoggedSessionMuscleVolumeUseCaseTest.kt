package com.stratum.application.training

import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Weight
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.training.LoggedSet

class ComputeLoggedSessionMuscleVolumeUseCaseTest {

    private val useCase = ComputeLoggedSessionMuscleVolumeUseCase()

    @Test
    @DisplayName("Primary muscle gets 1.0 and secondary 0.5 per set")
    fun creditsPrimaryAndSecondary() {
        val set =
            LoggedSet(
                id = LoggedSetId("s1"),
                sessionId = TrainingSessionId("ts"),
                exerciseNameRaw = "Bench",
                liftId = WorkoutLoggingFixtures.liftBenchId,
                setIndex = 0,
                weight = Weight(135.0, Weight.WeightUnit.Lb),
                reps = 8,
                rir = 2,
                completedAt = Instant.parse("2025-01-01T10:00:00Z"),
                liftContext = WorkoutLoggingFixtures.importLiftContext(),
            )
        val summaries = useCase(listOf(set, set.copy(id = LoggedSetId("s2"), setIndex = 1)))
        val byMuscle = summaries.associateBy { it.muscle }
        assertEquals(2.0, byMuscle[MuscleGroup.Chest]!!.setCount)
        assertEquals(1.0, byMuscle[MuscleGroup.Tricep]!!.setCount)
        assertEquals(1, byMuscle[MuscleGroup.Chest]!!.exerciseCount)
    }

    @Test
    @DisplayName("Empty sets yield empty volume")
    fun emptySets() {
        assertTrueEmpty(useCase(emptyList()))
    }

    private fun assertTrueEmpty(list: List<*>) {
        assertEquals(0, list.size)
    }
}
