@file:Suppress("MagicNumber", "ReturnCount", "LoopWithTooManyJumpStatements", "MatchingDeclarationName", "ComplexCondition", "MaxLineLength")

package com.stratum.domain.program.generation.context

import com.stratum.domain.importing.GlobalVolumePriors
import com.stratum.domain.importing.MuscleVolumeHighlight
import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.SpecializationPreference
import com.stratum.domain.onboarding.TrainingAge
import com.stratum.domain.onboarding.UserTrainingProfile
import com.stratum.domain.program.LiftWithInsights
import com.stratum.domain.program.LiteratureVolumeDefaultsProvider
import com.stratum.domain.program.ResponseProfilePayload
import com.stratum.domain.response.modeling.VolumePosture

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
        val movementPatternByLiftId = linkedMapOf<String, MovementPattern?>()
        for (entry in provenLifts + availableLifts) {
            val ctx = entry.toLiftContext(proven = entry in provenLifts)
            liftsById[ctx.id] = ctx
            enabledLiftIds += ctx.id
            movementPatternByLiftId[ctx.id] = ctx.movementPattern
        }

        val trainingAge = profile.trainingAge
        val muscleVolumeSnapshotsByMuscle = mergeVolumeSnapshots(responseProfile, trainingAge)
        val volumeCorrelationsByMuscle = mergeVolumeCorrelations(responseProfile)
        val liftEffectivenessByLiftId = mergeLiftEffectiveness(responseProfile)

        return ProgramGenerationContext(
            trainingAge = trainingAge?.name.orEmpty(),
            lastTrained = profile.lastTrained?.name,
            sessionLengthMinutes = profile.sessionLengthMinutes,
            trainingDaysPerWeek = profile.weekdays.size,
            weekdays = profile.weekdays.map { it.name },
            specialization = specialization.levels,
            liftsById = liftsById,
            muscleVolumeSnapshotsByMuscle = muscleVolumeSnapshotsByMuscle,
            volumeCorrelationsByMuscle = volumeCorrelationsByMuscle,
            enabledLiftIds = enabledLiftIds,
            movementPatternByLiftId = movementPatternByLiftId,
            liftEffectivenessByLiftId = liftEffectivenessByLiftId,
        )
    }

        private fun LiftWithInsights.toLiftContext(proven: Boolean): LiftContext {
        val patterns = lift.movementPatterns
        return LiftContext(
            id = lift.id.value,
            name = lift.name,
            movementPattern = patterns.firstOrNull(),
            movementPatterns = patterns,
            primaryMuscles = lift.primaryMuscles,
            secondaryMuscles = lift.secondaryMuscles,
            mainstream = lift.mainstream,
            progressTrend = trainingInsights?.progressTrend?.name,
            proven = proven,
            sessionCount = trainingInsights?.sessionCount,
            fatigueDecaySlope = trainingInsights?.fatigueDecaySlope,
            fatigueDecayIntercept = trainingInsights?.fatigueDecayIntercept,
            muscleLengthBias = lift.muscleLengthBias.toGenerationContext(),
            equipment = lift.equipment,
            kinematicProfile = lift.kinematicProfile,
            incrementTiers = lift.incrementTiers,
            estimatedOneRm = trainingInsights?.estimatedOneRm,
        )
    }

    private fun com.stratum.domain.lift.MuscleLengthBias.toGenerationContext(): MuscleLengthBias? =
        when (this) {
            com.stratum.domain.lift.MuscleLengthBias.Lengthened -> MuscleLengthBias.Lengthened
            com.stratum.domain.lift.MuscleLengthBias.Shortened -> MuscleLengthBias.Shortened
            com.stratum.domain.lift.MuscleLengthBias.MidRange -> MuscleLengthBias.MidRange
        }

    private fun mergeVolumeSnapshots(
        responseProfile: ResponseProfilePayload?,
        trainingAge: TrainingAge?,
    ): Map<String, MuscleVolumeSnapshotContext> {
        val globals = GlobalVolumePriors.forTrainingAge(trainingAge)
        val into = linkedMapOf<String, MuscleVolumeSnapshotContext>()
        LiteratureVolumeDefaultsProvider.all(trainingAge).forEach { literature ->
            mergeSnapshot(
                into,
                literature.toSnapshotContext(),
                preferPersonal = false,
            )
        }
        responseProfile?.volumeSnapshots.orEmpty().forEach { highlight ->
            mergeSnapshot(into, highlight.toPersonalSnapshot(globals), preferPersonal = true)
        }
        return into
    }

    private fun MuscleVolumeHighlight.toPersonalSnapshot(
        globals: com.stratum.domain.importing.ResolvedGlobalVolumePriors,
    ): MuscleVolumeSnapshotContext =
        MuscleVolumeSnapshotContext(
            muscle = muscle.name,
            retentionFloorMv = retentionFloorMv,
            paretoVolumePv = paretoVolumePv,
            recoverabilityCeilingRc = recoverabilityCeilingRc,
            growthEntryFloor = globals.growthEntryFloor,
            maintenanceCeiling = globals.maintenanceCeiling,
            posture = posture.name,
            observedDoseMax = observedDoseMax,
            observedDoseMedian = observedDoseMedian,
            source = VolumeSnapshotSource.Personal,
        )

    private fun com.stratum.domain.program.LiteratureVolumeDefaults.toSnapshotContext(): MuscleVolumeSnapshotContext =
        MuscleVolumeSnapshotContext(
            muscle = muscle.name,
            retentionFloorMv = retentionFloorMv,
            paretoVolumePv = paretoVolumePv,
            recoverabilityCeilingRc = recoverabilityCeilingRc,
            growthEntryFloor = growthEntryFloor,
            maintenanceCeiling = maintenanceCeiling,
            posture = null,
            source = VolumeSnapshotSource.Literature,
        )

    private fun mergeSnapshot(
        into: MutableMap<String, MuscleVolumeSnapshotContext>,
        snapshot: MuscleVolumeSnapshotContext,
        preferPersonal: Boolean,
    ) {
        val existing = into[snapshot.muscle]
        if (existing == null) {
            into[snapshot.muscle] = snapshot
            return
        }
        if (preferPersonal && snapshot.source == VolumeSnapshotSource.Personal) {
            into[snapshot.muscle] = snapshot
        } else if (existing.source != VolumeSnapshotSource.Personal) {
            into[snapshot.muscle] = snapshot
        }
    }

    private fun mergeVolumeCorrelations(
        responseProfile: ResponseProfilePayload?,
    ): Map<String, VolumeCorrelationContext> {
        if (responseProfile == null) return emptyMap()
        return responseProfile.volumeResponses.associate { influence ->
            influence.muscle.name to VolumeCorrelationContext(
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
                pattern = influence.pattern,
                rank = influence.rank,
                effectivenessScore = influence.effectivenessScore,
                evidence = influence.evidence.name,
            )
        }
    }
}
