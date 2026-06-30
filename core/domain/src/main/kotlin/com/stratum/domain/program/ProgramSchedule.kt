package com.stratum.domain.program

import com.stratum.domain.onboarding.Weekday
import com.stratum.domain.program.generation.split.MuscleSessionExposure
import com.stratum.domain.program.generation.split.RecoveryScheduleSummary
import com.stratum.domain.program.generation.reference.MUSCLE_RECOVERY_MIN_SCHEDULING_DOSE
import com.stratum.domain.program.generation.split.recoveryScheduleSummary
import com.stratum.domain.program.generation.split.inferSessionTypeFromWorkoutName
import com.stratum.domain.program.generation.split.SessionTypeId
import com.stratum.domain.program.generation.split.primaryMusclesForSessionType
import com.stratum.domain.program.generation.split.sortedWeekdayIndicesFromWeekdays

private const val MAX_SESSION_LETTER_INDEX = 25

/** Training weekdays persisted on the program, or inferred from bound workouts when unset. */
fun Program.effectiveTrainingWeekdays(): Set<Weekday> =
    trainingWeekdays.ifEmpty { workouts.mapNotNull { it.weekday }.toSet() }

/**
 * Binds each workout in cycle order to a training weekday and persists [trainingWeekdays] on the
 * program. Rest days are the complement of the training set across the calendar week.
 *
 * When multiple workouts exist, permutes workout-to-weekday assignment to maximize calendar
 * recovery between sessions using per-muscle fractional set exposure when provided.
 */
fun assignProgramSchedule(
    program: Program,
    trainingWeekdays: List<Weekday>,
    workoutMuscleExposures: List<Map<String, Double>>? = null,
    recoveryPriorityMuscles: Set<String> = emptySet(),
): Program {
    val sorted =
        trainingWeekdays.distinct().sortedBy { weekday -> Weekday.entries.indexOf(weekday) }
    require(sorted.isNotEmpty()) { "assignProgramSchedule requires at least one training weekday." }
    require(program.workouts.size == sorted.size) {
        "Workout count ${program.workouts.size} must match training days ${sorted.size}."
    }
    val workoutsByPosition = program.workouts.sortedBy { it.position }
    val weekdayIndices = sortedWeekdayIndicesFromWeekdays(sorted)
    val orderedWorkouts = optimizeWorkoutWeekdayAssignment(
        workouts = workoutsByPosition,
        weekdayIndices = weekdayIndices,
        workoutMuscleExposures = workoutMuscleExposures,
        recoveryPriorityMuscles = recoveryPriorityMuscles,
    )
    val boundWorkouts =
        relabelWorkoutNamesByWeekday(
            orderedWorkouts.mapIndexed { index, workout ->
                workout.copy(weekday = sorted[index])
            },
        )
    return program.copy(
        trainingWeekdays = sorted.toSet(),
        workouts = boundWorkouts,
    )
}

