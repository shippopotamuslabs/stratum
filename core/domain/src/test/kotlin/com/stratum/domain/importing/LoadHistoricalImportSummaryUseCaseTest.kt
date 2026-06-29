package com.stratum.domain.importing

import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.response.FakeLiftEffectivenessObservationRepository
import com.stratum.core.testing.response.FakeMuscleVolumeLandmarkEstimateRepository
import com.stratum.core.testing.response.FakeVolumeResponseObservationRepository
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.EvidenceLevel
import com.stratum.domain.response.LiftEffectivenessObservation
import com.stratum.domain.response.OutcomeDescriptor
import com.stratum.domain.response.OutcomeMetric
import com.stratum.domain.response.RegressionReliability
import com.stratum.domain.response.StimulusDescriptor
import com.stratum.domain.response.VolumeResponseObservation
import com.stratum.domain.response.WeeklySetRange
import com.stratum.domain.response.identity.EpochId
import com.stratum.domain.response.modeling.MrvSource
import com.stratum.domain.response.modeling.MuscleVolumeLandmarkEstimate
import com.stratum.domain.response.modeling.OutcomeModelType
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LoadHistoricalImportSummaryUseCaseTest {
    @Test
    @DisplayName("Given no analysis metadata when invoked then returns null")
    fun returnsNullWithoutMetadata() = runTest {
        val useCase = buildUseCase(metadata = null)

        assertNull(useCase())
    }

    @Test
    @DisplayName("Given completed import when invoked then aggregates persisted insights")
    fun aggregatesPersistedInsights() = runTest {
        val metadata =
            ImportAnalysisMetadata(
                windowStart = Instant.parse("2025-01-01T00:00:00Z"),
                windowEnd = Instant.parse("2025-02-15T00:00:00Z"),
                provenLiftIds = listOf(LiftId("bench-press")),
                unmatchedExerciseNames = emptyList(),
                totalSessions = 12,
                totalSets = 240,
            )
        val useCase =
            buildUseCase(
                metadata = metadata,
                baselines =
                    listOf(
                        MuscleVolumeBaseline(
                            muscle = MuscleGroup.Chest,
                            meanWeeklyHardSets = 12.0,
                            p90WeeklyHardSets = 16.0,
                            trendSlope = null,
                        ),
                        MuscleVolumeBaseline(
                            muscle = MuscleGroup.Back,
                            meanWeeklyHardSets = 8.0,
                            p90WeeklyHardSets = 10.0,
                            trendSlope = null,
                        ),
                    ),
                lifts =
                    listOf(
                        sampleLift("bench-press", enabled = true),
                        sampleLift("pull-up", enabled = true, isCustom = true),
                    ),
                effectiveness =
                    listOf(
                        LiftEffectivenessObservation(
                            pattern = MovementPattern.HorizontalPress,
                            liftId = LiftId("bench-press"),
                            epochId = EpochId("epoch-1"),
                            effectivenessScore = 1.2,
                            peerLiftCount = 2,
                            rank = 1,
                            evidence = EvidenceLevel.Proven,
                            confidenceScore = 0.9,
                        ),
                    ),
                volumeResponses =
                    listOf(
                        VolumeResponseObservation(
                            muscle = MuscleGroup.Chest,
                            epochId = EpochId("epoch-1"),
                            anchorLiftId = LiftId("bench-press"),
                            stimulus =
                                StimulusDescriptor(
                                    epochId = EpochId("epoch-1"),
                                    experimentId = null,
                                    muscle = MuscleGroup.Chest,
                                    pattern = null,
                                    liftId = LiftId("bench-press"),
                                    weeklyHardSets = 12.0,
                                    sessionsPerWeek = 2.0,
                                    intensityProxy = null,
                                ),
                            outcome =
                                OutcomeDescriptor(
                                    anchorLiftId = LiftId("bench-press"),
                                    metric = OutcomeMetric.WeeklyPeakE1rm,
                                    value = 225.0,
                                    windowWeeks = 6,
                                    reliability = RegressionReliability(null, 0, 0.8),
                                ),
                            correlationSlope = 1.5,
                            confidenceScore = 0.82,
                            evidence = EvidenceLevel.Hypothesis,
                            weeklyPointsUsed = 6,
                            epochWeeksUsed = 6,
                            observedWeeklySetRange = WeeklySetRange(8.0, 16.0),
                            bestObservedWeeklySetRange = null,
                        ),
                    ),
            )

        val summary = useCase()!!

        assertEquals(12, summary.totalSessions)
        assertEquals(240, summary.totalSets)
        assertEquals(7, summary.analysisWeeks)
        assertEquals(2, summary.enabledLiftCount)
        assertEquals(1, summary.provenLiftCount)
        assertEquals(1, summary.customLiftsCreated)
        assertEquals(1, summary.effectiveLifts.size)
        assertEquals("Bench press", summary.effectiveLifts.first().liftName)
        assertEquals(MuscleGroup.Chest, summary.effectiveLifts.first().muscle)
        assertEquals(1, summary.volumeResponses.size)
        assertEquals(2, summary.volumeHighlights.size)
        assertTrue(summary.effectiveLifts.first().effectivenessScore > 0.0)
        assertEquals(LandmarkConfidenceTier.Medium, summary.effectiveLifts.first().confidenceTier)
    }

    private fun buildUseCase(
        metadata: ImportAnalysisMetadata?,
        baselines: List<MuscleVolumeBaseline> = emptyList(),
        lifts: List<Lift> = emptyList(),
        effectiveness: List<LiftEffectivenessObservation> = emptyList(),
        volumeResponses: List<VolumeResponseObservation> = emptyList(),
    ): LoadHistoricalImportSummaryUseCase {
        val metadataRepository = FakeImportAnalysisMetadataRepository(metadata)
        val volumeRepository = FakeMuscleVolumeBaselineRepository(baselines)
        val liftInsightsRepository = FakeLiftTrainingInsightsRepository()
        val supersetRepository = FakeSupersetAffinityRepository()
        val volumeResponseRepository = FakeVolumeResponseObservationRepository().apply { seed(volumeResponses) }
        val effectivenessRepository = FakeLiftEffectivenessObservationRepository().apply { seed(effectiveness) }
        val liftRepository = FakeLiftRepository().apply { seedLifts(lifts) }
        val landmarkRepository = FakeMuscleVolumeLandmarkEstimateRepository().apply {
            seed(
                listOf(
                    sampleLandmark(MuscleGroup.Chest),
                    sampleLandmark(MuscleGroup.Back),
                ),
            )
        }
        return LoadHistoricalImportSummaryUseCase(
            metadataRepository,
            volumeRepository,
            liftInsightsRepository,
            supersetRepository,
            volumeResponseRepository,
            effectivenessRepository,
            landmarkRepository,
            liftRepository,
        )
    }

    private fun sampleLandmark(muscle: MuscleGroup): MuscleVolumeLandmarkEstimate =
        MuscleVolumeLandmarkEstimate(
            muscle = muscle,
            estimatedMev = 6.0,
            estimatedMav = 12.0,
            estimatedMrv = 18.0,
            estimatedPv = 9.0,
            mevInterval = LandmarkConfidenceInterval.Unavailable,
            mavInterval = LandmarkConfidenceInterval.Unavailable,
            mrvInterval = LandmarkConfidenceInterval.Unavailable,
            pvInterval = LandmarkConfidenceInterval.Unavailable,
            confidenceTier = LandmarkConfidenceTier.Medium,
            outcomeModelType = OutcomeModelType.MuscleIndex,
            mrvSource = MrvSource.Observed,
            observedDoseMin = 4.0,
            observedDoseMax = 16.0,
            diagnosticAnchorLiftId = null,
        )

    private fun sampleLift(
        slug: String,
        enabled: Boolean,
        isCustom: Boolean = false,
    ): Lift =
        Lift(
            id = LiftId(slug),
            name = slug.replace('-', ' ').replaceFirstChar { it.uppercase() },
            movementPatterns = setOf(MovementPattern.HorizontalPress),
            primaryMuscles = setOf(MuscleGroup.Chest),
            secondaryMuscles = emptySet(),
            allowWeightChangeBetweenSets = false,
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            enabled = enabled,
            isCustom = isCustom,
        )

    private class FakeImportAnalysisMetadataRepository(
        private var stored: ImportAnalysisMetadata?,
    ) : ImportAnalysisMetadataRepository {
        override suspend fun save(metadata: ImportAnalysisMetadata) {
            stored = metadata
        }

        override suspend fun get(): ImportAnalysisMetadata? = stored

        override suspend fun clear() {
            stored = null
        }
    }

    private class FakeMuscleVolumeBaselineRepository(
        private val baselines: List<MuscleVolumeBaseline>,
    ) : MuscleVolumeBaselineRepository {
        override suspend fun replaceAll(baselines: List<MuscleVolumeBaseline>) = Unit

        override suspend fun getAll(): List<MuscleVolumeBaseline> = baselines
    }

    private class FakeLiftTrainingInsightsRepository : LiftTrainingInsightsRepository {
        override suspend fun replaceAll(insights: List<LiftTrainingInsights>) = Unit

        override suspend fun getAll(): List<LiftTrainingInsights> = emptyList()

        override suspend fun getByLiftId(liftId: LiftId): LiftTrainingInsights? = null
    }

    private class FakeSupersetAffinityRepository : SupersetAffinityRepository {
        override suspend fun replaceAll(affinities: List<SupersetAffinity>) = Unit

        override suspend fun getAll(): List<SupersetAffinity> = emptyList()
    }
}
