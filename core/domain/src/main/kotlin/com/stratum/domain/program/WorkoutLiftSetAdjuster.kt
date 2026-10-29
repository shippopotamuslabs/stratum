package com.stratum.domain.program

import com.stratum.domain.program.identity.LiftSetId
import java.util.UUID
import com.stratum.domain.program.identity.WorkoutLiftId

/**
 * Pure set-list mutations for a [WorkoutLift] template. Used by single-lift and batch check-in
 * apply paths so absolute targets and deltas share one implementation.
 */
object WorkoutLiftSetAdjuster {

    fun withSetCount(lift: WorkoutLift, targetSetCount: Int): WorkoutLift {
        val target = targetSetCount.coerceAtLeast(1)
        val current = lift.sets.sortedBy { it.position }
        if (current.size == target) {
            return lift.copy(sets = current.mapIndexed { index, set -> set.copy(position = index) })
        }
        val delta = target - current.size
        return lift.copy(sets = adjustByDelta(lift.id, current, delta))
    }

    fun withDelta(lift: WorkoutLift, delta: Int): WorkoutLift {
        if (delta == 0) return lift
        val current = lift.sets.sortedBy { it.position }
        return lift.copy(sets = adjustByDelta(lift.id, current, delta))
    }

    private fun adjustByDelta(
        workoutLiftId: com.stratum.domain.program.identity.WorkoutLiftId,
        current: List<LiftSet>,
        delta: Int,
    ): List<LiftSet> =
        when {
            delta > 0 -> {
                var sets = current
                repeat(delta) {
                    val template = sets.lastOrNull() ?: return@repeat
                    sets = sets +
                        template.copy(
                            id = LiftSetId(UUID.randomUUID().toString()),
                            workoutLiftId = workoutLiftId,
                            position = sets.size,
                            isTopSet = false,
                        )
                }
                sets.mapIndexed { index, set -> set.copy(position = index) }
            }
            else -> {
                val mutable = current.toMutableList()
                var toRemove = (-delta).coerceAtMost((mutable.size - 1).coerceAtLeast(0))
                while (toRemove > 0 && mutable.size > 1) {
                    val dropIndex =
                        mutable.indices.lastOrNull { !mutable[it].isTopSet }
                            ?: mutable.lastIndex
                    mutable.removeAt(dropIndex)
                    toRemove--
                }
                mutable.mapIndexed { index, set -> set.copy(position = index) }
            }
        }
}
