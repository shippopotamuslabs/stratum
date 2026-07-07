package com.stratum.domain.importing

import com.stratum.core.testing.training.WorkoutLoggingFixtures
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrainingInsightsExtractorTest {
    @Test
    fun `extracts fatigue slope and superset affinity from fixture history`() {
        val squatId = LiftId("hack-squat")
        val curlId = LiftId("db-curl")
        val lifts = listOf(
            lift(squatId, "Hack Squat", MovementPattern.SquatPattern, MuscleGroup.Quad),
            lift(curlId, "DB Curl", MovementPattern.HorizontalPull, MuscleGroup.Bicep),
        )
        val t0 = Instant.parse("2025-01-01T10:00:00Z")
        val sessions = (1..5).map { index ->
            WorkoutLoggingFixtures.importTrainingSession(
                id = "session-$index",
                startedAt = t0.plusSeconds(index * 86_400L),
                endedAt = null,
            )
        }
        val sets = buildList {
            sessions.forEachIndexed { sessionIndex, session ->
                repeat(3) { setIndex ->
                    add(
                        importSet(
                            id = "${session.id.value}-squat-$setIndex",
                            session = session,
                            exerciseNameRaw = "Hack Squat",
                            liftId = squatId,
                            setIndex = setIndex + 1,
                            weight = Weight(200.0 - setIndex * 10, Weight.WeightUnit.Lb),
                            reps = 10 - setIndex,
                            rir = null,
                            completedAt = session.startedAt.plusSeconds((setIndex * 60L) + 1),
                        ),
                    )
                }
                add(
                    importSet(
                        id = "${session.id.value}-curl",
                        session = session,
                        exerciseNameRaw = "DB Curl",
                        liftId = curlId,
                        setIndex = 1,
                        weight = Weight(30.0, Weight.WeightUnit.Lb),
                        reps = 12,
                        rir = null,
                        completedAt = session.startedAt.plusSeconds(190),
                    ),
                )
            }
        }
        val output = TrainingInsightsExtractor.analyze(
            TrainingInsightsExtractor.AnalysisInput(sessions, sets, lifts),
        )
        val squatInsights = output.liftInsights.first { it.liftId == squatId }
        assertTrue((squatInsights.fatigueDecaySlope ?: 0.0) < 0.0)
        assertEquals(5, squatInsights.sessionCount)
        assertTrue(output.supersetAffinities.isNotEmpty())
        assertTrue(output.volumeBaselines.any { it.muscle == MuscleGroup.Quad })
    }

    @Test
    fun `analysis window spans full imported history`() {
        val squatId = LiftId("hack-squat")
        val lifts =
            listOf(
                lift(squatId, "Hack Squat", MovementPattern.SquatPattern, MuscleGroup.Quad),
            )
        val oldSession =
            WorkoutLoggingFixtures.importTrainingSession(
                id = "old",
                startedAt = Instant.parse("2020-01-01T10:00:00Z"),
                endedAt = null,
            )
        val recentSession =
            WorkoutLoggingFixtures.importTrainingSession(
                id = "recent",
                startedAt = Instant.parse("2025-06-01T10:00:00Z"),
                endedAt = null,
            )
        val sets =
            listOf(
                importSet(
                    id = "old-set",
                    session = oldSession,
                    exerciseNameRaw = "Hack Squat",
                    liftId = squatId,
                    setIndex = 0,
                    weight = Weight(100.0, Weight.WeightUnit.Lb),
                    reps = 5,
                    rir = null,
                    completedAt = Instant.parse("2020-01-01T11:00:00Z"),
                ),
                importSet(
                    id = "recent-set",
                    session = recentSession,
                    exerciseNameRaw = "Hack Squat",
                    liftId = squatId,
                    setIndex = 0,
                    weight = Weight(200.0, Weight.WeightUnit.Lb),
                    reps = 5,
                    rir = null,
                    completedAt = Instant.parse("2025-06-01T11:00:00Z"),
                ),
            )

        val output =
            TrainingInsightsExtractor.analyze(
                TrainingInsightsExtractor.AnalysisInput(
                    sessions = listOf(oldSession, recentSession),
                    sets = sets,
                    lifts = lifts,
                ),
            )

        assertEquals(Instant.parse("2020-01-01T10:00:00Z"), output.metadata.windowStart)
        assertEquals(Instant.parse("2025-06-01T11:00:00Z"), output.metadata.windowEnd)
        assertEquals(2, output.metadata.totalSets)
    }

    private fun importSet(
        id: String,
        session: TrainingSession,
        exerciseNameRaw: String,
        liftId: LiftId,
        setIndex: Int,
        weight: Weight,
        reps: Int,
        rir: Int?,
        completedAt: Instant,
    ): LoggedSet =
        ImportHistoryTrainingMapper.parseSet(
            id = LoggedSetId(id),
            sessionId = session.id,
            exerciseNameRaw = exerciseNameRaw,
            setIndex = setIndex,
            weight = weight,
            reps = reps,
            rir = rir,
            completedAt = completedAt,
        ).copy(liftId = liftId)

    private fun lift(
        id: LiftId,
        name: String,
        pattern: MovementPattern,
        muscle: MuscleGroup,
    ): Lift = Lift(
        id = id,
        name = name,
        movementPatterns = setOf(pattern),
        primaryMuscles = setOf(muscle),
        secondaryMuscles = emptySet(),
        incrementTiers = emptyList(),
        presetTags = emptySet(),
        enabled = true,
    )
}
