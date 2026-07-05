package com.stratum.domain.program

import javax.inject.Inject
import kotlin.math.ceil

/** Active work per set before rest (lifting, setup, logging). */
private const val ACTIVE_SECONDS_PER_SET = 60

private const val SECONDS_PER_MINUTE = 60

/**
 * Estimates wall-clock workout duration from set count and configured rest periods.
 * Superset chains skip inter-lift rest unless [WorkoutLiftDurationInput.useRestTimerBetweenLifts]
 * is true on the non-terminal lift; terminal lifts always count set rest between rounds.
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
    val rounds = block.maxOf { lift -> lift.restSecondsBySet.size }.coerceAtLeast(1)
    var total = 0
    repeat(rounds) { roundIndex ->
        block.forEachIndexed { liftIndex, lift ->
            val restForSet = lift.restSecondsBySet.getOrNull(roundIndex) ?: return@forEachIndexed
            total += ACTIVE_SECONDS_PER_SET
            val isLastLiftInChain = liftIndex == block.lastIndex
            val includeRest =
                if (isLastLiftInChain) {
                    true
                } else {
                    lift.useRestTimerBetweenLifts
                }
            if (includeRest) {
                total += restForSet.coerceAtLeast(0)
            }
        }
    }
    return total
}

internal fun estimateLiftSeconds(lift: WorkoutLiftDurationInput): Int {
    if (lift.restSecondsBySet.isEmpty()) return 0
    var total = 0
    lift.restSecondsBySet.forEachIndexed { index, rest ->
        total += ACTIVE_SECONDS_PER_SET
        if (index < lift.restSecondsBySet.lastIndex) {
            total += rest.coerceAtLeast(0)
        }
    }
    return total
}
