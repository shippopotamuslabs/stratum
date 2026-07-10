package com.stratum.domain.workout.progression

import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.incrementFor
import com.stratum.domain.lift.roundToIncrement
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
 * Linear-progression engine for true beginners. Per-session increment is GATED by RIR
 * achievement on the top set: increments when BOTTOM of rep range met at RIR (load-first);
 * Double increments when TOP met at RIR (reps-first).
 *
 * **Exit criterion (not Greyskull reset-churn):** [LinearProgressionConfig.stallThresholdSessions]
 * consecutive stalls → auto-transition to Double. Classic LP literature (GSLP/SS) prescribed
 * repeated 10% deloads and rebuilds; modern coaching treats repeated failure of session-to-session
 * load adds as a signal that the progression *model* should change. Stratum flips the lift to
 * Double rather than looping deload → rebuild → stall.
 *
 * Pure — derives all state from the supplied history. Math baseline comes from
 * [SlotProgressionRequest.progressionBaseline] (capacity-ordered recent block built by
 * the orchestrator), so recommendations are monotonically non-increasing across slots
 * even when prior-set RIR was misreported. `history` is used only for
 * `previousWeight`/`previousReps` (UI display).
 */
class LinearProgressionEngine @Inject constructor(
    private val config: LinearProgressionConfig,
) : ProgressionEngine {
    override val scheme: ProgressionScheme = ProgressionScheme.Linear

    /**
     * Forward-looking next-session load for [request.slot].
     *
     * - **Increment** when the most recent session met the BOTTOM of the rep range at the
     *   RIR target (load-first).
     * - **Hold** otherwise (reps lever may still advance via [rirAdjustedReps]).
     * - Backoff slots inherit [SlotProgressionRequest.currentTopSetWeight] when provided
     *   (no independent increment).
     *
     * Scheme transition (Linear → Double) is owned by [diagnose] + the snapshot
     * orchestrator — not by load reduction inside this method.
     */
    override fun recommend(request: SlotProgressionRequest): SlotRecommendation {
        val slot = request.slot
        val latest = request.history.firstOrNull()
        val prevWeight = latest?.loggedSet?.weight
        val prevReps = latest?.loggedSet?.reps
        val baselineEntry = baselineEntryFor(request.progressionBaseline)

        // Bodyweight: no load lever — always recommend 0 weight, rep-only progression.
        if (request.loadType == LoadType.Bodyweight) {
            val zeroWeight = Weight(0.0, prevWeight?.unit ?: Weight.WeightUnit.Lb)
            val baselineReps = baselineEntry?.loggedSet?.reps ?: slot.repMin
            return SlotRecommendation(
                slotId = slot.slotId,
                recommendedWeight = zeroWeight,
                baselineExpectedReps = baselineReps,
                previousWeight = prevWeight,
                previousReps = prevReps,
            )
        }

        val baseWeight = baselineEntry?.loggedSet?.weight
            ?: request.fallbackOneRm
            ?: defaultWeight()

        val targetWeight = request.currentTopSetWeight ?: run {
            if (!slot.isTopSet) {
                baseWeight
            } else {
                val raw =
                    if (metBottomAtRir(baselineEntry, slot)) {
                        val increment = incrementFor(request, slot, baseWeight)
                        Weight(baseWeight.amount + increment.amount, baseWeight.unit)
                    } else {
                        baseWeight
                    }
                request.incrementTiers
                    .roundToIncrement(raw, request.loadType)
                    .capAssistedAtZero(request.loadType)
            }
        }

        val baselineReps = when {
            // No recent baseline for this slot — project from the prior slot rec via
            // the fatigue model (e.g. slots beyond the most-recent session's set count).
            baselineEntry == null && request.progressionBaseline is ProgressionBaseline.ProjectFromPrior ->
                projectFromPrior(request, slot, targetWeight)
                    ?: coldStartReps(slot, baselineEntry)

            baselineEntry != null && baselineEntry.loggedSet.weight == targetWeight -> {
                if (slot.isTopSet) {
                    if (metBottomAtRir(baselineEntry, slot)) {
                        slot.repMin
                    } else {
                        val prevSet = baselineEntry.loggedSet
                        rirAdjustedReps(prevSet.reps, prevSet.rir, slot) ?: slot.repMin
                    }
                } else {
                    val prevSet = baselineEntry.loggedSet
                    rirAdjustedReps(
                        prevSet.reps,
                        prevSet.rir,
                        slot,
                    ) ?: prevSet.reps
                }
            }

            else -> {
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
                    coldStartReps(slot, baselineEntry)
                }
            }
        }

        val isProjected = baselineEntry != null || (request.priorSlotRecommendation?.isProjected == true)

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
     * Backward-looking per-lift stall/transition state derived from [history].
     * Walks sessions oldest→newest (stable sort on [SlotHistoryEntry.session.startedAt]
     * then session id), counting consecutive stalls. When the streak reaches
     * [LinearProgressionConfig.stallThresholdSessions], surfaces a
     * [SchemeTransitionEvent] (Linear → Double, [SchemeTransitionReason.ConsecutiveStalls]).
     *
     * Stall classification honours [LinearProgressionConfig.stallStrictness]:
     * - [StallStrictness.RirBased] — a stall is "RIR met but bottom missed"; a set that
     *   met the bottom but missed RIR is a hold (does not credit a stall, does not break
     *   the streak).
     * - [StallStrictness.AbsoluteFailure] — a stall is "could not exceed the bottom"
     *   (reps ≤ rep floor), so an exactly-at-floor ground-out set still counts.
     *
     * The rep floor and RIR goal are not recoverable from a logged set alone; the
     * Greyskull LP defaults (floor 5, RIR 2) are used for diagnose. Production
     * [recommend] binds the slot template for load decisions.
     */
    fun diagnose(
        liftId: LiftId,
        history: List<SlotHistoryEntry>,
    ): LinearProgressionState {
        // Oldest → newest. Secondary key keeps deterministic order when imports share a
        // timestamp (common in bulk-imported history).
        val ordered = history.sortedWith(
            compareBy(
                { it.session.startedAt },
                { it.session.id.value },
            ),
        )
        var stallCount = 0
        var shouldTransition = false

        for (entry in ordered) {
            when (classify(entry, config.stallStrictness)) {
                Class.Stall -> {
                    stallCount++
                    if (stallCount >= config.stallThresholdSessions) {
                        shouldTransition = true
                        // Keep counting so currentStallCount reflects the live streak;
                        // transition is sticky once reached for this history walk.
                    }
                }
                Class.Success -> stallCount = 0
                Class.Hold -> Unit // neither credits nor breaks the stall streak
            }
        }

        val emitted = if (shouldTransition) {
            // diagnose recovers no workoutLiftId from history (SlotHistoryEntry carries
            // none); production snapshot wiring that needs the bound id routes the
            // emitted event through the caller. The auto-transition sentinel is accepted
            // because callers consume `from`/`to`/`reason`.
            SchemeTransitionEvent(
                workoutLiftId = WorkoutLiftId(AUTO_TRANSITION_SENTINEL),
                liftId = liftId,
                from = ProgressionScheme.Linear,
                to = ProgressionScheme.Double,
                reason = SchemeTransitionReason.ConsecutiveStalls,
            )
        } else {
            null
        }
        return LinearProgressionState(
            liftId = liftId,
            currentStallCount = stallCount,
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

    private fun projectFromPrior(
        request: SlotProgressionRequest,
        slot: SetSlot,
        targetWeight: Weight,
    ): Int? {
        val priorRec = request.priorSlotRecommendation ?: return null
        val priorSlot = request.priorSlot ?: return null
        val priorE1rm = EstimatedOneRm.calculate(
            priorRec.recommendedWeight.amount,
            priorRec.baselineExpectedReps + priorSlot.rirGoal,
        )
        val rawCapacityReps = projectReps(
            baselineOneRm = priorE1rm,
            targetWeight = targetWeight,
            nextSetIndex = slot.position + 1,
            perLiftSlope = request.perLiftSlope,
            perLiftIntercept = request.perLiftIntercept,
        )
        val repsAtTargetRir = rawCapacityReps - slot.rirGoal
        return if (slot.isTopSet) {
            repsAtTargetRir.coerceIn(slot.repMin, slot.repMax)
        } else {
            repsAtTargetRir.coerceAtLeast(BACKOFF_REP_FLOOR)
        }
    }

    private fun coldStartReps(slot: SetSlot, baselineEntry: SlotHistoryEntry?): Int =
        if (slot.isTopSet) {
            slot.repMin
        } else {
            baselineEntry?.loggedSet?.reps ?: slot.repMin
        }

    private fun baselineEntryFor(baseline: ProgressionBaseline?): SlotHistoryEntry? =
        when (baseline) {
            is ProgressionBaseline.FromRecent -> baseline.entry
            ProgressionBaseline.ProjectFromPrior, null -> null
        }

    private fun incrementFor(
        request: SlotProgressionRequest,
        slot: SetSlot,
        baseWeight: Weight,
    ): Weight {
        val fromTiers = request.incrementTiers.incrementFor(baseWeight)
        if (fromTiers != null) return fromTiers
        return config.perLiftIncrementOverride[slot.liftId] ?: config.lowerBodyIncrement
    }

    private fun defaultWeight(): Weight = Weight(0.0, Weight.WeightUnit.Lb)

    private companion object {
        const val DEFAULT_REP_FLOOR = 5
        const val DEFAULT_RIR_GOAL = 2
        const val AUTO_TRANSITION_SENTINEL = "auto-transition"
    }
}
