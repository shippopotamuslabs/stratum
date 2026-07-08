package com.stratum.domain.response

import com.stratum.domain.response.fixtures.ResponseFixtures
import com.stratum.domain.lift.Weight
import com.stratum.domain.onboarding.MuscleGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class TrainingSignalCleanerTest {
    @Test
    @DisplayName("RP-S1-001 working set retained")
    fun rpS1_001_workingSetRetained() {
        val input = ResponseFixtures.fixS1Working()
        val output = TrainingSignalCleaner.clean(input)

        assertEquals(1, output.size)
        val row = output.single()
        assertTrue(row.isWorkingSet)
        assertEquals(100.0, row.normalizedWeight)
        assertTrue(row.estimatedOneRm > 100.0)
        assertFalse(row.qualityFlags.contains(TrainingSignalQualityFlag.HighRirWarmupExcluded))
    }

    @Test
    @DisplayName("RP-S1-002 high RIR warmup excluded")
    fun rpS1_002_highRirWarmupExcluded() {
        val input = ResponseFixtures.fixS1WarmupRir()
        val output = TrainingSignalCleaner.clean(input)

        assertEquals(1, output.count { it.isWorkingSet })
        val excluded = output.first { !it.isWorkingSet }
        assertFalse(excluded.isWorkingSet)
        assertTrue(excluded.qualityFlags.contains(TrainingSignalQualityFlag.HighRirWarmupExcluded))
        assertEquals(
            1,
            excluded.qualityFlags.count { it == TrainingSignalQualityFlag.HighRirWarmupExcluded },
        )
    }

    @Test
    @DisplayName("RP-S1-003 low relative load warmup excluded")
    fun rpS1_003_lowRelativeLoadWarmupExcluded() {
        val input = ResponseFixtures.fixS1WarmupLoad()
        val output = TrainingSignalCleaner.clean(input)

        assertEquals(1, output.count { it.isWorkingSet })
        assertEquals(1, output.count { !it.isWorkingSet })
        val excluded = output.first { !it.isWorkingSet }
        assertTrue(excluded.qualityFlags.contains(TrainingSignalQualityFlag.LowRelativeLoadWarmupExcluded))
    }

    @Test
    @DisplayName("RP-S1-004 RIR-adjusted e1RM populated")
    fun rpS1_004_rirAdjustedE1rmPopulated() {
        val input = ResponseFixtures.fixS1Working()
        val row = TrainingSignalCleaner.clean(input).single()

        assertNotNull(row.rirAdjustedEstimatedOneRm)
        assertTrue(row.rirAdjustedEstimatedOneRm!! > row.estimatedOneRm)
    }

    @Test
    @DisplayName("RP-S1-005 missing RIR flagged")
    fun rpS1_005_missingRirFlagged() {
        val input = ResponseFixtures.fixS1Working().copy(
            sets =
                listOf(
                    ResponseFixtures.fixS1Working().sets.single().copy(
                        rir = null,
                    ),
                ),
        )
        val row = TrainingSignalCleaner.clean(input).single()

        assertTrue(row.qualityFlags.contains(TrainingSignalQualityFlag.MissingRir))
        assertNull(row.rirAdjustedEstimatedOneRm)
    }

    @Test
    @DisplayName("RP-S1-006 mixed units flagged")
    fun rpS1_006_mixedUnitsFlagged() {
        val input = ResponseFixtures.fixS1MixedUnit()
        val output = TrainingSignalCleaner.clean(input)

        assertTrue(output.any { it.qualityFlags.contains(TrainingSignalQualityFlag.MixedUnits) })
    }

    @Test
    @DisplayName("RP-S1-M001 RIR at or above the threshold (4) is excluded not working")
    fun rpS1_M001_rirAtThresholdExcluded() {
        val base = ResponseFixtures.fixS1Working()
        val input =
            base.copy(
                sets =
                    listOf(
                        base.sets.single().copy(
                            weight = Weight(80.0, Weight.WeightUnit.Kg),
                            reps = 8,
                            rir = 4,
                        ),
                    ),
            )
        val row = TrainingSignalCleaner.clean(input).single()

        assertFalse(row.isWorkingSet)
        assertTrue(row.qualityFlags.contains(TrainingSignalQualityFlag.HighRirWarmupExcluded))
    }

    @Test
    @DisplayName("RP-S1-M002 RIR 3 (RPE 7) is retained as a working set")
    fun rpS1_M002_rirBelowThresholdRetained() {
        val base = ResponseFixtures.fixS1Working()
        val input =
            base.copy(
                sets =
                    listOf(
                        base.sets.single().copy(
                            weight = Weight(80.0, Weight.WeightUnit.Kg),
                            reps = 8,
                            rir = 3,
                        ),
                    ),
            )
        val row = TrainingSignalCleaner.clean(input).single()

        assertTrue(row.isWorkingSet)
        assertFalse(row.qualityFlags.contains(TrainingSignalQualityFlag.HighRirWarmupExcluded))
    }
}
