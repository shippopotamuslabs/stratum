@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.context

import com.stratum.domain.importing.MuscleVolumeHighlight
import com.stratum.domain.onboarding.SpecializationPreference
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.domain.program.LiftWithInsights
import com.stratum.domain.program.LiteratureVolumeLandmarks
import com.stratum.domain.program.ResponseProfilePayload

object ProgramGenerationContextMapper {
    fun map(
        profile: UserTrainingProfile,
        specialization: SpecializationPreference,
        provenLifts: List<LiftWithInsights>,
        availableLifts: List<LiftWithInsights>,
        responseProfile: ResponseProfilePayload?,
    ): ProgramGenerationContext {
        val liftsById = linkedMapOf<String, LiftContext>()
        val enabledLiftIds = mutableSetOf<String>()
        val movementPatternByLiftId = linkedMapOf<String, String>()
        for (entry in provenLifts + availableLifts) {
            val ctx = entry.toLiftContext(proven = entry in provenLifts)
            liftsById[ctx.id] = ctx
            enabledLiftIds += ctx.id
            movementPatternByLiftId[ctx.id] = ctx.movementPattern
        }

        val volumeLandmarksByMuscle = mergeVolumeLandmarks(responseProfile)
        val volumeResponsesByMuscle = mergeVolumeResponses(responseProfile)
        val liftEffectivenessByLiftId = mergeLiftEffectiveness(responseProfile)

        return ProgramGenerationContext(
            trainingAge = profile.trainingAge?.name.orEmpty(),
            lastTrained = profile.lastTrained?.name,
            sessionLengthMinutes = profile.sessionLengthMinutes,
            trainingDaysPerWeek = profile.weekdays.size,
            weekdays = profile.weekdays.map { it.name },
            specialization = specialization.levels.mapKeys { (muscle, _) -> muscle.name }
                .mapValues { (_, level) -> level.name },
            liftsById = liftsById,
            volumeLandmarksByMuscle = volumeLandmarksByMuscle,
            volumeResponsesByMuscle = volumeResponsesByMuscle,
            enabledLiftIds = enabledLiftIds,
            movementPatternByLiftId = movementPatternByLiftId,
            liftEffectivenessByLiftId = liftEffectivenessByLiftId,
        )
    }

    private fun LiftWithInsights.toLiftContext(proven: Boolean): LiftContext {
        val patterns = lift.movementPatterns.map { it.name }
        return LiftContext(
            id = lift.id.value,
            name = lift.name,
            movementPattern = patterns.firstOrNull().orEmpty(),
            movementPatterns = patterns,
            primaryMuscles = lift.primaryMuscles.map { it.name },
            secondaryMuscles = lift.secondaryMuscles.map { it.name },
            mainstream = lift.mainstream,
            progressTrend = trainingInsights?.progressTrend?.name,
            proven = proven,
            sessionCount = trainingInsights?.sessionCount,
            fatigueDecaySlope = trainingInsights?.fatigueDecaySlope,
            fatigueDecayIntercept = trainingInsights?.fatigueDecayIntercept,
            muscleLengthBias = lift.muscleLengthBias.toGenerationContext(),
            equipment = lift.equipment,
        )
    }

    private fun com.stratum.domain.lift.MuscleLengthBias.toGenerationContext(): MuscleLengthBias? =
        when (this) {
            com.stratum.domain.lift.MuscleLengthBias.Lengthened -> MuscleLengthBias.Lengthened
            com.stratum.domain.lift.MuscleLengthBias.Shortened -> MuscleLengthBias.Shortened
            com.stratum.domain.lift.MuscleLengthBias.MidRange -> MuscleLengthBias.MidRange
        }

    private fun mergeVolumeLandmarks(
        responseProfile: ResponseProfilePayload?,
    ): Map<String, VolumeLandmarkContext> {
        val into = linkedMapOf<String, VolumeLandmarkContext>()
        responseProfile?.volumeLandmarks.orEmpty().forEach { highlight ->
            mergeLandmark(into, highlight.toPersonalLandmark(), preferPersonal = true)
        }
        LiteratureVolumeLandmarks.all().forEach { literature ->
            mergeLandmark(
                into,
                VolumeLandmarkContext(
                    muscle = literature.muscle.name,
                    estimatedMev = literature.estimatedMev,
                    estimatedMav = literature.estimatedMav,
                    estimatedMrv = literature.estimatedMrv,
                    estimatedPv = literature.estimatedPv,
                    estimatedMv = literature.estimatedMv,
                    source = VolumeLandmarkSource.Literature,
                ),
                preferPersonal = false,
            )
        }
        return into
    }

    private fun MuscleVolumeHighlight.toPersonalLandmark(): VolumeLandmarkContext =
        VolumeLandmarkContext(
            muscle = muscle.name,
            estimatedMev = estimatedMev,
            estimatedMav = estimatedMav,
            estimatedMrv = estimatedMrv,
            estimatedPv = estimatedPv,
            source = VolumeLandmarkSource.Personal,
        )

    private fun mergeLandmark(
        into: MutableMap<String, VolumeLandmarkContext>,
        landmark: VolumeLandmarkContext,
        preferPersonal: Boolean,
    ) {
        val existing = into[landmark.muscle]
        if (existing == null) {
            into[landmark.muscle] = landmark
            return
        }
        if (preferPersonal && landmark.source == VolumeLandmarkSource.Personal) {
            into[landmark.muscle] = landmark
        } else if (existing.source != VolumeLandmarkSource.Personal) {
            into[landmark.muscle] = landmark
        }
    }

    private fun mergeVolumeResponses(
        responseProfile: ResponseProfilePayload?,
    ): Map<String, VolumeResponseContext> {
        if (responseProfile == null) return emptyMap()
        return responseProfile.volumeResponses.associate { influence ->
            influence.muscle.name to VolumeResponseContext(
                muscle = influence.muscle.name,
                correlationSlope = influence.correlationSlope,
                evidence = influence.evidence.name,
            )
        }
    }

    private fun mergeLiftEffectiveness(
        responseProfile: ResponseProfilePayload?,
    ): Map<String, LiftEffectivenessEntry> {
        if (responseProfile == null) return emptyMap()
        return responseProfile.liftEffectiveness.associate { influence ->
            influence.liftId.value to LiftEffectivenessEntry(
                liftId = influence.liftId.value,
                pattern = influence.pattern.name,
                rank = influence.rank,
                effectivenessScore = influence.effectivenessScore,
                evidence = influence.evidence.name,
            )
        }
    }
}
