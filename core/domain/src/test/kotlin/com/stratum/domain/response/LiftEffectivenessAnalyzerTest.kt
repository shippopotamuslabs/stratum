package com.stratum.domain.response

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.core.testing.response.ResponseFixtures
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import com.stratum.domain.importing.ImportHistoryTrainingMapper
import com.stratum.domain.importing.identity.LoggedSetId
import com.stratum.domain.lift.Lift
import com.stratum.domain.training.identity.TrainingSessionId

class LiftEffectivenessAnalyzerTest {
    @Test
    @DisplayName("RP-S6-001 fewer than 6 sessions excluded")
    fun rpS6_001_fewerThanSixSessionsExcluded() {
        val input = ResponseFixtures.fixS6Pattern3()
        val epochs =
            listOf(
                ResponseFixtures.epoch(
                    id = "s6-gate",
                    start = input.analysisWindowStart,
                    end = input.analysisWindowEnd,
                ),
            )
        val observations =
            LiftEffectivenessAnalyzer.analyze(
                pattern = MovementPattern.HorizontalPress,
                epoch = epochs.single(),
                input = input,
                deloadWeeks = emptySet(),
            )
        val inclineObs = observations.filter { it.liftId == ResponseFixtures.liftIncline }

        assertEquals(0, inclineObs.size)
    }

    @Test
    @DisplayName("RP-S6-002 single peer no rank")
    fun rpS6_002_singlePeerNoRank() {
        val input = ResponseFixtures.fixS6SingleLift()
        val epochs =
            listOf(
                ResponseFixtures.epoch(
                    id = "s6-single",
                    start = input.analysisWindowStart,
                    end = input.analysisWindowEnd,
                ),
            )
        val observations =
            LiftEffectivenessAnalyzer.analyze(
                pattern = MovementPattern.HorizontalPress,
                epoch = epochs.single(),
                input = input,
                deloadWeeks = emptySet(),
            )

        assertEquals(1, observations.size)
        assertEquals(1, observations[0].rank)
        assertEquals(1, observations[0].peerLiftCount)
        assertEquals(EvidenceLevel.Hypothesis, observations[0].evidence)
    }

    @Test
    @DisplayName("RP-S6-003 rank margin enforced")
    fun rpS6_003_rankMarginEnforced() {
        val input = ResponseFixtures.fixS6Pattern3()
        val epochs =
            listOf(
                ResponseFixtures.epoch(
                    id = "s6-margin",
                    start = input.analysisWindowStart,
                    end = input.analysisWindowEnd,
                ),
            )
        val observations =
            LiftEffectivenessAnalyzer.analyze(
                pattern = MovementPattern.HorizontalPress,
                epoch = epochs.single(),
                input = input,
                deloadWeeks = emptySet(),
            )
        val rank1 = observations.first { it.rank == 1 }

        assertEquals(0.12705640902543433, rank1.effectivenessScore, 0.001)
        assertEquals(EvidenceLevel.Hypothesis, rank1.evidence)
        assertTrue(observations.none { it.rank == 1 && it.evidence == EvidenceLevel.Proven })
    }

    @Test
    @DisplayName("RP-S6-004 cross-epoch rank stability")
    fun rpS6_004_crossEpochRankStability() {
        val input = ResponseFixtures.fixS5DualEpoch()
        val epochs = EpochSplitter.split(input)
        val perEpoch =
            epochs.flatMap { epoch ->
                LiftEffectivenessAnalyzer.analyze(
                    pattern = MovementPattern.HorizontalPress,
                    epoch = epoch,
                    input = input,
                    deloadWeeks = emptySet(),
                )
            }
        val benchObs = perEpoch.filter { it.liftId == ResponseFixtures.liftBench }
        val mergedEvidence = ResponseEvidenceMerger.mergeLiftEffectivenessEvidence(benchObs)

        assertEquals(EvidenceLevel.Proven, mergedEvidence)
        assertEquals(1, benchObs.last().rank)
    }

    @Test
    @DisplayName("RP-S6-005 patterns ranked independently")
    fun rpS6_005_patternsRankedIndependently() {
        val input = ResponseFixtures.fixS6WithVerticalPull()
        val epoch =
            ResponseFixtures.epoch(
                id = "s6-cross",
                start = input.analysisWindowStart,
                end = input.analysisWindowEnd,
            )
        val horizontal =
            LiftEffectivenessAnalyzer.analyze(
                pattern = MovementPattern.HorizontalPress,
                epoch = epoch,
                input = input,
                deloadWeeks = emptySet(),
            )
        val vertical =
            LiftEffectivenessAnalyzer.analyze(
                pattern = MovementPattern.VerticalPull,
                epoch = epoch,
                input = input,
                deloadWeeks = emptySet(),
            )
        val horizontalRank1 = horizontal.first { it.rank == 1 }
        val verticalRank1 = vertical.first { it.rank == 1 }

        assertNotEquals(horizontalRank1.liftId, verticalRank1.liftId)
        assertEquals(MovementPattern.HorizontalPress, horizontalRank1.pattern)
        assertEquals(MovementPattern.VerticalPull, verticalRank1.pattern)
    }

