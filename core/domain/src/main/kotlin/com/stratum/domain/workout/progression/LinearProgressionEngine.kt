package com.stratum.domain.workout.progression

import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.training.EstimatedOneRm
import com.stratum.domain.workout.BACKOFF_REP_FLOOR
import com.stratum.domain.workout.SetSlot
import com.stratum.domain.workout.SlotHistoryEntry
import com.stratum.domain.workout.SlotRecommendation
import com.stratum.domain.workout.fatigue.projectReps
import javax.inject.Inject

/**
 * Greyskull-style hybrid linear-progression engine (D8). Per-session increment GATED
 * by RIR achievement on the top set: increments when BOTTOM of rep range met at RIR
 * (load-first); Double (Slice 2) increments when TOP met at RIR (reps-first).
 * Tracks per-lift stall count + reset count across history. 2 consecutive stalls →
 * 10% reset; 2 resets → auto-transition to Double (D8 locked).
 *
 * Pure — derives all state from the supplied history.
 */
class LinearProgressionEngine @Inject constructor(
    private val config: LinearProgressionConfig,
) : ProgressionEngine {
    override val scheme: ProgressionScheme = ProgressionScheme.Linear

    /**
     * Forward-looking next-session load for [request.slot]. Increments when the most
     * recent session met the BOTTOM of the rep range at the RIR target; holds otherwise.
     * Backoff slots inherit the latest weight (no increment).
     */
    override fun recommend(request: SlotProgressionRequest): SlotRecommendation {
        val slot = request.slot
        val latest = request.history.firstOrNull()
        val prevWeight = latest?.loggedSet?.weight
        val prevReps = latest?.loggedSet?.reps

        // Bodyweight: no load lever — always recommend 0 weight, rep-only progression.
        if (request.loadType == LoadType.Bodyweight) {
            val zeroWeight = Weight(0.0, prevWeight?.unit ?: Weight.WeightUnit.Lb)
            val baselineReps = latest?.loggedSet?.reps ?: slot.repMin
            return SlotRecommendation(
                slotId = slot.slotId,
                recommendedWeight = zeroWeight,
                baselineExpectedReps = baselineReps,
                previousWeight = prevWeight,
                previousReps = prevReps,
            )
        }

        val baseWeight = latest?.loggedSet?.weight
            ?: request.fallbackOneRm
            ?: defaultWeight()

        val targetWeight = request.currentTopSetWeight ?: run {
            if (!slot.isTopSet) {
                baseWeight
            } else {
                val increment = if (metBottomAtRir(latest, slot)) incrementFor(slot) else zero(baseWeight)
                Weight(baseWeight.amount + increment.amount, baseWeight.unit)
                    .capAssistedAtZero(request.loadType)
            }
        }

        val baselineReps = if (prevWeight != null && prevWeight == targetWeight) {
            if (slot.isTopSet) {
                val increment = if (metBottomAtRir(latest, slot)) incrementFor(slot) else zero(baseWeight)
                if (increment.amount > 0.0) {
                    slot.repMin
                } else {
                    val prevSet = latest.loggedSet
                    rirAdjustedReps(prevSet.reps, prevSet.rir, slot) ?: slot.repMin
                }
            } else {
                val prevSet = latest.loggedSet
                rirAdjustedReps(
                    prevSet.reps,
                    prevSet.rir,
                    slot,
                ) ?: prevSet.reps
            }
        } else {
            val priorRec = request.priorSlotRecommendation
            val priorSlot = request.priorSlot
            if (priorRec != null && priorSlot != null) {
                val priorWeight = priorRec.recommendedWeight
                val priorReps = priorRec.baselineExpectedReps
                val priorRirGoal = priorSlot.rirGoal
                val priorE1rm = EstimatedOneRm.calculate(priorWeight.amount, priorReps + priorRirGoal)
                val rawCapacityReps = projectReps(
                    baselineOneRm = priorE1rm,
                    targetWeight = targetWeight,
                    nextSetIndex = slot.position + 1,
                    perLiftSlope = request.perLiftSlope,
                    perLiftIntercept = request.perLiftIntercept,
                )
                val repsAtTargetRir = rawCapacityReps - slot.rirGoal
                if (slot.isTopSet) {
                    repsAtTargetRir.coerceIn(slot.repMin, slot.repMax)
                } else {
                    repsAtTargetRir.coerceAtLeast(BACKOFF_REP_FLOOR)
                }
            } else {
                if (slot.isTopSet) {
                    slot.repMin
                } else {
                    val prevSet = latest?.loggedSet
                    rirAdjustedReps(
                        prevSet?.reps ?: slot.repMin,
                        prevSet?.rir,
                        slot,
                    ) ?: (prevSet?.reps ?: slot.repMin)
                }
            }
        }

        val isProjected = latest != null || (request.priorSlotRecommendation?.isProjected == true)

        return SlotRecommendation(
            slotId = slot.slotId,
            recommendedWeight = targetWeight,
            baselineExpectedReps = baselineReps,
            previousWeight = prevWeight,
            previousReps = prevReps,
            isProjected = isProjected,
        )
    }

    /**
     * Backward-looking per-lift stall/reset/transition state derived from [history]
     * (D8). Walks sessions oldest→newest (stable sort on [SlotHistoryEntry.session.startedAt]),
     * counting consecutive stalls; each time the stall threshold is reached a reset
     * cycle fires ([currentResetCount]++ and the streak clears); the next session
     * consumes the pending reset. After [LinearProgressionConfig.resetsBeforeTransition]
     * cycles the engine surfaces a [SchemeTransitionEvent] (Linear → Double,
     * ResetCyclesExhausted) on [LinearProgressionState.emittedEvent].
     *
     * Stall classification honours [LinearProgressionConfig.stallStrictness]:
     * - [StallStrictness.RirBased] — a stall is "RIR met but bottom missed"; a set that
     *   met the bottom but missed RIR is a hold (does not credit a stall, does not break
     *   the streak).
     * - [StallStrictness.AbsoluteFailure] — a stall is "could not exceed the bottom"
     *   (reps ≤ rep floor), so an exactly-at-floor ground-out set still counts.
     *
     * The rep floor and RIR goal are not recoverable from a logged set alone; the
     * Greyskull LP defaults (floor 5, RIR 2) are used. Production snapshot wiring that
     * needs per-lift floors routes through [recommend], which binds the slot template.
     */
    fun diagnose(
        liftId: LiftId,
        history: List<SlotHistoryEntry>,
    ): LinearProgressionState {
        val ordered = history.sortedBy { it.session.startedAt }
        var stallCount = 0
        var resetCount = 0
        var resetPending = false

        for (entry in ordered) {
            // A session following a fired reset consumes the pending reset (the lifter
            // trained at the reduced weight).
            if (resetPending) resetPending = false

            when (classify(entry, config.stallStrictness)) {
                Class.Stall -> {
                    stallCount++
                    if (stallCount >= config.stallThresholdSessions) {
                        resetCount++
                        stallCount = 0
                        resetPending = true
                    }
                }
                Class.Success -> stallCount = 0
                Class.Hold -> Unit // neither credits nor breaks the stall streak
            }
        }

        val shouldTransition = resetCount >= config.resetsBeforeTransition
        val emitted = if (shouldTransition) {
            // diagnose recovers no workoutLiftId from history (SlotHistoryEntry carries
            // none); production snapshot wiring that needs the bound id routes the
            // emitted event through the caller. The auto-transition sentinel is accepted
            // because callers consume `from`/`to`/`reason` (asserted in S4).
            SchemeTransitionEvent(
                workoutLiftId = WorkoutLiftId(AUTO_TRANSITION_SENTINEL),
                liftId = liftId,
                from = ProgressionScheme.Linear,
                to = ProgressionScheme.Double,
                reason = SchemeTransitionReason.ResetCyclesExhausted,
            )
        } else {
            null
        }
        return LinearProgressionState(
            liftId = liftId,
            currentStallCount = stallCount,
            currentResetCount = resetCount,
            shouldReset = resetPending,
            shouldTransitionToDouble = shouldTransition,
            emittedEvent = emitted,
        )
    }

    private enum class Class { Stall, Success, Hold }

    private fun classify(entry: SlotHistoryEntry, strictness: StallStrictness): Class {
        val reps = entry.loggedSet.reps
        val rir = entry.loggedSet.rir
        val rirMet = rir != null && rir >= DEFAULT_RIR_GOAL
        val bottomMet = reps >= DEFAULT_REP_FLOOR
        return when (strictness) {
            StallStrictness.RirBased -> when {
                // RIR met but bottom missed → true failure.
                !bottomMet && rirMet -> Class.Stall
                // Bottom met at RIR → success (breaks stall streak).
                bottomMet && rirMet -> Class.Success
                // Otherwise (bottom met but RIR not met, or both missed) → hold: the
                // lifter had more to give, so do not credit a stall.
                else -> Class.Hold
            }
            StallStrictness.AbsoluteFailure -> when {
                // Could not exceed the bottom (reps ≤ floor) → stall, regardless of RIR.
                reps <= DEFAULT_REP_FLOOR -> Class.Stall
                // Exceeded the bottom at RIR → success.
                rirMet -> Class.Success
                else -> Class.Hold
            }
        }
    }

    private fun metBottomAtRir(latest: SlotHistoryEntry?, slot: SetSlot): Boolean {
        val set = latest?.loggedSet ?: return false
        return set.reps >= slot.repMin && (set.rir != null && set.rir >= slot.rirGoal)
    }

    private fun incrementFor(slot: SetSlot): Weight =
        config.perLiftIncrementOverride[slot.liftId] ?: config.lowerBodyIncrement

    private fun defaultWeight(): Weight = Weight(0.0, Weight.WeightUnit.Lb)

    private fun zero(weight: Weight): Weight = Weight(0.0, weight.unit)

    private companion object {
        const val DEFAULT_REP_FLOOR = 5
        const val DEFAULT_RIR_GOAL = 2
        const val AUTO_TRANSITION_SENTINEL = "auto-transition"
    }
}
