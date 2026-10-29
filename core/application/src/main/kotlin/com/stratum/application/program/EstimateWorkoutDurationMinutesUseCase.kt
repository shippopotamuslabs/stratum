package com.stratum.application.program

import javax.inject.Inject
import kotlin.math.ceil
import com.stratum.domain.program.WorkoutLift
import com.stratum.domain.program.WorkoutLiftDurationInput

/** Active work per rep (concentric/eccentric + brief pauses). */
private const val SECONDS_PER_REP = 5

/**
 * Fixed rack / walk-up / log overhead per set, independent of rep count.
 * Low-rep compounds would otherwise undercount wall time if only reps × 5s were used.
 */
private const val SETUP_SECONDS_PER_SET = 15

/** Mid-band of default prescription range (8–12) when no positive band is present. */
private const val DEFAULT_REPS_PER_SET = 10

/** Transition time between lifts in a superset when inter-lift rest timer is off. */
private const val SUPERSET_INTER_LIFT_SECONDS = 30

private const val SECONDS_PER_MINUTE = 60

/**
 * Estimates wall-clock workout duration from prescribed sets, reps, and rest.
 *
 * Model:
 * - Active work: setup overhead + estimated reps × 5 seconds per set.
 * - Estimated reps: midpoint of the set's rep band; Double `0/0` inherits the first positive
 *   band on the lift; else default 10.
 * - Solo rest: configured rest between sets as stored (including 0); no rest after the final set.
 * - Superset chains: between non-terminal lifts in a round, use that lift's rest when
 *   [WorkoutLiftDurationInput.useRestTimerBetweenLifts] is true, otherwise 30s; terminal lift
 *   rest only between rounds; no rest after the final set of the block.
 *
 * Does not drive live rest-timer UX — that path reads [WorkoutLift.useRestTimerBetweenLifts]
 * independently during logging.
 */
class EstimateWorkoutDurationMinutesUseCase @Inject constructor() {
    operator fun invoke(workoutLifts: List<WorkoutLiftDurationInput>): Int {
        val totalSeconds = estimateWorkoutSeconds(workoutLifts)
        return ceil(totalSeconds.toDouble() / SECONDS_PER_MINUTE).toInt().coerceAtLeast(0)
    }
}

internal fun estimateWorkoutSeconds(workoutLifts: List<WorkoutLiftDurationInput>): Int {
    if (workoutLifts.isEmpty()) {
        return 0
    }
    val processedGroups = mutableSetOf<String>()
    var total = 0
    workoutLifts.sortedBy { lift -> lift.position }.forEach { lift ->
        val groupId = lift.supersetGroupId
        when {
            groupId == null -> total += estimateLiftSeconds(lift)
            groupId in processedGroups -> Unit
            else -> {
                processedGroups += groupId
                val block =
                    workoutLifts
                        .filter { member -> member.supersetGroupId == groupId }
                        .sortedBy { member -> member.position }
                total += estimateBlockSeconds(block)
            }
        }
    }
    return total
}

private fun estimateBlockSeconds(block: List<WorkoutLiftDurationInput>): Int {
    if (block.size == 1) {
        return estimateLiftSeconds(block.single())
    }
    val rounds = block.maxOf { lift -> lift.sets.size }.coerceAtLeast(0)
    if (rounds == 0) {
        return 0
    }
    var total = 0
    repeat(rounds) { roundIndex ->
        val participants = block.filter { lift -> lift.sets.getOrNull(roundIndex) != null }
        if (participants.isEmpty()) {
            return@repeat
        }
        val isLastRound = roundIndex == rounds - 1
        participants.forEachIndexed { participantIndex, lift ->
            total += activeSecondsForSet(lift, roundIndex)
            val isLastInRound = participantIndex == participants.lastIndex
            when {
                !isLastInRound -> total += interLiftSeconds(lift, roundIndex)
                !isLastRound -> {
                    val rest = lift.sets[roundIndex].restSeconds
                    total += rest.coerceAtLeast(0)
                }
            }
        }
    }
    return total
}

internal fun estimateLiftSeconds(lift: WorkoutLiftDurationInput): Int {
    if (lift.sets.isEmpty()) return 0
    var total = 0
    lift.sets.forEachIndexed { index, set ->
        total += activeSecondsForSet(lift, index)
        if (index < lift.sets.lastIndex) {
            total += set.restSeconds.coerceAtLeast(0)
        }
    }
    return total
}

/** Resolved working-rep estimate for one set index (inheritance + default applied). */
internal fun estimatedRepsForSet(lift: WorkoutLiftDurationInput, setIndex: Int): Int {
    val set = lift.sets.getOrNull(setIndex) ?: return DEFAULT_REPS_PER_SET
    midpointRepsOrNull(set.repMin, set.repMax)?.let { return it }
    val inherited =
        lift.sets.firstNotNullOfOrNull { candidate ->
            midpointRepsOrNull(candidate.repMin, candidate.repMax)
        }
    return inherited ?: DEFAULT_REPS_PER_SET
}

internal fun midpointRepsOrNull(repMin: Int, repMax: Int): Int? {
    if (repMin <= 0 && repMax <= 0) return null
    val min = if (repMin > 0) repMin else repMax
    val max = if (repMax > 0) repMax else repMin
    return ((min + max) / 2).coerceAtLeast(1)
}

private fun activeSecondsForSet(lift: WorkoutLiftDurationInput, setIndex: Int): Int {
    val reps = estimatedRepsForSet(lift, setIndex)
    return SETUP_SECONDS_PER_SET + reps * SECONDS_PER_REP
}

private fun interLiftSeconds(lift: WorkoutLiftDurationInput, setIndex: Int): Int {
    if (!lift.useRestTimerBetweenLifts) {
        return SUPERSET_INTER_LIFT_SECONDS
    }
    val rest = lift.sets.getOrNull(setIndex)?.restSeconds ?: return SUPERSET_INTER_LIFT_SECONDS
    return rest.coerceAtLeast(0)
}
