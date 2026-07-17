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
import com.stratum.domain.program.schedule.restSlotIndicesForSevenDayWeek
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
 * Cycle space materialization (independent of provider meso / "Cycles: N"):
 *  - Walk template workouts in order. [NormalizedProgramTemplateWorkout.restDay] rows become
 *    [Program.restSlotIndices] (no domain [Workout]); training rows become workouts at positions
 *    0..T-1.
 *  - [Program.cycleLength] = train count + rest count (actual slot space). Provider
 *    [NormalizedProgramTemplate.cycleLength] is ignored for slot space (MacroFactor mesocycles,
 *    Alpha Progression week counts).
 *  - When the template has no rest markers but training rows carry weekdays that fit a classic
 *    7-day week, rests are derived via [restSlotIndicesForSevenDayWeek].
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
    /** Current catalog display names keyed by lift id (includes customs created during resolve). */
    suspend fun catalogNamesById(): Map<LiftId, String> =
        liftRepository.observeLifts().first().associate { it.id to it.name }

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
        // Walk template rows in order: rest days become rest slot indices; training days become
        // domain workouts at positions 0..T-1 (slot space, not provider meso length).
        val workouts = mutableListOf<Workout>()
        val restSlotIndices = mutableSetOf<Int>()
        val explicitTrainingWeekdays = mutableSetOf<Weekday>()
        var workoutPosition = 0
        template.workouts.forEachIndexed { slotIndex, templateWorkout ->
            if (templateWorkout.restDay) {
                restSlotIndices += slotIndex
                return@forEachIndexed
            }
            val explicitWeekday = templateWorkout.weekday?.let { mapWeekday(it) }
            if (explicitWeekday != null) {
                explicitTrainingWeekdays += explicitWeekday
            }
            // Prefer source weekday; fall back to Mon-start slot index for projection cache when
            // the template interleaves rests without labels (MacroFactor day rows).
            val weekday = explicitWeekday ?: Weekday.entries.getOrNull(slotIndex)
            val workoutId = WorkoutId(UUID.randomUUID().toString())
            // Group superset keys are scoped per-workout: equal keys within a workout share an id.
            val supersetIdByKey = mutableMapOf<String, WorkoutSupersetGroupId>()
            val workoutLifts = templateWorkout.lifts.map { templateLift ->
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

        val trainCount = workouts.size
        val (cycleLength, resolvedRestSlots) =
            resolveImportCycleSpace(
                trainCount = trainCount,
                restSlotIndicesFromTemplate = restSlotIndices,
                explicitTrainingWeekdays = explicitTrainingWeekdays,
            )
        // trainingWeekdays is a denormalized cache of the per-workout weekday bindings above; derive
        // it here so the imported Program is consistent at construction (rest slots have no Workout).
        return Program(
            id = programId,
            name = template.programName ?: DEFAULT_PROGRAM_NAME,
            createdAt = Instant.now(),
            cycleLength = cycleLength,

            workouts = workouts,
            restSlotIndices = resolvedRestSlots,
        )
    }

    /**
     * Slot-space identity for imports. Never uses provider meso / "Cycles: N" as [cycleLength].
     *
     * Precedence:
     * 1. Explicit rest-day rows in the template → rests + train+rest length.
     * 2. No rest markers but source weekdays fit a classic 7-day week → weekday complement.
     * 3. Train-only fallback: cycleLength = trainCount, empty rests.
     */
    private fun resolveImportCycleSpace(
        trainCount: Int,
        restSlotIndicesFromTemplate: Set<Int>,
        explicitTrainingWeekdays: Set<Weekday>,
    ): Pair<Int, Set<Int>> {
        if (trainCount <= 0) {
            // Server validation requires ≥1 training day; keep a positive length if reached.
            val restCount = restSlotIndicesFromTemplate.size
            return (restCount.coerceAtLeast(1)) to restSlotIndicesFromTemplate
        }
        if (restSlotIndicesFromTemplate.isNotEmpty()) {
            val cycleLength = trainCount + restSlotIndicesFromTemplate.size
            val restsInRange =
                restSlotIndicesFromTemplate.all { index -> index in 0 until cycleLength }
            if (restsInRange) {
                return cycleLength to restSlotIndicesFromTemplate
            }
            // Malformed rest indices — fall through rather than fail open with meso length.
        }
        if (explicitTrainingWeekdays.isNotEmpty()) {
            val rests = restSlotIndicesForSevenDayWeek(explicitTrainingWeekdays)
            val trainingFromWeekdays = 7 - rests.size
            if (trainingFromWeekdays == trainCount) {
                return 7 to rests
            }
        }
        return trainCount to emptySet()
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
