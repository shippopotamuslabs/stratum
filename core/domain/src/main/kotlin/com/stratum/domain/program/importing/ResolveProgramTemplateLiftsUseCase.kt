package com.stratum.domain.program.importing

import com.stratum.domain.importing.ImportStubLiftGuesser
import com.stratum.domain.importing.LiftNameResolverPort
import com.stratum.domain.lift.LiftRepository
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.DEFAULT_LIFT_SET_REST_SECONDS
import com.stratum.domain.program.LiftSet
import com.stratum.domain.program.Program
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.SetType
import com.stratum.domain.program.Workout
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.ProgramId
import com.stratum.domain.program.identity.WorkoutId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.program.identity.WorkoutSupersetGroupId
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Resolves a [NormalizedProgramTemplate] against the lift catalog and maps it to a domain [Program].
 *
 * Resolution strategy (mirrors the historical-import lift resolution):
 *  1. Load the full catalog **including** prior `isCustom` lifts so the user's existing customs are
 *     name-matchable.
 *  2. For each distinct imported lift name, call [LiftNameResolverPort.resolve] (exact-name first,
 *     then embeddings best-match via [com.stratum.domain.importing.HybridLiftNameResolver]).
 *  3. Unmatched names become new custom lifts via [LiftRepository.addCustomLift] (deterministic
 *     stub metadata from [ImportStubLiftGuesser]).
 *
 * The resulting [Program] uses [ProgressionScheme.Linear] as a safe default for every lift (the
 * imported template carries no training-age signal to assign [ProgressionScheme.Double]), and maps
 * source set-type labels onto [SetType] where recognizable. MacroFactor superset group keys are
 * preserved as [WorkoutLift.supersetGroupId] within each workout.
 */
class ResolveProgramTemplateLiftsUseCase @Inject constructor(
    private val liftRepository: LiftRepository,
    private val liftNameResolver: LiftNameResolverPort,
) {
    suspend operator fun invoke(
        template: NormalizedProgramTemplate,
    ): Program {
        val catalog = liftRepository.observeLifts().first()
        val catalogNames = catalog.associate { it.id to it.name }

        val distinctNames = template.workouts.flatMap { w -> w.lifts.map { it.name } }.distinct()
        liftNameResolver.prepareBatch(distinctNames)

        // Cache name -> LiftId, creating a custom lift for any unmatched name.
        val resolutionByLiftName = mutableMapOf<String, LiftId>()
        for (name in distinctNames) {
            val existing = liftNameResolver.resolve(name, catalogNames)
            if (existing != null) {
                resolutionByLiftName[name] = existing
            } else {
                val definition = ImportStubLiftGuesser.definitionFromName(
                    name = name,
                    increment = DEFAULT_INCREMENT,
                )
                val createdId = liftRepository.addCustomLift(definition)
                resolutionByLiftName[name] = createdId
            }
        }

        val programId = ProgramId(UUID.randomUUID().toString())
        // Build domain workouts, skipping rest days but consuming weekday slots for them so the
        // rendered schedule matches the source cycle (e.g. MT-Rest-TRF-Rest rather than MTWTF).
        val workouts = mutableListOf<Workout>()
        var workoutPosition = 0
        template.workouts.forEachIndexed { cycleIndex, templateWorkout ->
            val weekday = templateWorkout.weekday?.let { mapWeekday(it) }
                ?: Weekday.entries.getOrNull(cycleIndex)
            if (templateWorkout.restDay) {
                return@forEachIndexed // rest day consumes a weekday slot, no domain Workout created
            }
            val workoutId = WorkoutId(UUID.randomUUID().toString())
            // Group superset keys are scoped per-workout: equal keys within a workout share an id.
            val supersetIdByKey = mutableMapOf<String, WorkoutSupersetGroupId>()
            val workoutLifts = templateWorkout.lifts.mapIndexed { liftIndex, templateLift ->
                val workoutLiftId = WorkoutLiftId(UUID.randomUUID().toString())
                val liftId = resolutionByLiftName.getValue(templateLift.name)
                val supersetGroupId = templateLift.supersetGroupKey?.let { key ->
                    supersetIdByKey.getOrPut(key) {
                        WorkoutSupersetGroupId(UUID.randomUUID().toString())
                    }
                }
                WorkoutLift(
                    id = workoutLiftId,
                    workoutId = workoutId,
                    liftId = liftId,
                    progressionScheme = ProgressionScheme.Linear,
                    position = templateLift.position,
                    sets = templateLift.sets.mapIndexed { setIndex, templateSet ->
                        LiftSet(
                            id = LiftSetId(UUID.randomUUID().toString()),
                            workoutLiftId = workoutLiftId,
                            position = templateSet.position.coerceAtLeast(setIndex),
                            setType = mapSetType(templateSet.setTypeLabel),
                            repMin = templateSet.repMin ?: DEFAULT_REP_MIN,
                            repMax = templateSet.repMax ?: DEFAULT_REP_MAX,
                            rirGoal = templateSet.rirGoal ?: DEFAULT_RIR_GOAL,
                            isTopSet = templateSet.isTopSet,
                            restSeconds = templateSet.restSeconds ?: DEFAULT_LIFT_SET_REST_SECONDS,
                        )
                    },
                    supersetGroupId = supersetGroupId,
                )
            }
            workouts += Workout(
                id = workoutId,
                programId = programId,
                name = templateWorkout.name,
                weekday = weekday,
                position = workoutPosition,
                workoutLifts = workoutLifts,
            )
            workoutPosition += 1
        }

        val resolvedCycleLength = template.cycleLength?.takeIf { it > 0 } ?: template.workouts.size
        return Program(
            id = programId,
            name = template.programName ?: DEFAULT_PROGRAM_NAME,
            createdAt = Instant.now(),
            cycleLength = resolvedCycleLength,
            trainingWeekdays = emptySet(),
            workouts = workouts,
        )
    }

    private fun mapSetType(label: String?): SetType {
        if (label == null) return SetType.Standard
        val normalized = label.trim().lowercase()
        return when {
            normalized.contains("myo") -> SetType.MyoRep
            normalized.contains("drop") -> SetType.DropSet
            else -> SetType.Standard
        }
    }

    /** Loose weekday parse; null when the source label is not a recognizable weekday. */
    private fun mapWeekday(label: String): com.stratum.domain.onboarding.Weekday? {
        val normalized = label.trim().lowercase()
        return com.stratum.domain.onboarding.Weekday.entries.firstOrNull { day ->
            normalized.startsWith(day.name.lowercase().take(WEEKDAY_PREFIX_LEN))
        }
    }

    private companion object {
        const val DEFAULT_REP_MIN = 8
        const val DEFAULT_REP_MAX = 12
        const val DEFAULT_RIR_GOAL = 2
        const val WEEKDAY_PREFIX_LEN = 3
        val DEFAULT_INCREMENT = Weight(DEFAULT_CUSTOM_INCREMENT_LB, Weight.WeightUnit.Lb)
        const val DEFAULT_CUSTOM_INCREMENT_LB = 5.0
        const val DEFAULT_PROGRAM_NAME = "Imported Program"
    }
}
