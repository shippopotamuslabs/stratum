package com.stratum.domain.workout.progression

import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.incrementFor
import com.stratum.domain.program.ProgressionScheme
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.workout.SetSlot
import com.stratum.domain.workout.SlotHistoryEntry
import com.stratum.domain.workout.SlotRecommendation
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
 * Load selection + stall diagnose live here; shared recommend orchestration
 * ([recommendShared]) owns floor drops, fatigue projection, inherit guards, and cold-start
 * rep floors. Pure — derives all state from the supplied history. Math baseline comes from
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
     * - **Hold** otherwise (reps lever may still advance; floor-driven weight drop shared).
     * - Backoff slots inherit [SlotProgressionRequest.currentTopSetWeight] when provided
     *   (no independent increment).
     *
     * Scheme transition (Linear → Double) is owned by [diagnose] + the snapshot
     * orchestrator — not by load reduction inside this method.
     */
    override fun recommend(request: SlotProgressionRequest): SlotRecommendation =
        recommendShared(
            request = request,
            policy = ProgressionLoadPolicy(
                shouldAdvanceLoad = ::metBottomAtRir,
                incrementFor = { req, base -> incrementFor(req, req.slot, base) },
                heldTopRepsWithoutRir = { _, slot -> minRecommendedReps(slot) },
            ),
        )

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

    private fun incrementFor(
        request: SlotProgressionRequest,
        slot: SetSlot,
        baseWeight: Weight,
    ): Weight {
        val fromTiers = request.incrementTiers.incrementFor(baseWeight)
        if (fromTiers != null) return fromTiers
        return config.perLiftIncrementOverride[slot.liftId] ?: config.lowerBodyIncrement
    }

    private companion object {
        const val DEFAULT_REP_FLOOR = 5
        const val DEFAULT_RIR_GOAL = 2
        const val AUTO_TRANSITION_SENTINEL = "auto-transition"
    }
}
