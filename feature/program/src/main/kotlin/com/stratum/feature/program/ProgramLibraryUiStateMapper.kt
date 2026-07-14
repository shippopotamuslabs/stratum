package com.stratum.feature.program

import com.stratum.core.ui.component.ProgramLibraryCardState
import com.stratum.core.ui.component.ProgramLibraryDayState
import com.stratum.core.ui.component.toDisplayName
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.Program
import com.stratum.domain.program.Workout
import com.stratum.domain.program.schedule.CycleSlot
import com.stratum.domain.program.schedule.resolvedCycleSlots

/**
 * Domain → presentation mapping for Program Library cards.
 *
 * Day list is the **cycle window** via [resolvedCycleSlots] (same identity as Programs strip),
 * not a Mon–Sun weekday expansion. Rest rows use [restDayName]; train rows keep workout names.
 * Library inventory often has null [Program.cycleStartEpochDay], so cards show slot order
 * (including 8+ day rotating rests) without calendar projection.
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
        resolvedCycleSlots().map { slot ->
            when (slot) {
                is CycleSlot.Rest ->
                    ProgramLibraryDayState(
                        id = "rest-${id.value}-${slot.slotIndex}",
                        name = restDayName,
                        isRest = true,
                    )
                is CycleSlot.Training ->
                    slot.workout.toLibraryDayState(liftsById)
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
