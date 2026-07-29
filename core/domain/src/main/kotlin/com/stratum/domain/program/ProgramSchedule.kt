package com.stratum.domain.program

import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.generation.split.MuscleSessionExposure
import com.stratum.domain.program.generation.reference.MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE
import com.stratum.domain.program.generation.slots.SessionTypeId
import com.stratum.domain.program.generation.slots.primaryMusclesForSessionType

private const val MAX_SESSION_LETTER_INDEX = 25

/**
 * Training weekdays for schedule rendering. [Workout.weekday] is the single source of truth; this
 * function prefers it and falls back to [Program.trainingWeekdays] only when no workout carries a
 * weekday binding (a defensive path for callers that construct a Program without per-session
 * bindings). Under normal generation/import flows the two are kept equal by
 * [withReconciledTrainingWeekdays], so this fallback is a no-op.
 */
fun Program.effectiveTrainingWeekdays(): Set<Weekday> {
    val fromWorkouts = workouts.mapNotNull { workout -> workout.weekday }.toSet()
    if (fromWorkouts.isNotEmpty()) return fromWorkouts
    return trainingWeekdays
}

/**
 * Re-derives [Program.trainingWeekdays] from bound [Workout.weekday] rows. Call this whenever the
 * workout list may have changed (e.g. after sanitization drops workouts) to keep the denormalized
 * cache in sync with the authoritative per-session bindings. Re-derives unconditionally — when no
 * workout carries a weekday, the cache is cleared rather than left stale.
 */
fun Program.withReconciledTrainingWeekdays(): Program {
    val fromWorkouts = workouts.mapNotNull { workout -> workout.weekday }.toSet()
    return if (trainingWeekdays == fromWorkouts) this else copy(trainingWeekdays = fromWorkouts)
}

/**
 * Binds workouts using CP-SAT cycle slot indices and renumbers session letters.
 *
 * Slot indices are on the generation ring (classic Mon=0…Sun=6, or 6-in-8 train slots such as
 * 0,1,2,4,5,6). [Weekday] labels are projection labels when the index is in 0…6.
 */
fun assignProgramScheduleFromWeekdayIndices(
    program: Program,
    cycleSlotIndexByPosition: List<Int>,
): Program {
    val sortedWorkouts = program.workouts.sortedBy { workout -> workout.position }
    require(sortedWorkouts.size == cycleSlotIndexByPosition.size) {
        "Cycle slot index count ${cycleSlotIndexByPosition.size} must match workout count ${sortedWorkouts.size}."
    }
    val boundWorkouts =
        sortedWorkouts.mapIndexed { index, workout ->
            val slot = cycleSlotIndexByPosition[index]
            workout.copy(weekday = Weekday.entries.getOrNull(slot))
        }
    val trainingWeekdays =
        cycleSlotIndexByPosition.mapNotNull { index -> Weekday.entries.getOrNull(index) }.toSet()
    return program.copy(
        trainingWeekdays = trainingWeekdays,
        workouts = relabelWorkoutNamesByWeekday(boundWorkouts),
    )
}

/**
 * Binds each workout in cycle order to a training weekday and persists [trainingWeekdays] on the
 * program. Rest days are the complement of the training set across the calendar week.
 */
fun assignProgramSchedule(
    program: Program,
    trainingWeekdays: List<Weekday>,
): Program {
    val sorted =
        trainingWeekdays.distinct().sortedBy { weekday -> Weekday.entries.indexOf(weekday) }
    require(sorted.isNotEmpty()) { "assignProgramSchedule requires at least one training weekday." }
    require(program.workouts.size == sorted.size) {
        "Workout count ${program.workouts.size} must match training days ${sorted.size}."
    }
    val workoutsByPosition = program.workouts.sortedBy { it.position }
    val boundWorkouts =
        relabelWorkoutNamesByWeekday(
            workoutsByPosition.mapIndexed { index, workout ->
                workout.copy(weekday = sorted[index])
            },
        )
    return program.copy(
        trainingWeekdays = sorted.toSet(),
        workouts = boundWorkouts,
    )
}

/**
 * After weekday binding, session letters (A/B/C) still reflect generation order.
 * Renumber each session family by calendar weekday so Mon→A, Wed→B, Fri→C reads naturally.
 */
internal fun relabelWorkoutNamesByWeekday(workouts: List<Workout>): List<Workout> {
    fun sessionBaseName(name: String): String = name.replace(Regex(""" [A-Z]$"""), "")

    val renameByPosition = mutableMapOf<Int, String>()
    val groups = workouts.groupBy { workout -> sessionBaseName(workout.name) }
    for ((base, group) in groups) {
        val sorted =
            group.sortedBy { workout ->
                workout.weekday?.let { weekday -> Weekday.entries.indexOf(weekday) } ?: workout.position
            }
        sorted.forEachIndexed { index, workout ->
            val letter = ('A'.code + index.coerceAtMost(MAX_SESSION_LETTER_INDEX)).toChar()
            renameByPosition[workout.position] = "$base $letter"
        }
    }
    return workouts.map { workout ->
        renameByPosition[workout.position]?.let { newName -> workout.copy(name = newName) } ?: workout
    }
}

