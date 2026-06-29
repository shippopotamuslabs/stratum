package com.stratum.domain.program

import com.stratum.domain.importing.ImportAnalysisMetadata
import com.stratum.domain.importing.LiftTrainingInsights
import com.stratum.domain.importing.MuscleVolumeBaseline
import com.stratum.domain.importing.ProgressTrend
import com.stratum.domain.importing.SupersetAffinity
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationPreference
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.domain.response.EvidenceLevel
import java.time.Instant
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class ProgramGenerationPayloadJsonContractTest {
    private val builder = ProgramGenerationPromptBuilder()
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    @DisplayName("PROG-GEN-801 payload parses as JSON for realistic onboarding fixture")
    fun progGen801_realisticFixtureParsesAsJson() {
        val provenLift = catalogLift(LiftId("back-squat"), "Back Squat")
        val availableLift = catalogLift(LiftId("leg-press"), "Leg Press")
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
                supersetAffinities =
                    listOf(
                        SupersetAffinity(
                            liftA = LiftId("back-squat"),
                            liftB = LiftId("leg-press"),
                            medianRestSeconds = 45.0,
                            coOccurrenceCount = 8,
                            confidence = 0.4,
                        ),
                    ),
                volumeBaselines =
                    listOf(
                        MuscleVolumeBaseline(
                            muscle = MuscleGroup.Quad,
                            meanWeeklyHardSets = 10.0,
                            p90WeeklyHardSets = 14.0,
                            trendSlope = 0.5,
                        ),
                    ),
                analysisMetadata =
                    ImportAnalysisMetadata(
                        windowStart = Instant.parse("2025-01-01T00:00:00Z"),
                        windowEnd = Instant.parse("2025-06-01T00:00:00Z"),
                        provenLiftIds = listOf(LiftId("back-squat")),
                        unmatchedExerciseNames = emptyList(),
                        totalSessions = 10,
                        totalSets = 120,
                    ),
                responseProfile =
                    ResponseProfilePayload(
                        volumeResponses = emptyList(),
                        liftEffectiveness = emptyList(),
                        volumeLandmarks = emptyList(),
                        informational = ResponseProfileInformational(emptyList(), emptyList()),
                        epochsAnalyzed = 1,
                        analysisWindowStart = Instant.parse("2025-01-01T00:00:00Z"),
                        analysisWindowEnd = Instant.parse("2025-06-01T00:00:00Z"),
                    ),
            )

        assertParsesAsJson(payload)
    }

    @Test
    @DisplayName("PROG-GEN-802 non-finite volume and superset numbers serialize as finite JSON")
    fun progGen802_nonFiniteNumbersSerializeAsFiniteJson() {
        val payload =
            builder.buildPayload(
                profile = UserTrainingProfile.Empty,
                specialization = SpecializationPreference.Empty,
                provenLifts = emptyList(),
                availableLifts = emptyList(),
                supersetAffinities =
                    listOf(
                        SupersetAffinity(
                            liftA = LiftId("lift-a"),
                            liftB = LiftId("lift-b"),
                            medianRestSeconds = Double.NaN,
                            coOccurrenceCount = 3,
                            confidence = 0.15,
                        ),
                    ),
                volumeBaselines =
                    listOf(
                        MuscleVolumeBaseline(
                            muscle = MuscleGroup.Quad,
                            meanWeeklyHardSets = Double.NaN,
                            p90WeeklyHardSets = Double.POSITIVE_INFINITY,
                            trendSlope = Double.NEGATIVE_INFINITY,
                        ),
                    ),
                analysisMetadata = null,
            )

        assertParsesAsJson(payload)
    }

    @Test
    @DisplayName("PROG-GEN-803 control characters in lift names are JSON-escaped")
    fun progGen803_controlCharactersInLiftNamesEscaped() {
        val lift = catalogLift(LiftId("custom-press"), "Bench\u0001Press")
        val payload =
            builder.buildPayload(
                profile = UserTrainingProfile.Empty,
                specialization = SpecializationPreference.Empty,
                provenLifts = listOf(LiftWithInsights(lift, trainingInsights = null)),
                availableLifts = emptyList(),
                supersetAffinities = emptyList(),
                volumeBaselines = emptyList(),
                analysisMetadata = null,
            )

        assertParsesAsJson(payload)
    }

    private fun assertParsesAsJson(payload: String) {
        try {
            json.parseToJsonElement(payload)
        } catch (error: Exception) {
            throw AssertionError("Invalid program generation payload JSON: ${error.message}\n$payload", error)
        }
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
