package com.stratum.domain.program

import com.stratum.domain.importing.ImportAnalysisMetadata
import com.stratum.domain.importing.LiftTrainingInsights
import com.stratum.domain.importing.MuscleVolumeBaseline
import com.stratum.domain.importing.SupersetAffinity
import com.stratum.domain.lift.Lift
import com.stratum.domain.onboarding.SpecializationPreference
import com.stratum.domain.onboarding.UserTrainingProfile
import javax.inject.Inject

/**
 * Serializes onboarding + persisted training insights for OS-7 Callable payload.
 * OS-6 only persists insights; this builder is the read path for program generation.
 *
 * The enabled lift pool is split into [provenLifts] (Tier A: matched from the user's logged history,
 * carry training insights, prioritized as primary movements) and [availableLifts] (Tier B:
 * equipment-closure / preset-backfill lifts, no insights, the substitution pool). No gym-type hint is
 * sent — the curated lift pool itself is the gym-type signal, and an explicit hint would double-gate.
 */
class ProgramGenerationPromptBuilder @Inject constructor() {
    fun buildPayload(
        profile: UserTrainingProfile,
        specialization: SpecializationPreference,
        provenLifts: List<LiftWithInsights>,
        availableLifts: List<LiftWithInsights>,
        supersetAffinities: List<SupersetAffinity>,
        volumeBaselines: List<MuscleVolumeBaseline>,
        analysisMetadata: ImportAnalysisMetadata?,
        responseProfile: ResponseProfilePayload? = null,
    ): String {
        val provenJson = provenLifts.joinToString(",") { it.toJson(proven = true) }
        val availableJson = availableLifts.joinToString(",") { it.toJson(proven = false) }
        val supersetsJson = supersetAffinities.joinToString(",") { it.toJson() }
        val volumeJson = volumeBaselines.joinToString(",") { it.toJson() }
        val specializationJson = specialization.levels.entries.joinToString(",") { (muscle, level) ->
            """${muscle.name.quoteJson()}:${level.name.quoteJson()}"""
        }
        val importJson = analysisMetadata?.toJsonSnippet() ?: ""
        return buildString {
            append('{')
            append(""""profile":{"trainingAge":""")
            append(profile.trainingAge?.name?.quoteJson() ?: "null")
            append(""","lastTrained":""")
            append(profile.lastTrained?.name?.quoteJson() ?: "null")
            append(""","sessionLengthMinutes":""")
            append(profile.sessionLengthMinutes ?: "null")
            append(""","weekdays":""")
            append(profile.weekdays.joinToString(",") { it.name }.quoteJson())
            append("\"},")
            append(""""specialization":{""")
            append(specializationJson)
            append("},")
            append(""""provenLifts":[""")
            append(provenJson)
            append("],")
            append(""""availableLifts":[""")
            append(availableJson)
            append(']')
            if (supersetAffinities.isNotEmpty()) {
                append(""","supersetAffinities":[""")
                append(supersetsJson)
                append(']')
            }
            if (volumeBaselines.isNotEmpty()) {
                append(""","volumeBaselines":[""")
                append(volumeJson)
                append(']')
            }
            if (responseProfile != null && shouldIncludeResponseProfile(responseProfile)) {
                append(""","responseProfile":""")
                append(responseProfile.toJson())
            }
            append(importJson)
            append('}')
        }
    }

    private fun LiftWithInsights.toJson(proven: Boolean): String {
        val insightsJson = trainingInsights?.let { insights ->
            val e1rm = insights.estimatedOneRm?.amount
            val slope = insights.fatigueDecaySlope
            val intercept = insights.fatigueDecayIntercept
            val trend = insights.progressTrend?.name
            buildString {
                append(""","trainingInsights":{""")
                append(""""sessionCount":${insights.sessionCount}""")
                e1rm?.let { append(""","estimatedOneRm":$it""") }
                slope?.let { append(""","fatigueDecaySlope":$it""") }
                intercept?.let { append(""","fatigueDecayIntercept":$it""") }
                trend?.let { append(""","progressTrend":${it.quoteJson()}""") }
                append('}')
            }
        } ?: ""
        val id = lift.id.value.quoteJson()
        val name = lift.name.quoteJson()
        val pattern = lift.movementPattern.name.quoteJson()
        return """{"id":$id,"name":$name,"movementPattern":$pattern,"proven":$proven$insightsJson}"""
    }

    private fun SupersetAffinity.toJson(): String {
        val liftA = liftA.value.quoteJson()
        val liftB = liftB.value.quoteJson()
        return """{"liftA":$liftA,"liftB":$liftB,"medianRestSeconds":$medianRestSeconds,"coOccurrenceCount":$coOccurrenceCount}"""
    }

