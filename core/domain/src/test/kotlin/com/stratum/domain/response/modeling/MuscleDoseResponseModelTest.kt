package com.stratum.domain.response.modeling

import com.stratum.domain.importing.GlobalVolumePriors
import com.stratum.domain.importing.IntervalMethod
import com.stratum.domain.importing.LandmarkConfidenceTier
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class MuscleDoseResponseModelTest {
    @Test
    @DisplayName("MDM-001 insufficient data falls back toward priors")
    fun insufficientDataUsesPriorBlend() {
        val pairing =
            LaggedDoseResponsePairer.PairingResult(
                points = emptyList(),
                selectedLagWeeks = 0,
                responseWindowWeeks = 4,
                predictiveScore = null,
            )
        val result =
            MuscleDoseResponseModel.estimate(
                muscle = MuscleGroup.Chest,
                pairing = pairing,
                indices = emptyList(),
                stimulus = emptyList(),
                diagnosticAnchorLiftId = null,
            )

        assertEquals(OutcomeModelType.PriorOnly, result.snapshot.outcomeModelType)
        assertTrue(result.snapshot.confidenceTier == LandmarkConfidenceTier.Low)
        assertTrue(result.snapshot.retentionFloorMv.isFinite())
        assertTrue(result.snapshot.paretoVolumePv.isFinite())
        assertTrue(result.snapshot.recoverabilityCeilingRc.isFinite())
        assertTrue(result.snapshot.growthEntryFloor.isFinite())
        assertTrue(result.diagnostics.confidenceScore.isFinite())
    }

    @Test
    @DisplayName("MDM-003 empty outcome indices still produce persistable finite snapshots")
    fun emptyOutcomeIndicesProduceFiniteSnapshots() {
        val pairing =
            LaggedDoseResponsePairer.PairingResult(
                points = emptyList(),
                selectedLagWeeks = 0,
                responseWindowWeeks = 4,
                predictiveScore = null,
            )
        val result =
            MuscleDoseResponseModel.estimate(
                muscle = MuscleGroup.Tricep,
                pairing = pairing,
                indices = emptyList(),
                stimulus = emptyList(),
                diagnosticAnchorLiftId = null,
            )

        assertTrue(result.snapshot.retentionFloorMv.isFinite() && result.snapshot.retentionFloorMv > 0.0)
        assertTrue(result.snapshot.pvInterval.low.isFinite())
        assertTrue(result.snapshot.pvInterval.high.isFinite())
    }

    @Test
    @DisplayName("MDM-002 sqrt fit recovers snapshots from synthetic dose points")
    fun sqrtFitRecoversSnapshots() {
        val points =
            (0 until 12).map { index ->
                val dose = 6.0 + index
                val response = 0.01 - kotlin.math.abs(dose - 14.0) * 0.002
                MuscleDoseResponsePoint(
                    muscle = MuscleGroup.Chest,
                    weekStart = java.time.Instant.parse("2025-01-06T00:00:00Z").plusSeconds(index * 7L * 24 * 3600),
                    dose = dose,
                    response = response,
                    lagWeeks = 1,
                    responseWindowWeeks = 4,
                )
            }
        val pairing =
            LaggedDoseResponsePairer.PairingResult(
                points = points,
                selectedLagWeeks = 1,
                responseWindowWeeks = 4,
                predictiveScore = -0.1,
            )
        val indices =
            points.map {
                MuscleWeeklyOutcomeIndex(
                    muscle = MuscleGroup.Chest,
                    weekStart = it.weekStart,
                    indexValue = it.response,
                    indexStdError = 0.01,
                    contributingLiftCount = 2,
                    effectiveLiftCount = 2,
                    primaryCoverage = 0.8,
                    compositionHash = "bench:10|fly:5",
                    compositionDrift = 0.05,
                    qualityFlags = emptySet(),
                )
            }
        val stimulus =
            points.map {
                MuscleWeeklyStimulus(
                    muscle = MuscleGroup.Chest,
                    weekStart = it.weekStart,
                    hardSets = it.dose,
                    sessions = 2,
                    meanRir = 2.0,
                    intensityProxy = 0.8,
                    compositionHash = "bench:10|fly:5",
                    deloadFlag = false,
                    qualityFlags = emptySet(),
                )
            }
        val result =
            MuscleDoseResponseModel.estimate(
                muscle = MuscleGroup.Chest,
                pairing = pairing,
                indices = indices,
                stimulus = stimulus,
                diagnosticAnchorLiftId = null,
            )

        assertTrue(result.snapshot.retentionFloorMv <= result.snapshot.recoverabilityCeilingRc)
        assertTrue(result.snapshot.paretoVolumePv <= result.snapshot.recoverabilityCeilingRc)
        assertTrue(
            result.snapshot.pvInterval.method == IntervalMethod.Pragmatic ||
                result.snapshot.pvInterval.method == IntervalMethod.EpochBlockedBootstrap ||
                result.snapshot.pvInterval.isAvailable,
        )
    }

    @Test
    @DisplayName("MDM-004 high logged stimulus raises RC and PV even when dose-response fit is rejected")
    fun highLoggedStimulusPersonalizesVolumesDespiteRejection() {
        val stimulus =
            (0 until 16).map { index ->
                val hardSets = 6.0 + (index % 12) * 2.0
                MuscleWeeklyStimulus(
                    muscle = MuscleGroup.Tricep,
                    weekStart = java.time.Instant.parse("2025-01-06T00:00:00Z").plusSeconds(index * 7L * 24 * 3600),
                    hardSets = hardSets,
                    sessions = 2,
                    meanRir = 2.0,
                    intensityProxy = 0.8,
                    compositionHash = "pushdown:10",
                    deloadFlag = false,
                    qualityFlags = emptySet(),
                )
            }
        val points =
            stimulus.take(4).map {
                MuscleDoseResponsePoint(
                    muscle = MuscleGroup.Tricep,
                    weekStart = it.weekStart,
                    dose = it.hardSets,
                    response = 0.01,
                    lagWeeks = 1,
                    responseWindowWeeks = 4,
                )
            }
        val pairing =
            LaggedDoseResponsePairer.PairingResult(
                points = points,
                selectedLagWeeks = 1,
                responseWindowWeeks = 4,
                predictiveScore = null,
            )
        val result =
            MuscleDoseResponseModel.estimate(
                muscle = MuscleGroup.Tricep,
                pairing = pairing,
                indices = emptyList(),
                stimulus = stimulus,
                diagnosticAnchorLiftId = LiftId("tricep-pushdown"),
            )

        assertTrue(result.snapshot.recoverabilityCeilingRc > GlobalVolumePriors.RECOVERABILITY_CEILING_RC)
        assertTrue(result.snapshot.paretoVolumePv > GlobalVolumePriors.PARETO_VOLUME_PV)
        assertTrue(result.snapshot.observedDoseMax >= 26.0)
    }
}
