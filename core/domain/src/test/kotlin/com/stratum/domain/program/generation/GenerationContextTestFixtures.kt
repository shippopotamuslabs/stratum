package com.stratum.domain.program.generation

import com.stratum.domain.program.LiteratureVolumeDefaultsProvider
import com.stratum.domain.program.generation.context.LiftContext
import com.stratum.domain.program.generation.context.MuscleVolumeSnapshotContext
import com.stratum.domain.program.generation.context.ProgramGenerationContext
import com.stratum.domain.program.generation.context.VolumeCorrelationContext
import com.stratum.domain.program.generation.context.VolumeSnapshotSource

internal object GenerationContextTestFixtures {
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
        val movementPatternByLiftId = mutableMapOf<String, String>()
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
            specialization = specialization,
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
            movementPattern = "HorizontalPress",
            movementPatterns = listOf("HorizontalPress"),
            primaryMuscles = listOf("Chest"),
            secondaryMuscles = listOf("Tricep"),
        ),
        LiftContext(
            id = "row",
            name = "row",
            movementPattern = "HorizontalPull",
            movementPatterns = listOf("HorizontalPull"),
            primaryMuscles = listOf("Back"),
            secondaryMuscles = listOf("Bicep"),
        ),
    )

    private fun defaultLiteratureSnapshots(): List<MuscleVolumeSnapshotContext> =
        listOf("Chest", "Back").map { muscle -> literatureSnapshotContext(muscle) }

    internal fun literatureSnapshotContext(muscle: String): MuscleVolumeSnapshotContext {
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

    internal fun literatureRc(muscle: String): Double =
        LiteratureVolumeDefaultsProvider.all().single { it.muscle.name == muscle }.recoverabilityCeilingRc

    internal fun literaturePv(muscle: String): Double =
        LiteratureVolumeDefaultsProvider.all().single { it.muscle.name == muscle }.paretoVolumePv

    internal fun literatureSpecializeMin(muscle: String): Double =
        literatureSnapshotContext(muscle).growthEntryFloor

    val volumePlannerLifts: List<LiftContext> = listOf(
        LiftContext(
            id = "bench",
            name = "Bench Press",
            movementPattern = "HorizontalPress",
            movementPatterns = listOf("HorizontalPress"),
            primaryMuscles = listOf("Chest"),
            secondaryMuscles = listOf("Tricep"),
        ),
        LiftContext(
            id = "row",
            name = "Row",
            movementPattern = "HorizontalPull",
            movementPatterns = listOf("HorizontalPull"),
            primaryMuscles = listOf("Back"),
            secondaryMuscles = listOf("Bicep"),
        ),
        LiftContext(
            id = "squat",
            name = "Squat",
            movementPattern = "SquatPattern",
            movementPatterns = listOf("SquatPattern"),
            primaryMuscles = listOf("Quad"),
            secondaryMuscles = listOf("Glute"),
        ),
        LiftContext(
            id = "rdl",
            name = "RDL",
            movementPattern = "HipHinge",
            movementPatterns = listOf("HipHinge"),
            primaryMuscles = listOf("Hamstring"),
            secondaryMuscles = listOf("Glute"),
        ),
        LiftContext(
            id = "curl",
            name = "Curl",
            movementPattern = "ElbowFlexion",
            movementPatterns = listOf("ElbowFlexion"),
            primaryMuscles = listOf("Bicep"),
            secondaryMuscles = emptyList(),
        ),
        LiftContext(
            id = "tricep-ext",
            name = "Tricep Extension",
            movementPattern = "ElbowExtension",
            movementPatterns = listOf("ElbowExtension"),
            primaryMuscles = listOf("Tricep"),
            secondaryMuscles = emptyList(),
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
