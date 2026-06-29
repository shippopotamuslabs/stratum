package com.stratum.domain.program

import com.stratum.domain.onboarding.Weekday

/** Training weekdays persisted on the program, or inferred from bound workouts when unset. */
fun Program.effectiveTrainingWeekdays(): Set<Weekday> =
    trainingWeekdays.ifEmpty { workouts.mapNotNull { it.weekday }.toSet() }

/**
 * Binds each workout in cycle order to a training weekday and persists [trainingWeekdays] on the
 * program. Rest days are the complement of the training set across the calendar week.
 */
fun assignProgramSchedule(program: Program, trainingWeekdays: List<Weekday>): Program {
    val sorted =
        trainingWeekdays.distinct().sortedBy { weekday -> Weekday.entries.indexOf(weekday) }
    require(sorted.isNotEmpty()) { "assignProgramSchedule requires at least one training weekday." }
    require(program.workouts.size == sorted.size) {
        "Workout count ${program.workouts.size} must match training days ${sorted.size}."
    }
    val workoutsByPosition = program.workouts.sortedBy { it.position }
    val boundWorkouts =
        workoutsByPosition.mapIndexed { index, workout ->
            workout.copy(weekday = sorted[index])
        }
    return program.copy(
        trainingWeekdays = sorted.toSet(),
        workouts = boundWorkouts,
    )
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
