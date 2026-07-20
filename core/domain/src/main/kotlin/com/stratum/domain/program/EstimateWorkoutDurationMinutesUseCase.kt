package com.stratum.domain.program

import javax.inject.Inject
import kotlin.math.ceil

/** Active work per rep (concentric/eccentric + brief pauses). */
private const val SECONDS_PER_REP = 5

/** Mid-band of default prescription range (8–12). */
private const val DEFAULT_REPS_PER_SET = 10

/** Fallback rest when configured rest is missing or non-positive. */
private const val DEFAULT_REST_SECONDS = DEFAULT_LIFT_SET_REST_SECONDS

/** Transition time between lifts within a superset round. */
private const val SUPERSET_INTER_LIFT_SECONDS = 30

private const val SECONDS_PER_MINUTE = 60

/**
 * Estimates wall-clock workout duration from set counts, prescribed reps, and rest.
 *
 * Model:
 * - Active work: estimated reps × 5 seconds per set.
 * - Solo rest: configured rest between sets (default 120s when non-positive); no rest after the final set.
 * - Superset chains: 30s between lifts in a round; terminal lift rest only between rounds;
 *   no rest after the final set of the block.
 *
 * [WorkoutLiftDurationInput.useRestTimerBetweenLifts] is retained for API stability / live
 * session UX; estimation always uses the 30s inter-lift model so supersets stay shorter.
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
    val rounds = block.maxOf { lift -> lift.restSecondsBySet.size }.coerceAtLeast(0)
    if (rounds == 0) {
        return 0
    }
    var total = 0
    repeat(rounds) { roundIndex ->
        val participants =
            block.mapNotNull { lift ->
                if (lift.restSecondsBySet.getOrNull(roundIndex) == null) {
                    null
                } else {
                    lift
                }
            }
        if (participants.isEmpty()) {
            return@repeat
        }
        val isLastRound = roundIndex == rounds - 1
        participants.forEachIndexed { participantIndex, lift ->
            total += activeSecondsForSet(lift, roundIndex)
            val isLastInRound = participantIndex == participants.lastIndex
            when {
                !isLastInRound -> total += SUPERSET_INTER_LIFT_SECONDS
                !isLastRound -> {
                    val rest = lift.restSecondsBySet[roundIndex]
                    total += effectiveRestSeconds(rest)
                }
            }
        }
    }
    return total
}

internal fun estimateLiftSeconds(lift: WorkoutLiftDurationInput): Int {
    if (lift.restSecondsBySet.isEmpty()) return 0
    var total = 0
    lift.restSecondsBySet.forEachIndexed { index, rest ->
        total += activeSecondsForSet(lift, index)
        if (index < lift.restSecondsBySet.lastIndex) {
            total += effectiveRestSeconds(rest)
        }
    }
    return total
}

private fun activeSecondsForSet(lift: WorkoutLiftDurationInput, setIndex: Int): Int {
    val reps = lift.repsBySet.getOrNull(setIndex)?.takeIf { it > 0 } ?: DEFAULT_REPS_PER_SET
    return reps * SECONDS_PER_REP
}

private fun effectiveRestSeconds(rest: Int): Int =
    if (rest > 0) rest else DEFAULT_REST_SECONDS
