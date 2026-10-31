package com.stratum.core.infrastructure.db

import androidx.room.Room
import com.stratum.core.infrastructure.repository.RoomExperimentBlockRepository
import com.stratum.core.infrastructure.repository.RoomLiftEffectivenessObservationRepository
import com.stratum.core.infrastructure.repository.RoomResponseEpochRepository
import com.stratum.core.infrastructure.repository.RoomResponseProfileMetadataRepository
import com.stratum.core.infrastructure.repository.RoomVolumeResponseObservationRepository
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.response.EpochSplitReason
import com.stratum.domain.response.EvidenceLevel
import com.stratum.domain.response.ExperimentBlock
import com.stratum.domain.response.ExperimentStatus
import com.stratum.domain.response.LiftEffectivenessObservation
import com.stratum.domain.response.OutcomeDescriptor
import com.stratum.domain.response.OutcomeMetric
import com.stratum.domain.response.RegressionReliability
import com.stratum.domain.response.ResponseProfileMetadata
import com.stratum.domain.response.StimulusDescriptor
import com.stratum.domain.response.VolumeResponseObservation
import com.stratum.domain.response.WeeklySetRange
import com.stratum.domain.response.identity.EpochId
import com.stratum.domain.response.identity.ExperimentId
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
class ResponseDaoTest {
    private lateinit var database: StratumDatabase
    private lateinit var epochRepository: RoomResponseEpochRepository
    private lateinit var volumeRepository: RoomVolumeResponseObservationRepository
    private lateinit var effectivenessRepository: RoomLiftEffectivenessObservationRepository
    private lateinit var experimentRepository: RoomExperimentBlockRepository
    private lateinit var metadataRepository: RoomResponseProfileMetadataRepository

    @BeforeEach
    fun setup() {
        database =
            Room.inMemoryDatabaseBuilder(
                RuntimeEnvironment.getApplication(),
                StratumDatabase::class.java,
            ).allowMainThreadQueries().build()
        epochRepository = RoomResponseEpochRepository(database, database.responseEpochDao())
        volumeRepository = RoomVolumeResponseObservationRepository(database, database.volumeResponseObservationDao())
        effectivenessRepository =
            RoomLiftEffectivenessObservationRepository(database, database.liftEffectivenessObservationDao())
        experimentRepository = RoomExperimentBlockRepository(database, database.experimentBlockDao())
        metadataRepository = RoomResponseProfileMetadataRepository(database.responseProfileMetadataDao())
    }

    @AfterEach
    fun tearDown() {
        database.close()
    }

    @Test
    @DisplayName("RP-S8-001 epoch persistence")
    fun rpS8_001_epochPersistence() = runTest {
        val epoch =
            com.stratum.domain.response.Epoch(
                id = EpochId("epoch-1"),
                start = Instant.parse("2025-01-01T00:00:00Z"),
                end = Instant.parse("2025-06-01T00:00:00Z"),
                anchorLiftIds = setOf(LiftId("lift-bench")),
                splitReason = EpochSplitReason.SessionGap,
            )

        epochRepository.replaceAll(listOf(epoch))
        val stored = epochRepository.getAll()

        assertEquals(1, stored.size)
        assertEquals("epoch-1", stored[0].id.value)
        assertEquals(EpochSplitReason.SessionGap, stored[0].splitReason)
    }

    @Test
    @DisplayName("RP-S8-002 volume observation persistence")
    fun rpS8_002_volumeObservationPersistence() = runTest {
        val observation = sampleVolumeObservation(confidence = 0.70, evidence = EvidenceLevel.Hypothesis)

        volumeRepository.replaceAll(listOf(observation))
        val stored = volumeRepository.getAll()

        assertEquals(MuscleGroup.Quad, stored[0].muscle)
        assertEquals(EvidenceLevel.Hypothesis, stored[0].evidence)
        assertEquals(0.70, stored[0].confidenceScore)
    }

    @Test
    @DisplayName("RP-S8-003 effectiveness observation persistence")
    fun rpS8_003_effectivenessObservationPersistence() = runTest {
        val observation =
            LiftEffectivenessObservation(
                pattern = MovementPattern.HorizontalPress,
                liftId = LiftId("lift-bench"),
                epochId = EpochId("epoch-1"),
                effectivenessScore = 0.12,
                peerLiftCount = 2,
                rank = 1,
                evidence = EvidenceLevel.Proven,
                confidenceScore = 0.9,
            )

        effectivenessRepository.replaceAll(listOf(observation))
        val stored = effectivenessRepository.getAll()

        assertEquals(MovementPattern.HorizontalPress, stored[0].pattern)
        assertEquals(1, stored[0].rank)
        assertEquals(EvidenceLevel.Proven, stored[0].evidence)
    }

