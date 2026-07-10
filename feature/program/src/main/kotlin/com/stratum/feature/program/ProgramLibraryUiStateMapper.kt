package com.stratum.feature.program

import com.stratum.core.ui.component.ProgramLibraryCardState
import com.stratum.core.ui.component.ProgramLibraryDayState
import com.stratum.core.ui.component.toDisplayName
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.Program
import com.stratum.domain.program.ScheduleDayItem
import com.stratum.domain.program.Workout
import com.stratum.domain.program.buildWeeklyScheduleOrFallback

/**
 * Domain → presentation mapping for Program Library cards. Uses [buildWeeklyScheduleOrFallback] so
 * rest days match Programs screen schedule rows without crashing on unbound weekdays.
 */
internal fun List<Program>.toProgramLibraryCards(
    liftsById: Map<LiftId, Lift>,
    restDayName: String,
): List<ProgramLibraryCardState> =
    map { program -> program.toProgramLibraryCard(liftsById, restDayName) }

internal fun Program.toProgramLibraryCard(
    liftsById: Map<LiftId, Lift>,
    restDayName: String,
): ProgramLibraryCardState {
    val days =
        buildWeeklyScheduleOrFallback(this).map { item ->
            when (item) {
                is ScheduleDayItem.RestDay ->
                    ProgramLibraryDayState(
                        id = "rest-${id.value}-${item.weekday.name}",
                        name = restDayName,
                        isRest = true,
                    )
                is ScheduleDayItem.WorkoutDay ->
                    item.workout.toLibraryDayState(liftsById)
            }
        }
    return ProgramLibraryCardState(
        programId = id.value,
        name = name,
        workoutCount = workouts.size,
        isActive = isActive,
        days = days,
    )
}

private fun Workout.toLibraryDayState(liftsById: Map<LiftId, Lift>): ProgramLibraryDayState {
    val sortedLifts = workoutLifts.sortedBy { lift -> lift.position }
    val liftNames =
        sortedLifts.map { workoutLift ->
            liftsById[workoutLift.liftId]?.name
                ?: resolveLiftDisplayName(workoutLift.liftId.value, emptyMap())
        }
    val muscleLabels =
        sortedLifts
            .flatMap { workoutLift ->
                liftsById[workoutLift.liftId]?.primaryMuscles.orEmpty()
            }
            .distinct()
            .sortedBy { muscle -> muscle.ordinal }
            .map { muscle -> muscle.toDisplayName() }
    return ProgramLibraryDayState(
        id = id.value,
        name = name,
        isRest = false,
        liftNamesSummary = liftNames.joinToString(separator = ", "),
        muscleLabels = muscleLabels,
        workoutId = id.value,
    )
}