/**
 * After recovery-based permutation, session letters (A/B/C) still reflect generation order.
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

internal fun optimizeWorkoutWeekdayAssignment(
    workouts: List<Workout>,
    weekdayIndices: List<Int>,
    workoutMuscleExposures: List<Map<String, Double>>? = null,
    recoveryPriorityMuscles: Set<String> = emptySet(),
): List<Workout> {
    if (workouts.size <= 1 || workouts.size != weekdayIndices.size) return workouts
    val permutations = allPermutations(workouts.indices.toList())
    var bestOrder = workouts
    var bestSummary: RecoveryScheduleSummary? = null
    var bestScore = Int.MIN_VALUE
    for (perm in permutations) {
        val permutedWorkouts = perm.map { workouts[it] }
        val exposures = buildScheduleMuscleExposures(
            workouts = permutedWorkouts,
            weekdayIndices = weekdayIndices,
            workoutMuscleExposures = perm.map { workoutMuscleExposures?.getOrNull(it) },
        )
        val summary = recoveryScheduleSummary(exposures, recoveryPriorityMuscles)
        val score = 100 - summary.totalPenalty
        if (
            bestSummary == null ||
                isBetterScheduleSummary(
                    summary = summary,
                    score = score,
                    priorityMuscles = recoveryPriorityMuscles,
                    best = bestSummary,
                    bestScore = bestScore,
                )
        ) {
            bestSummary = summary
            bestScore = score
            bestOrder = permutedWorkouts
        }
    }
    return bestOrder
}

internal fun scheduleRecoveryScore(
    workouts: List<Workout>,
    weekdayIndices: List<Int>,
    workoutMuscleExposures: List<Map<String, Double>?>? = null,
    recoveryPriorityMuscles: Set<String> = emptySet(),
): Int {
    val exposures = buildScheduleMuscleExposures(workouts, weekdayIndices, workoutMuscleExposures)
    return 100 - recoveryScheduleSummary(exposures, recoveryPriorityMuscles).totalPenalty
}

internal fun buildScheduleMuscleExposures(
    workouts: List<Workout>,
    weekdayIndices: List<Int>,
    workoutMuscleExposures: List<Map<String, Double>?>? = null,
): List<MuscleSessionExposure> {
    val out = mutableListOf<MuscleSessionExposure>()
    workouts.forEachIndexed { sessionIdx, workout ->
        val weekdayIdx = weekdayIndices.getOrElse(sessionIdx) { sessionIdx }
        val sessionType = inferSessionTypeFromWorkoutName(workout.name)
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
                            sessionType = sessionType,
                        ),
                    )
                }
            }
        } else {
            val resolvedSessionType = sessionType ?: SessionTypeId.FullBody
            for (muscle in primaryMusclesForSessionType(resolvedSessionType)) {
                out.add(
                    MuscleSessionExposure(
                        muscle = muscle,
                        weekdayIndex = weekdayIdx,
                        fractionalSets = 5.0,
                        workoutName = workout.name,
                        sessionType = resolvedSessionType,
                    ),
                )
            }
        }
    }
    return out
}

private fun isBetterScheduleSummary(
    summary: RecoveryScheduleSummary,
    score: Int,
    priorityMuscles: Set<String>,
    best: RecoveryScheduleSummary,
    bestScore: Int,
): Boolean {
    for (muscle in priorityMuscleComparisonOrder(priorityMuscles)) {
        val violations = summary.priorityViolationsByMuscle[muscle] ?: 0
        val bestViolationsForMuscle = best.priorityViolationsByMuscle[muscle] ?: 0
        if (violations != bestViolationsForMuscle) {
            return violations < bestViolationsForMuscle
        }
    }
    return when {
        summary.maxPriorityConsecutiveExposureDays != best.maxPriorityConsecutiveExposureDays ->
            summary.maxPriorityConsecutiveExposureDays < best.maxPriorityConsecutiveExposureDays
        summary.violationCount != best.violationCount ->
            summary.violationCount < best.violationCount
        summary.prioritySeverityScore != best.prioritySeverityScore ->
            summary.prioritySeverityScore < best.prioritySeverityScore
        summary.severityScore != best.severityScore ->
            summary.severityScore < best.severityScore
        summary.totalPenalty != best.totalPenalty ->
            summary.totalPenalty < best.totalPenalty
        else -> score > bestScore
    }
}

private fun priorityMuscleComparisonOrder(priorityMuscles: Set<String>): List<String> {
    val preferred = listOf("Tricep", "Calf", "Bicep")
    val ordered = preferred.filter { it in priorityMuscles }
    return ordered + priorityMuscles.filter { it !in preferred }.sorted()
}

private fun <T> allPermutations(items: List<T>): List<List<T>> {
    if (items.size <= 1) return listOf(items)
    if (items.size > 6) {
        return listOf(items)
    }
    val result = mutableListOf<List<T>>()
    fun permute(current: MutableList<T>, remaining: MutableList<T>) {
        if (remaining.isEmpty()) {
            result.add(current.toList())
            return
        }
        for (i in remaining.indices) {
            val next = remaining.removeAt(i)
            current.add(next)
            permute(current, remaining)
            current.removeAt(current.lastIndex)
            remaining.add(i, next)
        }
    }
    permute(mutableListOf(), items.toMutableList())
    return result
}

/** Builds a Mon–Sun schedule list with explicit rest-day rows and workout rows. */
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
