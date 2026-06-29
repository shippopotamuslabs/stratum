package com.stratum.domain.program

import com.stratum.core.testing.onboarding.FakeLiftRepository
import com.stratum.core.testing.onboarding.FakeSpecializationPreferenceRepository
import com.stratum.core.testing.onboarding.FakeUserTrainingProfileRepository
import com.stratum.domain.importing.ImportAnalysisMetadata
import com.stratum.domain.importing.LiftTrainingInsights
import com.stratum.domain.importing.MuscleVolumeBaseline
import com.stratum.domain.importing.ProgressTrend
import com.stratum.domain.importing.SupersetAffinity
import com.stratum.domain.lift.GymPreset
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.LastTrainedBucket
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.onboarding.SpecializationPreference
import com.stratum.domain.onboarding.TrainingAge
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.response.EvidenceLevel
import com.stratum.domain.response.LiftEffectivenessObservation
import com.stratum.domain.response.OutcomeDescriptor
import com.stratum.domain.response.OutcomeMetric
import com.stratum.domain.response.RegressionReliability
import com.stratum.domain.response.ResponseProfileMetadata
import com.stratum.domain.response.StimulusDescriptor
import com.stratum.domain.response.VolumeResponseObservation
import com.stratum.domain.response.WeeklySetRange
import com.stratum.domain.response.identity.EpochId
import java.nio.file.Path
import java.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * End-to-end JSON contract for [LoadProgramGenerationPayloadUseCase] — catches malformed payloads
 * before they reach the `generateProgram` Callable (server rejects with invalid-argument).
 */
class LoadProgramGenerationPayloadJsonContractTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    @DisplayName("PROG-GEN-804 first-time onboarding without import produces valid JSON")
    fun progGen804_firstTimeOnboardingWithoutImport() = runTest {
        val lifts = commercialGymLiftsFromSeed()
        val liftRepository = FakeLiftRepository(initialLifts = lifts, initialPreset = GymPreset.CommercialGym)
        val profile =
            UserTrainingProfile(
                trainingAge = TrainingAge.Beginner,
                lastTrained = LastTrainedBucket.WithinLastMonth,
                weekdays = setOf(Weekday.Monday, Weekday.Wednesday, Weekday.Friday),
                sessionLengthMinutes = 60,
            )
        val specialization =
            SpecializationPreference(
                levels =
                    mapOf(
                        MuscleGroup.Chest to SpecializationLevel.Specialize,
                        MuscleGroup.Quad to SpecializationLevel.Maintenance,
                    ),
            )
        val payload =
            buildUseCase(
                liftRepository = liftRepository,
                profileRepository = FakeUserTrainingProfileRepository(initial = profile),
                specializationRepository =
                    FakeSpecializationPreferenceRepository(initial = specialization),
            ).invoke()

        assertParsesAsJson(payload)
        assertFalse(payload.contains("NaN"))
        assertFalse(payload.contains("Infinity"))
        assertTrue(payload.contains("\"availableLifts\":["))
        assertFalse(payload.contains("\"provenLifts\":[{")) // no proven lifts without import
        assertTrue(payload.contains("\"literatureVolumeLandmarks\":["))
        assertTrue(payload.contains("\"muscle\":\"Chest\""))
        assertTrue(payload.contains("\"source\":\"LiteraturePrior\""))
        assertFalse(payload.contains("\"responseProfile\""))
    }

    @Test
    @DisplayName("PROG-GEN-805 post-import onboarding with insights and response profile produces valid JSON")
    fun progGen805_postImportOnboarding() = runTest {
        val squatId = LiftId("barbell-full-squat")
        val benchId = LiftId("barbell-bench-press")
        val lifts =
            listOf(
                seedLift(squatId, "Barbell Full Squat", MovementPattern.SquatPattern, GymPreset.CommercialGym),
                seedLift(benchId, "Barbell Bench Press", MovementPattern.HorizontalPress, GymPreset.CommercialGym),
                seedLift(LiftId("leg-press"), "Leg Press", MovementPattern.SquatPattern, GymPreset.CommercialGym),
            )
        val insights =
            listOf(
                LiftTrainingInsights(
                    liftId = squatId,
                    estimatedOneRm = Weight(315.0, Weight.WeightUnit.Lb),
                    fatigueDecaySlope = -0.12,
                    fatigueDecayIntercept = 320.0,
                    sessionCount = 14,
                    progressTrend = ProgressTrend.Stagnant,
                    lastPerformedAt = null,
                ),
            )
        val metadata =
            ImportAnalysisMetadata(
                windowStart = Instant.parse("2025-01-01T00:00:00Z"),
                windowEnd = Instant.parse("2025-06-01T00:00:00Z"),
                provenLiftIds = listOf(squatId),
                unmatchedExerciseNames = listOf("Landmine 180's"),
                totalSessions = 42,
                totalSets = 520,
            )
        val epochId = EpochId("epoch-1")
        val volumeObservation =
            VolumeResponseObservation(
                muscle = MuscleGroup.Quad,
                epochId = epochId,
                anchorLiftId = squatId,
                stimulus =
                    StimulusDescriptor(
                        epochId = epochId,
                        experimentId = null,
                        muscle = MuscleGroup.Quad,
                        pattern = null,
                        liftId = squatId,
                        weeklyHardSets = 10.0,
                        sessionsPerWeek = 2.0,
                        intensityProxy = 3.0,
                    ),
                outcome =
                    OutcomeDescriptor(
                        anchorLiftId = squatId,
                        metric = OutcomeMetric.EstimatedOneRmTrend,
                        value = 0.8,
                        windowWeeks = 10,
                        reliability = RegressionReliability(rSquared = 0.7, outlierCount = 0, completenessScore = 0.9),
                    ),
                correlationSlope = 0.8,
                confidenceScore = 0.80,
                evidence = EvidenceLevel.Proven,
                weeklyPointsUsed = 5,
                epochWeeksUsed = 10,
                observedWeeklySetRange = WeeklySetRange(min = 8.0, max = 12.0),
                bestObservedWeeklySetRange = null,
            )
        val volumeRepo = com.stratum.core.testing.response.FakeVolumeResponseObservationRepository()
        volumeRepo.seed(listOf(volumeObservation))
        val metadataRepo = com.stratum.core.testing.response.FakeResponseProfileMetadataRepository()
        metadataRepo.save(
            ResponseProfileMetadata(
                epochsAnalyzed = 2,
                analysisWindowStart = Instant.parse("2025-01-01T00:00:00Z"),
                analysisWindowEnd = Instant.parse("2025-06-01T00:00:00Z"),
                lastExtractedAt = Instant.parse("2026-01-15T00:00:00Z"),
            ),
        )
        val payload =
            buildUseCase(
                liftRepository = FakeLiftRepository(lifts.map { it.copy(enabled = true) }),
                liftInsightsRepository = FakeLiftInsightsRepository(insights),
                importMetadataRepository = FakeMetadataRepository(metadata),
                volumeResponseObservationRepository = volumeRepo,
                responseProfileMetadataRepository = metadataRepo,
                supersetAffinityRepository =
                    FakeSupersetRepository(
                        listOf(
                            SupersetAffinity(
                                liftA = squatId,
                                liftB = benchId,
                                medianRestSeconds = 45.0,
                                coOccurrenceCount = 8,
                                confidence = 0.4,
                            ),
                        ),
                    ),
                muscleVolumeBaselineRepository =
                    FakeVolumeRepository(
                        listOf(
                            MuscleVolumeBaseline(
                                muscle = MuscleGroup.Quad,
                                meanWeeklyHardSets = 10.0,
                                p90WeeklyHardSets = 14.0,
                                trendSlope = 0.5,
                            ),
                        ),
                    ),
            ).invoke()

        assertParsesAsJson(payload)
        assertTrue(payload.contains("\"provenLifts\":["))
        assertTrue(payload.contains("\"importAnalysis\""))
        assertTrue(payload.contains("\"responseProfile\""))
    }

    @Test
    @DisplayName("PROG-GEN-806 full commercial gym seed catalog produces valid JSON")
    fun progGen806_fullCommercialGymCatalog() = runTest {
        val lifts = commercialGymLiftsFromSeed()
        val payload =
            buildUseCase(
                liftRepository = FakeLiftRepository(initialLifts = lifts, initialPreset = GymPreset.CommercialGym),
            ).invoke()

        assertParsesAsJson(payload)
        val root = json.parseToJsonElement(payload).jsonObject
        val available = root["availableLifts"]
        assertTrue(available != null)
    }

    private fun buildUseCase(
        liftRepository: FakeLiftRepository = FakeLiftRepository(),
        profileRepository: FakeUserTrainingProfileRepository = FakeUserTrainingProfileRepository(),
        specializationRepository: FakeSpecializationPreferenceRepository =
            FakeSpecializationPreferenceRepository(),
        liftInsightsRepository: FakeLiftInsightsRepository = FakeLiftInsightsRepository(),
        supersetAffinityRepository: FakeSupersetRepository = FakeSupersetRepository(),
        muscleVolumeBaselineRepository: FakeVolumeRepository = FakeVolumeRepository(),
        importMetadataRepository: FakeMetadataRepository = FakeMetadataRepository(),
        volumeResponseObservationRepository:
            com.stratum.core.testing.response.FakeVolumeResponseObservationRepository =
            com.stratum.core.testing.response.FakeVolumeResponseObservationRepository(),
        liftEffectivenessObservationRepository:
            com.stratum.core.testing.response.FakeLiftEffectivenessObservationRepository =
            com.stratum.core.testing.response.FakeLiftEffectivenessObservationRepository(),
        responseProfileMetadataRepository:
            com.stratum.core.testing.response.FakeResponseProfileMetadataRepository =
            com.stratum.core.testing.response.FakeResponseProfileMetadataRepository(),
        muscleVolumeLandmarkEstimateRepository:
            com.stratum.core.testing.response.FakeMuscleVolumeLandmarkEstimateRepository =
            com.stratum.core.testing.response.FakeMuscleVolumeLandmarkEstimateRepository(),
    ): LoadProgramGenerationPayloadUseCase =
        LoadProgramGenerationPayloadUseCase(
            userTrainingProfileRepository = profileRepository,
            observeSpecialization =
                com.stratum.domain.onboarding.ObserveSpecializationUseCase(specializationRepository),
            liftRepository = liftRepository,
            liftTrainingInsightsRepository = liftInsightsRepository,
            supersetAffinityRepository = supersetAffinityRepository,
            muscleVolumeBaselineRepository = muscleVolumeBaselineRepository,
            importAnalysisMetadataRepository = importMetadataRepository,
            volumeResponseObservationRepository = volumeResponseObservationRepository,
            liftEffectivenessObservationRepository = liftEffectivenessObservationRepository,
            responseProfileMetadataRepository = responseProfileMetadataRepository,
            muscleVolumeLandmarkEstimateRepository = muscleVolumeLandmarkEstimateRepository,
            promptBuilder = ProgramGenerationPromptBuilder(),
        )

    private fun assertParsesAsJson(payload: String) {
        try {
            val element = json.parseToJsonElement(payload)
            require(element is JsonObject) { "Expected top-level JSON object" }
        } catch (error: Exception) {
            throw AssertionError(
                "Invalid program generation payload JSON: ${error.message}\n${payload.take(500)}…",
                error,
            )
        }
    }

    private fun commercialGymLiftsFromSeed(): List<Lift> {
        val seedPath =
            Path.of("..", "data", "src", "main", "assets", "lifts", "seed-lifts.json")
                .normalize()
                .toAbsolutePath()
        val raw = seedPath.toFile().readText()
        val list = json.parseToJsonElement(raw).jsonArray
        return list.map { entry ->
            val obj = entry.jsonObject
            val id = LiftId(obj.getValue("id").jsonPrimitive.content)
            val name = obj.getValue("name").jsonPrimitive.content
            val patterns = obj.getValue("movementPatterns").jsonArray.map { MovementPattern.valueOf(it.jsonPrimitive.content) }
            val pattern = patterns.first()
            val presets =
                obj["presetTags"]?.let { tags ->
                    tags.toString()
                        .removePrefix("[")
                        .removeSuffix("]")
                        .split(',')
                        .mapNotNull { tag ->
                            val presetName = tag.trim().trim('"').ifEmpty { return@mapNotNull null }
                            runCatching { GymPreset.valueOf(presetName) }.getOrNull()
                        }
                        .toSet()
                } ?: emptySet()
            seedLift(id, name, pattern, *presets.toTypedArray())
        }.filter { GymPreset.CommercialGym in it.presetTags }
            .map { it.copy(enabled = true) }
    }

    private fun seedLift(
        id: LiftId,
        name: String,
        pattern: MovementPattern,
        vararg presets: GymPreset,
    ): Lift =
        Lift(
            id = id,
            name = name,
            movementPatterns = setOf(pattern),
            primaryMuscles = emptySet(),
            secondaryMuscles = emptySet(),
            allowWeightChangeBetweenSets = false,
            incrementTiers = emptyList(),
            presetTags = presets.toSet(),
            enabled = true,
        )

    private class FakeLiftInsightsRepository(
        private val insights: List<LiftTrainingInsights> = emptyList(),
    ) : com.stratum.domain.importing.LiftTrainingInsightsRepository {
        override suspend fun replaceAll(insights: List<LiftTrainingInsights>) = Unit

        override suspend fun getAll(): List<LiftTrainingInsights> = insights

        override suspend fun getByLiftId(liftId: LiftId) = insights.find { it.liftId == liftId }
    }

    private class FakeSupersetRepository(
        private val affinities: List<SupersetAffinity> = emptyList(),
    ) : com.stratum.domain.importing.SupersetAffinityRepository {
        override suspend fun replaceAll(affinities: List<SupersetAffinity>) = Unit

        override suspend fun getAll(): List<SupersetAffinity> = affinities
    }

    private class FakeVolumeRepository(
        private val baselines: List<MuscleVolumeBaseline> = emptyList(),
    ) : com.stratum.domain.importing.MuscleVolumeBaselineRepository {
        override suspend fun replaceAll(baselines: List<MuscleVolumeBaseline>) = Unit

        override suspend fun getAll(): List<MuscleVolumeBaseline> = baselines
    }

    private class FakeMetadataRepository(
        private val metadata: ImportAnalysisMetadata? = null,
    ) : com.stratum.domain.importing.ImportAnalysisMetadataRepository {
        override suspend fun save(metadata: ImportAnalysisMetadata) = Unit

        override suspend fun get(): ImportAnalysisMetadata? = metadata

        override suspend fun clear() = Unit
    }
}
