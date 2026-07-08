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
    fun `extractor discards non-negative fatigue slope and falls back to null for population default`() {
        // Reps rising at the same weight and RIR (120x8@2 → 120x10@2) is the misjudged-RIR
        // pattern, not genuine positive decay. e1RM rises across set index, so the per-session
        // regression slope is positive. The extractor must discard it (emit null) so downstream
        // substitutes POPULATION_FATIGUE_DECAY_SLOPE rather than compounding the error.
        val squatId = LiftId("hack-squat")
        val lifts = listOf(
            lift(squatId, "Hack Squat", MovementPattern.SquatPattern, MuscleGroup.Quad),
        )
        val t0 = Instant.parse("2025-01-01T10:00:00Z")
        val sessions = (1..3).map { index ->
            WorkoutLoggingFixtures.importTrainingSession(
                id = "session-$index",
                startedAt = t0.plusSeconds(index * 86_400L),
                endedAt = null,
            )
        }
        val sets = buildList {
            sessions.forEach { session ->
                add(
                    importSet(
                        id = "${session.id.value}-squat-1",
                        session = session,
                        exerciseNameRaw = "Hack Squat",
                        liftId = squatId,
                        setIndex = 1,
                        weight = Weight(120.0, Weight.WeightUnit.Lb),
                        reps = 8,
                        rir = 2,
                        completedAt = session.startedAt.plusSeconds(1),
                    ),
                )
                add(
                    importSet(
                        id = "${session.id.value}-squat-2",
                        session = session,
                        exerciseNameRaw = "Hack Squat",
                        liftId = squatId,
                        setIndex = 2,
                        weight = Weight(120.0, Weight.WeightUnit.Lb),
                        reps = 10,
                        rir = 2,
                        completedAt = session.startedAt.plusSeconds(61),
                    ),
                )
            }
        }
        val output = TrainingInsightsExtractor.analyze(
            TrainingInsightsExtractor.AnalysisInput(sessions, sets, lifts),
        )
        val squatInsights = output.liftInsights.first { it.liftId == squatId }
        assertEquals(null, squatInsights.fatigueDecaySlope, "Positive slope must be discarded")
        assertEquals(null, squatInsights.fatigueDecayIntercept, "Intercept must be discarded with the slope")
        assertEquals(3, squatInsights.sessionCount)
    }

    @Test
    fun `fatigue slope is measured in reps per set, not e1RM load units`() {
        // Pins the regression unit: the slope must be in REPS PER SET to match
        // POPULATION_FATIGUE_DECAY_SLOPE (-2.0) and the consumers
        // (RepRirPlanner.repRangeFromFatigue, FatigueModel.projectReps). Regressing e1RM in load
        // units instead inflates the slope ~10× for a 165 lb lift and saturates the rep-range
        // cap at 30 for any setCount ≥ 3.
        //
        // Fixture: 3 sessions, each 3 sets: 165×11@0RIR → 165×8@0RIR → 165×5@0RIR
        // (genuine ~3-rep fatigue per set). Failure-reps: [11, 8, 5] per session → slope -3.0.
        // A load-unit regression would produce ~-25 lbs/set and fail this assertion.
        val pulldownId = LiftId("lat-pulldown")
        val lifts = listOf(
            lift(pulldownId, "Lat Pulldown", MovementPattern.VerticalPull, MuscleGroup.Back),
        )
        val t0 = Instant.parse("2025-01-01T10:00:00Z")
        val sessions = (1..3).map { index ->
            WorkoutLoggingFixtures.importTrainingSession(
                id = "session-$index",
                startedAt = t0.plusSeconds(index * 86_400L),
                endedAt = null,
            )
        }
        val sets = buildList {
            sessions.forEach { session ->
                listOf(
                    Triple(1, 11, 0),
                    Triple(2, 8, 0),
                    Triple(3, 5, 0),
                ).forEachIndexed { i, (setIndex, reps, rir) ->
                    add(
                        importSet(
                            id = "${session.id.value}-pull-$setIndex",
                            session = session,
                            exerciseNameRaw = "Lat Pulldown",
                            liftId = pulldownId,
                            setIndex = setIndex,
                            weight = Weight(165.0, Weight.WeightUnit.Lb),
                            reps = reps,
                            rir = rir,
                            completedAt = session.startedAt.plusSeconds(1 + i * 60L),
                        ),
                    )
                }
            }
        }
        val output = TrainingInsightsExtractor.analyze(
            TrainingInsightsExtractor.AnalysisInput(sessions, sets, lifts),
        )
        val insights = output.liftInsights.first { it.liftId == pulldownId }
        // -3.0 reps/set (failure-reps 11 → 8 over set index 1 → 2). A load-unit regression on
        // e1RM at 165 lb would be roughly -25, so this pins the unit tightly.
        assertEquals(-3.0, insights.fatigueDecaySlope!!, 0.001, "Slope must be in reps/set, not load units")
        assertEquals(0.0, insights.fatigueDecayIntercept!!, 0.001, "Intercept is pinned to 0 by the decay contract")
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

    @Test
    fun `extractor handles fluctuating or reversed fatigue sets in sessions if overall trend is negative`() {
        // Test fluctuating reps: 8 -> 10 -> 7 (RIR is null).
        // Regression on [8.0, 10.0, 7.0] yields slope of -0.5.
        // Since -0.5 is negative, it should be kept as a valid fatigue slope.
        val squatId = LiftId("hack-squat")
        val lifts = listOf(
            lift(squatId, "Hack Squat", MovementPattern.SquatPattern, MuscleGroup.Quad),
        )
        val t0 = Instant.parse("2025-01-01T10:00:00Z")
        val sessions = (1..3).map { index ->
            WorkoutLoggingFixtures.importTrainingSession(
                id = "session-$index",
                startedAt = t0.plusSeconds(index * 86_400L),
                endedAt = null,
            )
        }
        val sets = buildList {
            sessions.forEach { session ->
                add(importSet("${session.id.value}-s1", session, "Hack Squat", squatId, 1, Weight(100.0, Weight.WeightUnit.Lb), 8, null, session.startedAt.plusSeconds(1)))
                add(importSet("${session.id.value}-s2", session, "Hack Squat", squatId, 2, Weight(100.0, Weight.WeightUnit.Lb), 10, null, session.startedAt.plusSeconds(61)))
                add(importSet("${session.id.value}-s3", session, "Hack Squat", squatId, 3, Weight(100.0, Weight.WeightUnit.Lb), 7, null, session.startedAt.plusSeconds(121)))
            }
        }
        val output = TrainingInsightsExtractor.analyze(
            TrainingInsightsExtractor.AnalysisInput(sessions, sets, lifts),
        )
        val insights = output.liftInsights.first { it.liftId == squatId }
        assertEquals(-0.5, insights.fatigueDecaySlope!!, 0.001, "Fluctuating slope must be kept if overall trend is negative")
    }

    @Test
    fun `extractor handles mix of valid sessions and single invalid session`() {
        // Session 1: 10 -> 8 -> 6 (slope -2.0)
        // Session 2: 10 -> 8 -> 6 (slope -2.0)
        // Session 3: 8 -> 10 -> 12 (slope +2.0)
        // Average: (-2.0 - 2.0 + 2.0) / 3 = -0.667. Since negative, it should be kept.
        val squatId = LiftId("hack-squat")
        val lifts = listOf(
            lift(squatId, "Hack Squat", MovementPattern.SquatPattern, MuscleGroup.Quad),
        )
        val t0 = Instant.parse("2025-01-01T10:00:00Z")
        val sessions = (1..3).map { index ->
            WorkoutLoggingFixtures.importTrainingSession(
                id = "session-$index",
                startedAt = t0.plusSeconds(index * 86_400L),
                endedAt = null,
            )
        }
        val sets = buildList {
            // Session 1 (Valid decay)
            val s1 = sessions[0]
            add(importSet("s1-1", s1, "Hack Squat", squatId, 1, Weight(100.0, Weight.WeightUnit.Lb), 10, null, s1.startedAt.plusSeconds(1)))
            add(importSet("s1-2", s1, "Hack Squat", squatId, 2, Weight(100.0, Weight.WeightUnit.Lb), 8, null, s1.startedAt.plusSeconds(61)))
            add(importSet("s1-3", s1, "Hack Squat", squatId, 3, Weight(100.0, Weight.WeightUnit.Lb), 6, null, s1.startedAt.plusSeconds(121)))

            // Session 2 (Valid decay)
            val s2 = sessions[1]
            add(importSet("s2-1", s2, "Hack Squat", squatId, 1, Weight(100.0, Weight.WeightUnit.Lb), 10, null, s2.startedAt.plusSeconds(1)))
            add(importSet("s2-2", s2, "Hack Squat", squatId, 2, Weight(100.0, Weight.WeightUnit.Lb), 8, null, s2.startedAt.plusSeconds(61)))
            add(importSet("s2-3", s2, "Hack Squat", squatId, 3, Weight(100.0, Weight.WeightUnit.Lb), 6, null, s2.startedAt.plusSeconds(121)))

            // Session 3 (Invalid positive slope)
            val s3 = sessions[2]
            add(importSet("s3-1", s3, "Hack Squat", squatId, 1, Weight(100.0, Weight.WeightUnit.Lb), 8, null, s3.startedAt.plusSeconds(1)))
            add(importSet("s3-2", s3, "Hack Squat", squatId, 2, Weight(100.0, Weight.WeightUnit.Lb), 10, null, s3.startedAt.plusSeconds(61)))
            add(importSet("s3-3", s3, "Hack Squat", squatId, 3, Weight(100.0, Weight.WeightUnit.Lb), 12, null, s3.startedAt.plusSeconds(121)))
        }
        val output = TrainingInsightsExtractor.analyze(
            TrainingInsightsExtractor.AnalysisInput(sessions, sets, lifts),
        )
        val insights = output.liftInsights.first { it.liftId == squatId }
        assertEquals(-0.667, insights.fatigueDecaySlope!!, 0.001, "Average slope is kept if overall average remains negative")
    }

    @Test
    fun `extractor ignores single-set sessions when computing fatigue decay`() {
        // If sessions have only 1 set, they cannot produce a regression slope.
        val squatId = LiftId("hack-squat")
        val lifts = listOf(
            lift(squatId, "Hack Squat", MovementPattern.SquatPattern, MuscleGroup.Quad),
        )
        val t0 = Instant.parse("2025-01-01T10:00:00Z")
        val sessions = (1..3).map { index ->
            WorkoutLoggingFixtures.importTrainingSession(
                id = "session-$index",
                startedAt = t0.plusSeconds(index * 86_400L),
                endedAt = null,
            )
        }
        val sets = buildList {
            sessions.forEach { session ->
                add(importSet("${session.id.value}-s1", session, "Hack Squat", squatId, 1, Weight(100.0, Weight.WeightUnit.Lb), 10, null, session.startedAt.plusSeconds(1)))
            }
        }
        val output = TrainingInsightsExtractor.analyze(
            TrainingInsightsExtractor.AnalysisInput(sessions, sets, lifts),
        )
        val insights = output.liftInsights.firstOrNull { it.liftId == squatId }
        // If there were no multi-set sessions, we should have null insights for fatigue decay.
        assertEquals(null, insights?.fatigueDecaySlope, "Fatigue slope must be null if all sessions are single-set")
    }

    @Test
    fun `extractor ignores two-set sessions when computing fatigue slope`() {
        // A 2-point OLS is an exact line through two points (zero residual) — RIR noise maps
        // 1:1 to the slope. A top set at RPE 8 (RIR 2, 14 failure-reps) followed by a failure
        // set at RPE 10 (0 RIR, 7 failure-reps) would yield a slope of −7.0, which is noise.
        // Requiring ≥3 sets per session ensures a real fit with residuals.
        val curlId = LiftId("seated-leg-curl")
        val lifts = listOf(
            lift(curlId, "Seated Leg Curl", MovementPattern.KneeFlexion, MuscleGroup.Hamstring),
        )
        val t0 = Instant.parse("2025-01-01T10:00:00Z")
        val sessions = (1..3).map { index ->
            WorkoutLoggingFixtures.importTrainingSession(
                id = "session-$index",
                startedAt = t0.plusSeconds(index * 86_400L),
                endedAt = null,
            )
        }
        val sets = buildList {
            sessions.forEach { session ->
                // 2-set session: 18 reps @ RPE 8 (RIR 2 → FR 20) → 13 reps @ RPE 10 (RIR 0 → FR 13).
                // A 2-point OLS would give slope −7.0 (noise, not fatigue).
                add(importSet("${session.id.value}-s1", session, "Seated Leg Curl", curlId, 1, Weight(160.0, Weight.WeightUnit.Lb), 18, 2, session.startedAt.plusSeconds(1)))
                add(importSet("${session.id.value}-s2", session, "Seated Leg Curl", curlId, 2, Weight(160.0, Weight.WeightUnit.Lb), 13, 0, session.startedAt.plusSeconds(61)))
            }
        }
        val output = TrainingInsightsExtractor.analyze(
            TrainingInsightsExtractor.AnalysisInput(sessions, sets, lifts),
        )
        val insights = output.liftInsights.firstOrNull { it.liftId == curlId }
        assertEquals(null, insights?.fatigueDecaySlope, "2-set sessions must not contribute a fatigue slope")
    }

    @Test
    fun `extractor discards slopes steeper than the plausible fatigue bound`() {
        // Even with 3+ sets, a steep slope (e.g. −5.0) is RIR noise, not physiology. Real
        // fatigue ranges from −1.0 to −4.0 reps/set; anything steeper must be discarded so
        // downstream rep-range generation falls back to the population default instead of
        // producing absurd rep floors (e.g. repMin 14 for a 3-set lift from a −5.0 slope).
        val curlId = LiftId("leg-curl-extreme")
        val lifts = listOf(
            lift(curlId, "Leg Curl", MovementPattern.KneeFlexion, MuscleGroup.Hamstring),
        )
        val t0 = Instant.parse("2025-01-01T10:00:00Z")
        val sessions = (1..3).map { index ->
            WorkoutLoggingFixtures.importTrainingSession(
                id = "session-$index",
                startedAt = t0.plusSeconds(index * 86_400L),
                endedAt = null,
            )
        }
        val sets = buildList {
            sessions.forEach { session ->
                // 3-set session with a −5.0 slope: FR 20 → 15 → 10 (slope = −5.0).
                // This exceeds MAX_FATIGUE_SLOPE_MAGNITUDE (4.0) and must be discarded.
                add(importSet("${session.id.value}-s1", session, "Leg Curl", curlId, 1, Weight(100.0, Weight.WeightUnit.Lb), 18, 2, session.startedAt.plusSeconds(1)))
                add(importSet("${session.id.value}-s2", session, "Leg Curl", curlId, 2, Weight(100.0, Weight.WeightUnit.Lb), 13, 2, session.startedAt.plusSeconds(61)))
                add(importSet("${session.id.value}-s3", session, "Leg Curl", curlId, 3, Weight(100.0, Weight.WeightUnit.Lb), 8, 2, session.startedAt.plusSeconds(121)))
            }
        }
        val output = TrainingInsightsExtractor.analyze(
            TrainingInsightsExtractor.AnalysisInput(sessions, sets, lifts),
        )
        val insights = output.liftInsights.firstOrNull { it.liftId == curlId }
        assertEquals(null, insights?.fatigueDecaySlope, "Slope steeper than the plausible bound must be discarded")
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
