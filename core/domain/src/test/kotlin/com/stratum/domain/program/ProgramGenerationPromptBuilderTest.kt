package com.stratum.domain.program

import com.stratum.domain.importing.ImportAnalysisMetadata
import com.stratum.domain.importing.LiftTrainingInsights
import com.stratum.domain.importing.ProgressTrend
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationPreference
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.domain.response.EvidenceLevel
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ProgramGenerationPromptBuilderTest {
    private val builder = ProgramGenerationPromptBuilder()

    @Test
    fun `payload splits proven and available lifts with a proven flag`() {
        val provenLift = catalogLift(LiftId("back-squat"), "Back Squat")
        val availableLift = catalogLift(LiftId("leg-press"), "Leg Press")
        val payload = builder.buildPayload(
            profile = UserTrainingProfile.Empty,
            specialization = SpecializationPreference.Empty,
            provenLifts = listOf(
                LiftWithInsights(
                    lift = provenLift,
                    trainingInsights = LiftTrainingInsights(
                        liftId = provenLift.id,
                        estimatedOneRm = Weight(315.0, Weight.WeightUnit.Lb),
                        fatigueDecaySlope = -0.12,
                        fatigueDecayIntercept = 320.0,
                        sessionCount = 14,
                        progressTrend = ProgressTrend.Stagnant,
                        lastPerformedAt = null,
                    ),
                ),
            ),
            availableLifts = listOf(LiftWithInsights(availableLift, trainingInsights = null)),
            supersetAffinities = emptyList(),
            volumeBaselines = emptyList(),
            analysisMetadata = null,
        )
        assertTrue(payload.contains("\"provenLifts\":["))
        assertTrue(payload.contains("\"availableLifts\":["))
        assertTrue(payload.contains("\"proven\":true"))
        assertTrue(payload.contains("\"proven\":false"))
        assertTrue(payload.contains("fatigueDecaySlope"))
        assertTrue(payload.contains("Back Squat"))
        assertTrue(payload.contains("Leg Press"))
        // The old flat enabledLifts[] key is gone.
        assertFalse(payload.contains("\"enabledLifts\""))
    }

    @Test
    fun `import analysis snippet omits inferredGymProfile`() {
        val metadata = ImportAnalysisMetadata(
            windowStart = Instant.parse("2025-01-01T00:00:00Z"),
            windowEnd = Instant.parse("2025-06-01T00:00:00Z"),
            provenLiftIds = listOf(LiftId("back-squat")),
            unmatchedExerciseNames = emptyList(),
            totalSessions = 10,
            totalSets = 120,
        )
        val payload = builder.buildPayload(
            profile = UserTrainingProfile.Empty,
            specialization = SpecializationPreference.Empty,
            provenLifts = emptyList(),
            availableLifts = emptyList(),
            supersetAffinities = emptyList(),
            volumeBaselines = emptyList(),
            analysisMetadata = metadata,
        )
        assertTrue(payload.contains("\"importAnalysis\""))
        assertFalse(payload.contains("inferredGymProfile"))
        assertTrue(payload.contains("\"totalSessions\":10"))
    }

    @Test
    @DisplayName("RP-S10-008 non-finite doubles omitted from JSON")
    fun rpS10_008_nonFiniteDoublesOmittedFromJson() {
        val provenLift = catalogLift(LiftId("back-squat"), "Back Squat")
        val responseProfile =
            ResponseProfilePayload(
                volumeResponses =
                    listOf(
                        VolumeResponseInfluence(
                            muscle = MuscleGroup.Quad,
                            anchorLiftId = LiftId("back-squat"),
                            correlationSlope = Double.NaN,
                            confidenceScore = Double.POSITIVE_INFINITY,
                            evidence = EvidenceLevel.Hypothesis,
                        ),
                    ),
                liftEffectiveness = emptyList(),
                volumeLandmarks = emptyList(),
                informational = ResponseProfileInformational(emptyList(), emptyList()),
                epochsAnalyzed = 1,
                analysisWindowStart = Instant.parse("2025-01-01T00:00:00Z"),
                analysisWindowEnd = Instant.parse("2025-06-01T00:00:00Z"),
            )
        val payload =
            builder.buildPayload(
                profile = UserTrainingProfile.Empty,
                specialization = SpecializationPreference.Empty,
                provenLifts =
                    listOf(
                        LiftWithInsights(
                            lift = provenLift,
                            trainingInsights =
                                LiftTrainingInsights(
                                    liftId = provenLift.id,
                                    estimatedOneRm = Weight(Double.NaN, Weight.WeightUnit.Lb),
                                    fatigueDecaySlope = Double.NaN,
                                    fatigueDecayIntercept = Double.POSITIVE_INFINITY,
                                    sessionCount = 14,
                                    progressTrend = ProgressTrend.Stagnant,
                                    lastPerformedAt = null,
                                ),
                        ),
                    ),
                availableLifts = emptyList(),
                supersetAffinities = emptyList(),
                volumeBaselines = emptyList(),
                analysisMetadata = null,
                responseProfile = responseProfile,
            )

        assertFalse(payload.contains("NaN"))
        assertFalse(payload.contains("Infinity"))
        assertTrue(payload.contains("\"sessionCount\":14"))
        assertTrue(payload.contains("\"responseProfile\""))
    }

    @Test
    fun `volume landmarks serialize mev mav mrv pv confidence tier and intervals`() {
        val responseProfile =
            ResponseProfilePayload(
                volumeResponses = emptyList(),
                liftEffectiveness = emptyList(),
                volumeLandmarks =
                    listOf(
                        com.stratum.domain.importing.MuscleVolumeHighlight(
                            muscle = MuscleGroup.Quad,
                            estimatedMev = 6.0,
                            estimatedMav = 12.0,
                            estimatedMrv = 18.0,
                            estimatedPv = 9.0,
                            mevInterval =
                                com.stratum.domain.importing.LandmarkConfidenceInterval(
                                    4.0,
                                    8.0,
                                    com.stratum.domain.importing.IntervalMethod.DeltaMethod,
                                ),
                            mavInterval = com.stratum.domain.importing.LandmarkConfidenceInterval.Unavailable,
                            mrvInterval =
                                com.stratum.domain.importing.LandmarkConfidenceInterval(
                                    15.0,
                                    21.0,
                                    com.stratum.domain.importing.IntervalMethod.ResidualBootstrap,
                                ),
                            pvInterval = com.stratum.domain.importing.LandmarkConfidenceInterval.Unavailable,
                            confidenceTier = com.stratum.domain.importing.LandmarkConfidenceTier.Medium,
                        ),
                    ),
                informational = ResponseProfileInformational(emptyList(), emptyList()),
                epochsAnalyzed = 1,
                analysisWindowStart = Instant.parse("2025-01-01T00:00:00Z"),
                analysisWindowEnd = Instant.parse("2025-06-01T00:00:00Z"),
            )
        val payload = builder.buildPayload(
            profile = UserTrainingProfile.Empty,
            specialization = SpecializationPreference.Empty,
            provenLifts = emptyList(),
            availableLifts = emptyList(),
            supersetAffinities = emptyList(),
            volumeBaselines = emptyList(),
            analysisMetadata = null,
            responseProfile = responseProfile,
        )
        assertTrue(payload.contains("\"volumeLandmarks\":["))
        assertTrue(payload.contains("\"estimatedMev\":6.0"))
        assertTrue(payload.contains("\"estimatedMav\":12.0"))
        assertTrue(payload.contains("\"estimatedMrv\":18.0"))
        assertTrue(payload.contains("\"estimatedPv\":9.0"))
        assertTrue(payload.contains("\"confidenceTier\":\"Medium\""))
        assertTrue(payload.contains("\"mevInterval\":{\"low\":4.0,\"high\":8.0,\"method\":\"DeltaMethod\"}"))
        assertTrue(payload.contains("\"mrvInterval\":{\"low\":15.0,\"high\":21.0,\"method\":\"ResidualBootstrap\"}"))
        assertFalse(payload.contains("\"mavInterval\""))
        assertFalse(payload.contains("\"pvInterval\""))
    }

    @Test
    @DisplayName("PROG-GEN-807 responseProfile influence fields do not double-separate with commas")
    fun progGen807_responseProfileInfluenceFieldsAvoidDoubleCommas() {
        val responseProfile =
            ResponseProfilePayload(
                volumeResponses =
                    listOf(
                        VolumeResponseInfluence(
                            muscle = MuscleGroup.Quad,
                            anchorLiftId = LiftId("back-squat"),
                            correlationSlope = 0.8,
                            confidenceScore = 0.80,
                            evidence = EvidenceLevel.Proven,
                        ),
                    ),
                liftEffectiveness =
                    listOf(
                        LiftEffectivenessInfluence(
                            pattern = MovementPattern.SquatPattern,
                            liftId = LiftId("back-squat"),
                            effectivenessScore = 0.75,
                            rank = 1,
                            evidence = EvidenceLevel.Proven,
                        ),
                    ),
                volumeLandmarks = emptyList(),
                informational = ResponseProfileInformational(emptyList(), emptyList()),
                epochsAnalyzed = 1,
                analysisWindowStart = Instant.parse("2025-01-01T00:00:00Z"),
                analysisWindowEnd = Instant.parse("2025-06-01T00:00:00Z"),
            )
        val payload =
            builder.buildPayload(
                profile = UserTrainingProfile.Empty,
                specialization = SpecializationPreference.Empty,
                provenLifts = emptyList(),
                availableLifts = emptyList(),
                supersetAffinities = emptyList(),
                volumeBaselines = emptyList(),
                analysisMetadata = null,
                responseProfile = responseProfile,
            )

        assertFalse(payload.contains(",,"))
        kotlinx.serialization.json.Json.parseToJsonElement(payload)
    }

    @Test
    fun `payload includes trainingDaysPerWeek and lift muscle mappings`() {
        val provenLift =
            catalogLift(LiftId("bench-press"), "Bench Press").copy(
                primaryMuscles = setOf(MuscleGroup.Chest),
                secondaryMuscles = setOf(MuscleGroup.Tricep, MuscleGroup.AnteriorDeltoid),
            )
        val payload =
            builder.buildPayload(
                profile =
                    UserTrainingProfile.Empty.copy(
                        weekdays =
                            setOf(
                                com.stratum.domain.onboarding.Weekday.Monday,
                                com.stratum.domain.onboarding.Weekday.Wednesday,
                                com.stratum.domain.onboarding.Weekday.Friday,
                            ),
                    ),
                specialization = SpecializationPreference.Empty,
                provenLifts = listOf(LiftWithInsights(provenLift, trainingInsights = null)),
                availableLifts = emptyList(),
                supersetAffinities = emptyList(),
                volumeBaselines = emptyList(),
                analysisMetadata = null,
            )
        assertTrue(payload.contains("\"trainingDaysPerWeek\":3"))
        assertTrue(payload.contains("\"primaryMuscles\":[\"Chest\"]"))
        assertTrue(payload.contains("\"secondaryMuscles\":[\"Tricep\",\"AnteriorDeltoid\"]"))
        kotlinx.serialization.json.Json.parseToJsonElement(payload)
    }

    @Test
    fun `preset-only payload includes literature volume landmarks for every muscle`() {
        val payload =
            builder.buildPayload(
                profile = UserTrainingProfile.Empty,
                specialization = SpecializationPreference.Empty,
                provenLifts = emptyList(),
                availableLifts = emptyList(),
                supersetAffinities = emptyList(),
                volumeBaselines = emptyList(),
                analysisMetadata = null,
            )
        assertTrue(payload.contains("\"literatureVolumeLandmarks\":["))
        assertTrue(payload.contains("\"muscle\":\"Quad\""))
        assertTrue(payload.contains("\"method\":\"LiteraturePrior\""))
        assertTrue(payload.contains("\"source\":\"LiteraturePrior\""))
        kotlinx.serialization.json.Json.parseToJsonElement(payload)
    }

    @Test
    fun `personal and literature volume landmarks are both included`() {
        val responseProfile =
            ResponseProfilePayload(
                volumeResponses = emptyList(),
                liftEffectiveness = emptyList(),
                volumeLandmarks =
                    listOf(
                        com.stratum.domain.importing.MuscleVolumeHighlight(
                            muscle = MuscleGroup.Quad,
                            estimatedMev = 6.0,
                            estimatedMav = 12.0,
                            estimatedMrv = 18.0,
                            estimatedPv = 9.0,
                            mevInterval = com.stratum.domain.importing.LandmarkConfidenceInterval.Unavailable,
                            mavInterval = com.stratum.domain.importing.LandmarkConfidenceInterval.Unavailable,
                            mrvInterval = com.stratum.domain.importing.LandmarkConfidenceInterval.Unavailable,
                            pvInterval = com.stratum.domain.importing.LandmarkConfidenceInterval.Unavailable,
                            confidenceTier = com.stratum.domain.importing.LandmarkConfidenceTier.Medium,
                        ),
                    ),
                informational = ResponseProfileInformational(emptyList(), emptyList()),
                epochsAnalyzed = 1,
                analysisWindowStart = Instant.parse("2025-01-01T00:00:00Z"),
                analysisWindowEnd = Instant.parse("2025-06-01T00:00:00Z"),
            )
        val payload =
            builder.buildPayload(
                profile = UserTrainingProfile.Empty,
                specialization = SpecializationPreference.Empty,
                provenLifts = emptyList(),
                availableLifts = emptyList(),
                supersetAffinities = emptyList(),
                volumeBaselines = emptyList(),
                analysisMetadata = null,
                responseProfile = responseProfile,
            )
        assertTrue(payload.contains("\"volumeLandmarks\":["))
        assertTrue(payload.contains("\"literatureVolumeLandmarks\":["))
    }

    private fun catalogLift(id: LiftId, name: String): Lift =
        Lift(
            id = id,
            name = name,
            movementPattern = MovementPattern.SquatPattern,
            primaryMuscles = emptySet(),
            secondaryMuscles = emptySet(),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            allowWeightChangeBetweenSets = false,
            enabled = true,
        )
}
