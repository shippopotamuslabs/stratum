@file:Suppress("MagicNumber")

package com.stratum.core.testing.program

import com.stratum.domain.lift.MovementPattern
import com.stratum.domain.onboarding.MuscleGroup
import com.stratum.domain.onboarding.SpecializationLevel
import com.stratum.domain.program.LiteratureVolumeDefaultsProvider
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.MuscleVolumeSnapshotContext
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.context.VolumeCorrelationContext
import com.stratum.domain.program.generation.context.VolumeSnapshotSource

object GenerationContextTestFixtures {
    fun contextWith(
        profileOverrides: Map<String, Any?> = emptyMap(),
        specialization: Map<String, String> = mapOf("Chest" to "Specialize"),
        provenLifts: List<LiftContext> = defaultProvenLifts(),
        availableLifts: List<LiftContext> = emptyList(),
        literatureVolumeSnapshots: List<MuscleVolumeSnapshotContext> = defaultLiteratureSnapshots(),
        volumeCorrelations: List<VolumeCorrelationContext> = emptyList(),
    ): ProgramGenerationContext {
        val profile = mutableMapOf(
            "trainingAge" to "Intermediate",
            "sessionLengthMinutes" to 60,
            "weekdays" to listOf("Monday", "Wednesday", "Friday", "Saturday"),
            "trainingDaysPerWeek" to 4,
            "lastTrained" to null,
        )
        profile.putAll(profileOverrides)

        val liftsById = mutableMapOf<String, LiftContext>()
        val enabledLiftIds = mutableSetOf<String>()
        val movementPatternByLiftId = mutableMapOf<String, MovementPattern?>()
        for (lift in provenLifts + availableLifts) {
            liftsById[lift.id] = lift
            enabledLiftIds.add(lift.id)
            movementPatternByLiftId[lift.id] = lift.movementPattern
        }

        val muscleVolumeSnapshotsByMuscle = literatureVolumeSnapshots.associateBy { it.muscle }
        val volumeCorrelationsByMuscle = volumeCorrelations.associateBy { it.muscle }

        return ProgramGenerationContext(
            trainingAge = profile["trainingAge"] as String,
            lastTrained = profile["lastTrained"] as String?,
            sessionLengthMinutes = profile["sessionLengthMinutes"] as Int?,
            trainingDaysPerWeek = profile["trainingDaysPerWeek"] as Int,
            weekdays = (profile["weekdays"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            specialization = specialization.toSpecializationMap(),
            liftsById = liftsById,
            muscleVolumeSnapshotsByMuscle = muscleVolumeSnapshotsByMuscle,
            volumeCorrelationsByMuscle = volumeCorrelationsByMuscle,
            enabledLiftIds = enabledLiftIds,
            movementPatternByLiftId = movementPatternByLiftId,
            liftEffectivenessByLiftId = emptyMap(),
        )
    }

    private fun defaultProvenLifts(): List<LiftContext> = listOf(
        LiftContext(
            id = "bench",
            name = "bench",
            movementPattern = MovementPattern.HorizontalPress,
            movementPatterns = setOf(MovementPattern.HorizontalPress),
            primaryMuscles = setOf(MuscleGroup.Chest),
            secondaryMuscles = setOf(MuscleGroup.Tricep),
        ),
        LiftContext(
            id = "row",
            name = "row",
            movementPattern = MovementPattern.HorizontalPull,
            movementPatterns = setOf(MovementPattern.HorizontalPull),
            primaryMuscles = setOf(MuscleGroup.Back),
            secondaryMuscles = setOf(MuscleGroup.Bicep),
        ),
    )

    /** Bridges a String-keyed specialization map (as still passed by legacy callers) to enums. */
    private fun Map<String, String>.toSpecializationMap(): Map<MuscleGroup, SpecializationLevel> =
        entries.mapNotNull { (muscle, level) ->
            val mg = runCatching { MuscleGroup.valueOf(muscle) }.getOrNull() ?: return@mapNotNull null
            val sl = runCatching { SpecializationLevel.valueOf(level) }.getOrNull() ?: return@mapNotNull null
            mg to sl
        }.toMap()

    private fun defaultLiteratureSnapshots(): List<MuscleVolumeSnapshotContext> =
        listOf("Chest", "Back").map { muscle -> literatureSnapshotContext(muscle) }

    fun literatureSnapshotContext(muscle: String): MuscleVolumeSnapshotContext {
        val literature =
            LiteratureVolumeDefaultsProvider.all().single { entry -> entry.muscle.name == muscle }
        return MuscleVolumeSnapshotContext(
            muscle = literature.muscle.name,
            retentionFloorMv = literature.retentionFloorMv,
            paretoVolumePv = literature.paretoVolumePv,
            recoverabilityCeilingRc = literature.recoverabilityCeilingRc,
            growthEntryFloor = literature.growthEntryFloor,
            maintenanceCeiling = literature.maintenanceCeiling,
            posture = null,
            source = VolumeSnapshotSource.Literature,
        )
    }

    fun literatureRc(muscle: String): Double =
        LiteratureVolumeDefaultsProvider.all().single { it.muscle.name == muscle }.recoverabilityCeilingRc

    fun literaturePv(muscle: String): Double =
        LiteratureVolumeDefaultsProvider.all().single { it.muscle.name == muscle }.paretoVolumePv

    fun literatureSpecializeMin(muscle: String): Double =
        literatureSnapshotContext(muscle).growthEntryFloor

    val volumePlannerLifts: List<LiftContext> = listOf(
        LiftContext(
            id = "bench",
            name = "Bench Press",
            movementPattern = MovementPattern.HorizontalPress,
            movementPatterns = setOf(MovementPattern.HorizontalPress),
            primaryMuscles = setOf(MuscleGroup.Chest),
            secondaryMuscles = setOf(MuscleGroup.Tricep),
        ),
        LiftContext(
            id = "row",
            name = "Row",
            movementPattern = MovementPattern.HorizontalPull,
            movementPatterns = setOf(MovementPattern.HorizontalPull),
            primaryMuscles = setOf(MuscleGroup.Back),
            secondaryMuscles = setOf(MuscleGroup.Bicep),
        ),
        LiftContext(
            id = "squat",
            name = "Squat",
            movementPattern = MovementPattern.SquatPattern,
            movementPatterns = setOf(MovementPattern.SquatPattern),
            primaryMuscles = setOf(MuscleGroup.Quad),
            secondaryMuscles = setOf(MuscleGroup.Glute),
        ),
        LiftContext(
            id = "rdl",
            name = "RDL",
            movementPattern = MovementPattern.HipHinge,
            movementPatterns = setOf(MovementPattern.HipHinge),
            primaryMuscles = setOf(MuscleGroup.Hamstring),
            secondaryMuscles = setOf(MuscleGroup.Glute),
        ),
        LiftContext(
            id = "curl",
            name = "Curl",
            movementPattern = MovementPattern.ElbowFlexion,
            movementPatterns = setOf(MovementPattern.ElbowFlexion),
            primaryMuscles = setOf(MuscleGroup.Bicep),
            secondaryMuscles = emptySet(),
        ),
        LiftContext(
            id = "tricep-ext",
            name = "Tricep Extension",
            movementPattern = MovementPattern.ElbowExtension,
            movementPatterns = setOf(MovementPattern.ElbowExtension),
            primaryMuscles = setOf(MuscleGroup.Tricep),
            secondaryMuscles = emptySet(),
        ),
    )

    val volumePlannerSnapshots: List<MuscleVolumeSnapshotContext> =
        listOf("Chest", "Back", "Quad", "Hamstring", "Bicep", "Tricep").map { muscle ->
            literatureSnapshotContext(muscle)
        }

    fun volumePlannerContext(
        specialization: Map<String, String> = emptyMap(),
        profileOverrides: Map<String, Any?> = emptyMap(),
    ): ProgramGenerationContext =
        contextWith(
            provenLifts = volumePlannerLifts,
            literatureVolumeSnapshots = volumePlannerSnapshots,
            specialization = specialization,
            profileOverrides = profileOverrides,
        )
}
