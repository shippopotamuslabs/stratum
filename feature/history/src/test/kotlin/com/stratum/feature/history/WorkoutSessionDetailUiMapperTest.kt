package com.stratum.feature.history

import com.stratum.domain.lift.Weight
import com.stratum.domain.training.WorkoutSessionDetail
import com.stratum.domain.training.WorkoutSessionLiftDetail
import com.stratum.domain.training.WorkoutSessionSetDetail
import com.stratum.domain.training.identity.TrainingSessionId
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class WorkoutSessionDetailUiMapperTest {

    @Test
    @DisplayName("Zero-load best set is max reps when no PR flag is set")
    fun bodyweightBestSetIsMaxReps() {
        val detail =
            sessionDetail(
                sets =
                    listOf(
                        bodyweightSet(setIndex = 0, reps = 8),
                        bodyweightSet(setIndex = 1, reps = 12),
                        bodyweightSet(setIndex = 2, reps = 6),
                    ),
            )

        val loaded = WorkoutSessionDetailUiMapper.toDetailLoaded(detail)

        assertEquals("12", loaded.lifts.single().bestSet?.performanceLabel)
        assertEquals(1, loaded.lifts.single().bestSet?.setIndex)
    }

    private fun sessionDetail(sets: List<WorkoutSessionSetDetail>): WorkoutSessionDetail {
        val started = Instant.parse("2025-01-01T10:00:00Z")
        val ended = Instant.parse("2025-01-01T11:00:00Z")
        return WorkoutSessionDetail(
            sessionId = TrainingSessionId("ts-1"),
            workoutName = "Upper A",
            startedAt = started,
            endedAt = ended,
            completedAt = ended,
            durationMillis = 3_600_000L,
            totalSets = sets.size,
            personalRecordCount = sets.count { it.isPersonalRecord },
            muscleVolumes = emptyList(),
            lifts =
                listOf(
                    WorkoutSessionLiftDetail(
                        liftName = "Pull-up",
                        sets = sets,
                        bestSet = sets.first { it.setIndex == 1 },
                    ),
                ),
        )
    }

    private fun bodyweightSet(setIndex: Int, reps: Int): WorkoutSessionSetDetail =
        WorkoutSessionSetDetail(
            setIndex = setIndex,
            weight = Weight(0.0, Weight.WeightUnit.Lb),
            reps = reps,
            rir = null,
            isPersonalRecord = false,
        )
}