    @Test
    @DisplayName("RP-S8-004 experiment block persistence")
    fun rpS8_004_experimentBlockPersistence() = runTest {
        val block =
            ExperimentBlock(
                id = ExperimentId("exp-1"),
                prescribed =
                    StimulusDescriptor(
                        epochId = EpochId("epoch-1"),
                        experimentId = ExperimentId("exp-1"),
                        muscle = MuscleGroup.Chest,
                        pattern = MovementPattern.HorizontalPress,
                        liftId = LiftId("lift-bench"),
                        weeklyHardSets = 12.0,
                        sessionsPerWeek = 3.0,
                        intensityProxy = 2.0,
                    ),
                outcomeWindowStart = Instant.parse("2025-01-01T00:00:00Z"),
                outcomeWindowEnd = Instant.parse("2025-02-01T00:00:00Z"),
                status = ExperimentStatus.Scheduled,
            )

        experimentRepository.replaceAll(listOf(block))
        val stored = experimentRepository.getAll()

        assertEquals("exp-1", stored[0].id.value)
        assertEquals(ExperimentStatus.Scheduled, stored[0].status)
    }

    @Test
    @DisplayName("RP-S8-005 metadata singleton")
    fun rpS8_005_metadataSingleton() = runTest {
        val metadata =
            ResponseProfileMetadata(
                epochsAnalyzed = 2,
                analysisWindowStart = Instant.parse("2025-01-01T00:00:00Z"),
                analysisWindowEnd = Instant.parse("2025-06-01T00:00:00Z"),
                lastExtractedAt = Instant.parse("2026-01-15T00:00:00Z"),
            )

        metadataRepository.save(metadata)
        val stored = metadataRepository.get()

        assertEquals(2, stored?.epochsAnalyzed)
        assertEquals(Instant.parse("2026-01-15T00:00:00Z"), stored?.lastExtractedAt)
    }

    @Test
    @DisplayName("RP-S8-006 replaceAll clears prior rows")
    fun rpS8_006_replaceAllClearsPriorRows() = runTest {
        val epochs =
            (1..3).map { index ->
                com.stratum.domain.response.Epoch(
                    id = EpochId("epoch-$index"),
                    start = Instant.parse("2025-01-01T00:00:00Z"),
                    end = Instant.parse("2025-06-01T00:00:00Z"),
                    anchorLiftIds = emptySet(),
                    splitReason = EpochSplitReason.WindowEnd,
                )
            }
        epochRepository.replaceAll(epochs)
        epochRepository.replaceAll(listOf(epochs.first()))

        assertEquals(1, epochRepository.getAll().size)
    }

    @Test
    @DisplayName("RP-S8-007 schema version includes response modeling tables (Room v15)")
    fun rpS8_007_schemaVersionIncludesModelingTables() {
        // v13 (STR-129: PR unique index → (liftId, equipmentId, isPersonalRecord)),
        // v14 (STR-127: workout_lift_notes pair-scoping lift_id), and v15
        // (STR-131: lifts tombstone flag) bumped the schema version; response
        // tables remain present.
        assertEquals(15, database.openHelper.readableDatabase.version)
    }

    private fun sampleVolumeObservation(
        confidence: Double,
        evidence: EvidenceLevel,
    ): VolumeResponseObservation =
        VolumeResponseObservation(
            muscle = MuscleGroup.Quad,
            epochId = EpochId("epoch-1"),
            anchorLiftId = LiftId("lift-squat"),
            stimulus =
                StimulusDescriptor(
                    epochId = EpochId("epoch-1"),
                    experimentId = null,
                    muscle = MuscleGroup.Quad,
                    pattern = null,
                    liftId = LiftId("lift-squat"),
                    weeklyHardSets = 10.0,
                    sessionsPerWeek = 2.0,
                    intensityProxy = 3.0,
                ),
            outcome =
                OutcomeDescriptor(
                    anchorLiftId = LiftId("lift-squat"),
                    metric = OutcomeMetric.WeeklyPeakE1rm,
                    value = 0.8,
                    windowWeeks = 10,
                    reliability = RegressionReliability(rSquared = 0.7, outlierCount = 0, completenessScore = 0.9),
                ),
            correlationSlope = 0.8,
            confidenceScore = confidence,
            evidence = evidence,
            weeklyPointsUsed = 5,
            epochWeeksUsed = 10,
            observedWeeklySetRange = WeeklySetRange(min = 8.0, max = 12.0),
            bestObservedWeeklySetRange = null,
        )
}