    private fun MuscleVolumeBaseline.toJson(): String {
        val trend = trendSlope?.let { ""","trendSlope":$it""" } ?: ""
        val muscle = muscle.name.quoteJson()
        return """{"muscle":$muscle,"meanWeeklyHardSets":$meanWeeklyHardSets,"p90WeeklyHardSets":$p90WeeklyHardSets$trend}"""
    }

    internal fun ResponseProfilePayload.toJson(): String {
        val windowStartJson = analysisWindowStart.toString().quoteJson()
        val windowEndJson = analysisWindowEnd.toString().quoteJson()
        val volumeInfluenceJson =
            volumeResponses.joinToString(",") { influence ->
                buildString {
                    append("""{"muscle":${influence.muscle.name.quoteJson()},""")
                    append(""""anchorLiftId":${influence.anchorLiftId.value.quoteJson()},""")
                    append(""""correlationSlope":${influence.correlationSlope},""")
                    append(""""confidenceScore":${influence.confidenceScore},""")
                    append(""""evidence":${influence.evidence.name.quoteJson()}}""")
                }
            }
        val liftInfluenceJson =
            liftEffectiveness.joinToString(",") { influence ->
                buildString {
                    append("""{"pattern":${influence.pattern.name.quoteJson()},""")
                    append(""""liftId":${influence.liftId.value.quoteJson()},""")
                    append(""""effectivenessScore":${influence.effectivenessScore},""")
                    append(""""rank":${influence.rank},""")
                    append(""""evidence":${influence.evidence.name.quoteJson()}}""")
                }
            }
        val informationalVolumeJson =
            informational.volumeResponses.joinToString(",") { observation ->
                buildString {
                    append("""{"muscle":${observation.muscle.name.quoteJson()},""")
                    append(""""confidenceScore":${observation.confidenceScore},""")
                    append(""""evidence":${observation.evidence.name.quoteJson()}}""")
                }
            }
        val informationalEffectivenessJson =
            informational.liftEffectiveness.joinToString(",") { observation ->
                buildString {
                    append("""{"pattern":${observation.pattern.name.quoteJson()},""")
                    append(""""liftId":${observation.liftId.value.quoteJson()},""")
                    append(""""confidenceScore":${observation.confidenceScore},""")
                    append(""""evidence":${observation.evidence.name.quoteJson()}}""")
                }
            }
        return buildString {
            append('{')
            append(""""epochsAnalyzed":$epochsAnalyzed,""")
            append(""""analysisWindowStart":$windowStartJson,""")
            append(""""analysisWindowEnd":$windowEndJson,""")
            append(""""volumeResponses":[$volumeInfluenceJson],""")
            append(""""liftEffectiveness":[$liftInfluenceJson],""")
            append(""""informational":{""")
            append(""""volumeResponses":[$informationalVolumeJson],""")
            append(""""liftEffectiveness":[$informationalEffectivenessJson]""")
            append("}}")
        }
    }

    private fun ImportAnalysisMetadata.toJsonSnippet(): String {
        val windowStartJson = windowStart.toString().quoteJson()
        val windowEndJson = windowEnd.toString().quoteJson()
        return buildString {
            append(""","importAnalysis":{""")
            append(""""windowStart":$windowStartJson,""")
            append(""""windowEnd":$windowEndJson,""")
            append(""""totalSessions":$totalSessions,""")
            append(""""totalSets":$totalSets}}""")
        }
    }

    private fun String.quoteJson(): String = buildString {
        append('"')
        for (ch in this@quoteJson) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
        append('"')
    }
}

data class LiftWithInsights(
    val lift: Lift,
    val trainingInsights: LiftTrainingInsights?,
)

class BuildProgramGenerationPayloadUseCase {
    private val builder = ProgramGenerationPromptBuilder()

    operator fun invoke(
        profile: UserTrainingProfile,
        specialization: SpecializationPreference,
        provenLifts: List<LiftWithInsights>,
        availableLifts: List<LiftWithInsights>,
        supersetAffinities: List<SupersetAffinity>,
        volumeBaselines: List<MuscleVolumeBaseline>,
        analysisMetadata: ImportAnalysisMetadata?,
    ): String = builder.buildPayload(
        profile = profile,
        specialization = specialization,
        provenLifts = provenLifts,
        availableLifts = availableLifts,
        supersetAffinities = supersetAffinities,
        volumeBaselines = volumeBaselines,
        analysisMetadata = analysisMetadata,
    )
}