    @Test
    @DisplayName("RP-S6-006 volume-correlated lift outranks flat high-volume lift")
    fun rpS6_006_volumeCorrelatedLiftOutranksFlatHighVolume() {
        // Lift A: weekly hard sets climb 1..8 and e1RM climbs with them (true correlation).
        // Lift B: weekly hard sets climb 1..8 but e1RM is flat (no correlation).
        // The OLS effectiveness score should rank A above B; the old endpoint-ratio could not.
        val correlatedLift = LiftId("lift-correlated")
        val flatLift = LiftId("lift-flat")
        val baseLifts =
            listOf(
                fixtureLift(correlatedLift, "Correlated Press"),
                fixtureLift(flatLift, "Flat Press"),
            )
        val baseInstant = Instant.parse("2025-01-06T10:00:00Z")
        val sessions = (0 until 8).map { week ->
            com.stratum.core.testing.training.WorkoutLoggingFixtures.importTrainingSession(
                id = "vol-week-$week",
                startedAt = baseInstant.plus(week * 7L, ChronoUnit.DAYS),
                endedAt = null,
                sourceApp = "fixture",
            )
        }
        // Each week both lifts get (week+1) working sets — volume grows identically.
        // A's weight climbs 100..114; B's weight stays at 100. This isolates outcome response.
        val sets = sessions.flatMapIndexed { week, session ->
            val setCount = week + 1
            (0 until setCount).flatMap { setIndex ->
                listOf(
                    com.stratum.domain.importing.ImportHistoryTrainingMapper.parseSet(
                        id = com.stratum.domain.importing.identity.LoggedSetId("vol-a-$week-$setIndex"),
                        sessionId = session.id,
                        exerciseNameRaw = "Fixture",
                        setIndex = setIndex + 1,
                        weight = Weight(100.0 + week * 2.0, Weight.WeightUnit.Kg),
                        reps = 5,
                        rir = 2,
                        completedAt = session.startedAt.plusSeconds((setIndex * 90).toLong()),
                    ).copy(liftId = correlatedLift),
                    com.stratum.domain.importing.ImportHistoryTrainingMapper.parseSet(
                        id = com.stratum.domain.importing.identity.LoggedSetId("vol-b-$week-$setIndex"),
                        sessionId = session.id,
                        exerciseNameRaw = "Fixture",
                        setIndex = setIndex + 1,
                        weight = Weight(100.0, Weight.WeightUnit.Kg),
                        reps = 5,
                        rir = 2,
                        completedAt = session.startedAt.plusSeconds(60L + (setIndex * 90).toLong()),
                    ).copy(liftId = flatLift),
                )
            }
        }
        val input =
            ResponseExtractionInput(
                sessions = sessions,
                sets = sets,
                lifts = baseLifts,
                provenLiftIds = emptySet(),
                specializeMuscles = emptySet(),
                liftInsights = emptyList(),
                volumeBaselines = emptyList(),
                analysisWindowStart = sessions.first().startedAt,
                analysisWindowEnd = sessions.last().startedAt.plus(1, ChronoUnit.DAYS),
                activeExperiments = emptyList(),
            )
        val epoch =
            ResponseFixtures.epoch(
                id = "s6-vol-corr",
                start = input.analysisWindowStart,
                end = input.analysisWindowEnd,
            )
        val observations =
            LiftEffectivenessAnalyzer.analyze(
                pattern = MovementPattern.HorizontalPress,
                epoch = epoch,
                input = input,
                deloadWeeks = emptySet(),
            )

        assertTrue(observations.any { it.liftId == correlatedLift }, "correlated lift must be scored")
        assertTrue(observations.any { it.liftId == flatLift }, "flat lift must be scored")
        val correlated = observations.first { it.liftId == correlatedLift }
        val flat = observations.first { it.liftId == flatLift }
        assertTrue(correlated.rank < flat.rank, "Correlated lift should rank above flat lift")
        assertTrue(correlated.effectivenessScore > flat.effectivenessScore)
    }

    private fun fixtureLift(id: LiftId, name: String): com.stratum.domain.lift.Lift =
        com.stratum.domain.lift.Lift(
            id = id,
            name = name,
            movementPatterns = setOf(MovementPattern.HorizontalPress),
            primaryMuscles = setOf(MuscleGroup.Chest),
            secondaryMuscles = emptySet(),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = true,
        )

    @Test
    @DisplayName("WL-043 LiftEffectivenessAnalyzer filters by liftId using snapshot movement pattern")
    fun wl043_effectivenessFromSnapshot() {
        val sets = listOf(
            com.stratum.core.testing.training.WorkoutLoggingFixtures.loggedSet(
                id = "set-1",
                sessionId = com.stratum.domain.training.identity.TrainingSessionId("ts-1"),
            ),
        )

        val score = LiftEffectivenessAnalyzer.effectivenessFromSnapshot(
            pattern = MovementPattern.HorizontalPress,
            sets = sets,
            liftId = com.stratum.core.testing.training.WorkoutLoggingFixtures.liftBenchId,
        )

        assertTrue(score > 0.0)
    }
}
