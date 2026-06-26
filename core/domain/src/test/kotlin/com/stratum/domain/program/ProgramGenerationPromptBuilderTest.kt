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

    private fun catalogLift(id: LiftId, name: String): Lift =
        Lift(
            id = id,
            name = name,
            movementPattern = MovementPattern.LegPush,
            primaryMuscles = emptySet(),
            secondaryMuscles = emptySet(),
            incrementTiers = emptyList(),
            presetTags = emptySet(),
            allowWeightChangeBetweenSets = false,
            enabled = true,
        )
}