/**
 * Builds per-muscle calendar exposures for recovery streak analysis.
 *
 * Prefer passing [workoutMuscleExposures] (metadata / dose truth). Structure is **not** inferred
 * from workout names — names are polish (heuristic titles) and [MuscleSessionExposure.sessionType]
 * is left null on the dose path. The no-dose branch is a legacy estimated fallback only
 * (FullBody template muscles); it does not parse names.
 */
fun buildScheduleMuscleExposures(
    workouts: List<Workout>,
    weekdayIndices: List<Int>,
    workoutMuscleExposures: List<Map<String, Double>?>? = null,
): List<MuscleSessionExposure> {
    val out = mutableListOf<MuscleSessionExposure>()
    workouts.forEachIndexed { sessionIdx, workout ->
        val weekdayIdx = weekdayIndices.getOrElse(sessionIdx) { sessionIdx }
        val doseMap = workoutMuscleExposures?.getOrNull(sessionIdx)
        if (doseMap != null && doseMap.isNotEmpty()) {
            for ((muscle, dose) in doseMap) {
                if (dose >= MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE) {
                    out.add(
                        MuscleSessionExposure(
                            muscle = muscle,
                            weekdayIndex = weekdayIdx,
                            fractionalSets = dose,
                            workoutName = workout.name,
                            // Not structure truth; adjacency helpers that need SessionTypeId
                            // should construct exposures explicitly (tests), not parse names.
                            sessionType = null,
                        ),
                    )
                }
            }
        } else {
            // Legacy estimated path when no dose map — do not parse workout names.
            val estimatedType = SessionTypeId.FullBody
            for (muscle in primaryMusclesForSessionType(estimatedType)) {
                out.add(
                    MuscleSessionExposure(
                        muscle = muscle.name,
                        weekdayIndex = weekdayIdx,
                        fractionalSets = 5.0,
                        workoutName = workout.name,
                        sessionType = estimatedType,
                    ),
                )
            }
        }
    }
    return out
}

/**
 * Builds a Mon–Sun schedule list with explicit rest-day rows and workout rows.
 *
 * **Deprecated for runtime UI** — prefer cycle-window projection via
 * [com.stratum.domain.program.schedule.CycleProjection.projectCycleWindow] and
 * [com.stratum.domain.program.schedule.resolvedCycleSlots]. Kept for domain tests and
 * weekday-bound generation checks that still model calendar templates.
 */
fun buildWeeklySchedule(program: Program): List<ScheduleDayItem> {
    val training = program.effectiveTrainingWeekdays()
    val workoutByWeekday =
        program.workouts.filter { workout -> workout.weekday != null }.associateBy { workout ->
            workout.weekday!!
        }
    return Weekday.entries.map { weekday ->
        if (weekday !in training) {
            ScheduleDayItem.RestDay(weekday)
        } else {
            val workout =
                workoutByWeekday[weekday]
                    ?: error("Missing workout for training weekday $weekday on program ${program.id}")
            ScheduleDayItem.WorkoutDay(weekday = weekday, workout = workout)
        }
    }
}

/**
 * Like [buildWeeklySchedule], but never throws. When weekday bindings are incomplete or missing,
 * falls back to workouts ordered by [Workout.position] (using each workout's weekday when present,
 * otherwise [Weekday.Monday] as a non-calendar placeholder).
 *
 * Prefer cycle-slot projection
 * ([com.stratum.domain.program.schedule.resolvedCycleSlots]) for presentation. Use this only for
 * weekday-bound test helpers; use [buildWeeklySchedule] when an invariant violation should
 * surface (e.g. generation assertions).
 *
 * Note: [buildWeeklySchedule] succeeds with seven rest days when workouts exist but none are
 * weekday-bound (empty [Program.effectiveTrainingWeekdays]). That silent all-rest result is
 * treated as incomplete binding and falls back so callers still see the workouts.
 */
fun buildWeeklyScheduleOrFallback(program: Program): List<ScheduleDayItem> {
    val training = program.effectiveTrainingWeekdays()
    if (program.workouts.isNotEmpty() && training.isEmpty()) {
        return fallbackScheduleFromWorkouts(program)
    }
    return runCatching { buildWeeklySchedule(program) }
        .getOrElse { fallbackScheduleFromWorkouts(program) }
}

private fun fallbackScheduleFromWorkouts(program: Program): List<ScheduleDayItem> =
    program.workouts.sortedBy { workout -> workout.position }.map { workout ->
        ScheduleDayItem.WorkoutDay(
            weekday = workout.weekday ?: Weekday.Monday,
            workout = workout,
        )
    }
