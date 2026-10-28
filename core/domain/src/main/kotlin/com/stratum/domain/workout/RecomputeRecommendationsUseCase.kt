package com.stratum.domain.workout

import com.stratum.domain.importing.FatigueDecay
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.WeightConversion
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.incrementFor
import com.stratum.domain.lift.roundToIncrement
import com.stratum.domain.program.SetType
import com.stratum.domain.training.EstimatedOneRm
import com.stratum.domain.workout.dropset.DropSetRepProjection
import com.stratum.domain.workout.dropset.EstimateDropSetWeightUseCase
import com.stratum.domain.workout.fatigue.FatigueModel
import com.stratum.domain.workout.myorep.MyoMatchStructure
import com.stratum.domain.workout.myorep.MyoMiniRepProjection
import com.stratum.domain.workout.myorep.MyoRepKind
import com.stratum.domain.workout.myorep.MyoRepLifecycle
import javax.inject.Inject

/**
 * Overlay-only recommendation pipeline (D4). Computes the live
 * [LiveSetRecommendation] map for upcoming unlogged sets from the most recent
 * performed set per lift. Never writes back into the snapshot.
 *
 * **Straight sets** share one weight for the remaining block (existing Hold/Reduce).
 * Performance is anchored on the latest logged **straight-working** set (Standard or
 * Myo activation) — never a myo mini or drop, whose short-rest / reduced-load logs
 * understate e1RM. Completed minis/drops after that anchor count as fatigue sets
 * so the engine still sees cluster volume. Open minis/drops between the performance
 * anchor and the last standard share one ordinal geometry ([fatigueSetIndex] /
 * [remainingCountAfterPerformanceAnchor]) for floor + reps. Post-cluster uses the
 * same [RecommendationEngine.recommendNextSet] with `keepSnapshotWhenOnTrack = false`
 * so last-set floor reduce always runs (no second weight path).
 *
 * **Rep-match exception (STR-232):** when the just-logged set is a mini of a
 * rep-match cluster, the anchor is the match activation (the top set) and the
 * completed minis consume no fatigue steps — the backoff and its cluster count as
 * the one normal set they replace, and the next match working set (which takes
 * full normal rest) gets the normal FatigueDecay gradient from the top set. No
 * mini-cluster fatigue coefficient is invented. RepFloor blocks are unchanged:
 * their minis parent to the activation itself, so the anchor already is the top
 * set, and following standards keep counting cluster volume.
 *
 * **On-track (exact prescription):** when the engine returns `null` (matched load)
 * **and** the log matches the shown reps/RIR, the shared **weight** stays put and
 * fatigue uses **stable block geometry** (capacity + set indices from the first
 * straight set — same story as cold open). Downstream rep targets do not jump just
 * because an earlier set was completed exactly as prescribed.
 *
 * **Off-track (different from prescription):** recompute from the just-performed set
 * as capacity anchor with indices re-anchored (last performed ≈ set 1 of the remaining
 * story) so weak/strong/wrong-weight logs adapt the rest of the block.
 *
 * **Drop sets** use 20% off the immediate prior (performed, else prior live rec, else
 * snapshot prescription) and project reps with zero-rest [DropSetRepProjection] from
 * that prior — not normal-rest population fatigue. **Myo minis** copy activation load
 * (reduced when activation was way too heavy) and project reps with short-rest
 * [MyoMiniRepProjection] from the last logged set in the myo chain.
 *
 * **Post-anchor (straights + drops when something is logged):** unlogged straight/drop
 * sets before the last performed set do not receive live recs (user skipped ahead).
 * **Cold open (nothing logged yet):** unlogged drops still get 20%-off recs from the
 * prior slot's snapshot prescription — engines leave drops at shared straight-set load.
 * Straight sets after the first also get fatigue **rep** projections from the first
 * set's snapshot prescription (weight unchanged) so openers are not flat across sets.
 * **Myo minis exception:** unlogged auto-minis still get recs when their activation is
 * logged, even if a later cluster was completed first — multi-cluster out-of-order work
 * must not blank the earlier open mini.
 */
class RecomputeRecommendationsUseCase @Inject constructor(
    private val recommendationEngine: RecommendationEngine,
    private val fatigueModel: FatigueModel,
    private val estimateDropSetWeight: EstimateDropSetWeightUseCase = EstimateDropSetWeightUseCase(),
    private val straightSetPolicy: StraightSetRecommendationPolicy =
        StraightSetRecommendationPolicy(recommendationEngine, fatigueModel),
) {
    /**
     * @param priorRecommendations previous overlay live recs (from last recompute). **Required** —
     *   pass [emptyMap] only for cold open / first paint. Used to detect exact match to the
     *   **shown** prescription when a set is logged — merge for this call should still pass
     *   snapshot-only weights so Hold/Reduce does not ratchet on prior live loads.
     */
    operator fun invoke(
        rendered: RenderedWorkout,
        liftsById: Map<LiftId, Lift>,
        priorRecommendations: Map<SetSlotId, LiveSetRecommendation>,
        fatigueByLiftId: Map<LiftId, FatigueDecay> = emptyMap(),
    ): Map<SetSlotId, LiveSetRecommendation> =
        rendered.lifts
            .map { lift ->
                recommendationsForLift(lift, liftsById, fatigueByLiftId, priorRecommendations)
            }
            .fold(emptyMap()) { acc, part -> acc + part }

    private fun recommendationsForLift(
        lift: RenderedWorkoutLift,
        liftsById: Map<LiftId, Lift>,
        fatigueByLiftId: Map<LiftId, FatigueDecay>,
        priorRecommendations: Map<SetSlotId, LiveSetRecommendation>,
    ): Map<SetSlotId, LiveSetRecommendation> {
        val setsByPosition = lift.sets.sortedBy { it.position }
        val incrementTiers = liftsById[lift.liftId]?.incrementTiers ?: emptyList()
        val micro = liftsById[lift.liftId]?.microIncrements.orEmpty()
        val lastPerformedSet = lift.sets
            .filter { it.loggedValue != null }
            .maxByOrNull { it.position }
        val lastPerformed = lastPerformedSet?.loggedValue

        // Cold open: no logs yet — still prescribe drops from prior snapshot weights so
        // incomplete parents do not leave drops at the shared straight-set load (150→120).
        // Also project fatigue reps for straight sets after the first (weight stays snapshot).
        if (lastPerformedSet == null || lastPerformed == null) {
            val fatigue = fatigueByLiftId[lift.liftId] ?: FatigueDecay(null, null)
            val results = linkedMapOf<SetSlotId, LiveSetRecommendation>()
            when (lift.loadType) {
                LoadType.Weighted -> results += straightSetPolicy.coldOpenReps(setsByPosition, fatigue)
                LoadType.Bodyweight ->
                    results += straightSetPolicy.coldOpenBodyweightReps(setsByPosition, fatigue)
                LoadType.Assisted -> Unit // unchanged: no live straight-set recs (out of scope for STR-29)
            }
            val unloggedDrops = setsByPosition.filter {
                it.loggedValue == null && it.setType == SetType.DropSet
            }
            if (unloggedDrops.isNotEmpty()) {
                results += dropRecommendations(
                    drops = unloggedDrops,
                    setsByPosition = setsByPosition,
                    lastPerformed = null,
                    incrementTiers = incrementTiers,
                    loadType = lift.loadType,
                    priorResults = results,
                    micro = micro,
                )
            }
            return results
        }

        val anchorOrdinal = setsByPosition.indexOfFirst { it.slotId == lastPerformedSet.slotId }
        val postAnchorUpcoming = if (anchorOrdinal < 0) {
            emptyList()
        } else {
            setsByPosition.drop(anchorOrdinal + 1).filter { it.loggedValue == null }
        }
        // Open myo minis anywhere in the lift (not only post-anchor) — cluster order of work
        // can leave an earlier activation's mini still open after a later cluster is done.
        val unloggedMyoMinis = setsByPosition.filter { it.loggedValue == null && straightSetPolicy.isMyoMini(it) }
        if (postAnchorUpcoming.isEmpty() && unloggedMyoMinis.isEmpty()) return emptyMap()

        val fatigue = fatigueByLiftId[lift.liftId] ?: FatigueDecay(null, null)
        val results = linkedMapOf<SetSlotId, LiveSetRecommendation>()

        val straightUpcoming = postAnchorUpcoming.filter { straightSetPolicy.isStraightWorking(it) }
        if (straightUpcoming.isNotEmpty()) {
            when (lift.loadType) {
                LoadType.Weighted ->
                    results +=
                        straightSetPolicy.recommend(
                            lift = lift,
                            lastPerformedSet = lastPerformedSet,
                            lastPerformed = lastPerformed,
                            setsByPosition = setsByPosition,
                            anchorOrdinal = anchorOrdinal,
                            straightUpcoming = straightUpcoming,
                            incrementTiers = incrementTiers,
                            fatigue = fatigue,
                            priorRecommendations = priorRecommendations,
                            micro = micro,
                        )
                LoadType.Bodyweight ->
                    results +=
                        straightSetPolicy.recommendBodyweight(
                            lastPerformedSet = lastPerformedSet,
                            lastPerformed = lastPerformed,
                            setsByPosition = setsByPosition,
                            anchorOrdinal = anchorOrdinal,
                            straightUpcoming = straightUpcoming,
                            fatigue = fatigue,
                            priorRecommendations = priorRecommendations,
                        )
                LoadType.Assisted -> Unit // unchanged: no live straight-set recs (out of scope for STR-29)
            }
        }

        results += dropRecommendations(
            drops = postAnchorUpcoming.filter { it.setType == SetType.DropSet },
            setsByPosition = setsByPosition,
            lastPerformed = lastPerformed,
            incrementTiers = incrementTiers,
            loadType = lift.loadType,
            priorResults = results,
            micro = micro,
        )
        results += myoMiniRecommendations(
            unloggedMinis = unloggedMyoMinis,
            setsByPosition = setsByPosition,
            incrementTiers = incrementTiers,
            loadType = lift.loadType,
            micro = micro,
        )

        return results
    }

    /**
     * Drop load = 20% off immediate prior. Reps use [DropSetRepProjection] (zero-rest
     * residual on e1RM capacity at the new load). Processes drops in order so a chain of
     * unlogged drops re-anchors each step on the previous drop's projected rec, not only
     * the last performed parent set.
     */
    private fun dropRecommendations(
        drops: List<RenderedSet>,
        setsByPosition: List<RenderedSet>,
        lastPerformed: LoggedSetValue?,
        incrementTiers: List<IncrementTier>,
        loadType: LoadType,
        priorResults: Map<SetSlotId, LiveSetRecommendation>,
        micro: List<Weight> = emptyList(),
    ): Map<SetSlotId, LiveSetRecommendation> {
        val out = linkedMapOf<SetSlotId, LiveSetRecommendation>()
        // Merge live drop recs as we go so multi-drop chains resolve prior weight/reps.
        val resolved = priorResults.toMutableMap()
        for (set in drops) {
            val rec = recommendDropSet(
                set = set,
                setsByPosition = setsByPosition,
                lastPerformed = lastPerformed,
                incrementTiers = incrementTiers,
                loadType = loadType,
                resolved = resolved,
                micro = micro,
            ) ?: continue
            out[set.slotId] = rec
            resolved[set.slotId] = rec
        }
        return out
    }

    private fun recommendDropSet(
        set: RenderedSet,
        setsByPosition: List<RenderedSet>,
        lastPerformed: LoggedSetValue?,
        incrementTiers: List<IncrementTier>,
        loadType: LoadType,
        resolved: Map<SetSlotId, LiveSetRecommendation>,
        micro: List<Weight> = emptyList(),
    ): LiveSetRecommendation? {
        val prior = priorSet(setsByPosition, set) ?: return null
        val priorWeight = resolvePriorWeight(prior, resolved) ?: return null
        val priorRepsAndRir = resolvePriorRepsAndRir(
            prior = prior,
            lastPerformed = lastPerformed,
            resolved = resolved,
        ) ?: return null
        val (priorReps, priorRir) = priorRepsAndRir
        val dropWeight = estimateDropSetWeight(
            precedingWeight = priorWeight,
            incrementTiers = incrementTiers,
            loadType = loadType,
            micro = micro,
            capacityAtRir = { amountLb ->
                DropSetRepProjection.expectedRepsAtRir0(
                    priorWeight = priorWeight,
                    priorReps = priorReps,
                    priorRir = priorRir,
                    dropWeight = Weight(amountLb, Weight.WeightUnit.Lb),
                )
            },
        )
        val reps = DropSetRepProjection.expectedReps(
            priorWeight = priorWeight,
            priorReps = priorReps,
            priorRir = priorRir,
            dropWeight = dropWeight,
            rirGoal = set.rirGoal.coerceAtLeast(0),
        )
        return LiveSetRecommendation(weight = dropWeight, reps = reps)
    }

    /**
     * Immediate prior set's performed reps/RIR when logged; otherwise the **prescribed**
     * assumption for that prior (live rec or baseline) at [RenderedSet.rirGoal].
     *
     * Must not hardcode RIR 0 on an unlogged prior — live recs are already the working
     * reps at the slot's RIR goal. Using RIR 0 understates failure capacity and makes the
     * drop rec jump when the prior is logged exactly as prescribed (e.g. 7@1 → was treated
     * as 7@0 before log, 7@1 after).
     */
    private fun resolvePriorRepsAndRir(
        prior: RenderedSet,
        lastPerformed: LoggedSetValue?,
        resolved: Map<SetSlotId, LiveSetRecommendation>,
    ): Pair<Int, Int?>? {
        prior.loggedValue?.let { return it.reps to it.rir }
        val assumedRir = prior.rirGoal.coerceAtLeast(0)
        resolved[prior.slotId]?.reps?.takeIf { it > 0 }?.let { return it to assumedRir }
        // Baseline / display prescription on the prior row (no live rec yet).
        if (prior.recommendedReps > 0) {
            return prior.recommendedReps to assumedRir
        }
        lastPerformed?.let { return it.reps to it.rir }
        return null
    }

    /**
     * Myo mini load copies activation (with too-heavy reduce). Reps use short-rest
     * [MyoMiniRepProjection] from the last logged set in the myo chain — not normal-rest
     * [FatigueDecay] (that slope is multi-set rest, not ~15s minis).
     *
     * Considers **all** unlogged minis (not only post-global-anchor) so an earlier cluster's
     * open mini still gets a rec after a later cluster is finished.
     */
    private fun myoMiniRecommendations(
        unloggedMinis: List<RenderedSet>,
        setsByPosition: List<RenderedSet>,
        incrementTiers: List<IncrementTier>,
        loadType: LoadType,
        micro: List<Weight> = emptyList(),
    ): Map<SetSlotId, LiveSetRecommendation> =
        unloggedMinis
            .mapNotNull { set ->
                val clusterAnchor = findClusterAnchor(setsByPosition, set) ?: return@mapNotNull null
                // Weight seed: match uses true match activation (not the backoff); floor uses activation.
                val weightSource = resolveMyoWeightSource(setsByPosition, set, clusterAnchor)
                val weightLogged = weightSource.loggedValue ?: return@mapNotNull null
                var miniWeight = weightLogged.weight
                if (miniWeight.amount <= 0.0) return@mapNotNull null
                // Floor too-heavy only — match minis have no rep floor product rule.
                val isMatch =
                    set.myoRepKind == MyoRepKind.RepMatching ||
                        weightSource.myoRepKind == MyoRepKind.RepMatching ||
                        clusterAnchor.myoRepKind == MyoRepKind.RepMatching
                if (!isMatch && isActivationTooHeavy(weightLogged, weightSource)) {
                    miniWeight = reduceOneStep(miniWeight, incrementTiers, loadType, micro)
                }
                val priorInChain = lastLoggedMyoBefore(setsByPosition, set)
                val priorLogged = priorInChain?.loggedValue ?: weightLogged
                val isFirstMini =
                    priorInChain == null ||
                        priorInChain.slotId == clusterAnchor.slotId ||
                        priorInChain.slotId == weightSource.slotId
                val reps = MyoMiniRepProjection.expectedReps(
                    priorReps = priorLogged.reps,
                    priorRir = priorLogged.rir,
                    isFirstMini = isFirstMini,
                )
                set.slotId to LiveSetRecommendation(weight = miniWeight, reps = reps)
            }
            .toMap()

    /**
     * Most recent logged MyoRep set before [mini] in position order (prior mini, else
     * activation). Drives short-rest rep projection so each mini re-anchors on what was
     * just performed rather than always replaying activation capacity with 0 residual.
     */
    private fun lastLoggedMyoBefore(
        setsByPosition: List<RenderedSet>,
        mini: RenderedSet,
    ): RenderedSet? {
        val miniIdx = setsByPosition.indexOfFirst { it.slotId == mini.slotId }
        if (miniIdx <= 0) return null
        return setsByPosition
            .take(miniIdx)
            .lastOrNull { it.setType == SetType.MyoRep && it.loggedValue != null }
    }

    private fun resolvePriorWeight(
        prior: RenderedSet,
        results: Map<SetSlotId, LiveSetRecommendation>,
    ): Weight? {
        prior.loggedValue?.weight?.takeIf { it.amount > 0.0 }?.let { return it }
        results[prior.slotId]?.weight?.takeIf { it.amount > 0.0 }?.let { return it }
        return prior.recommendedWeight.takeIf { it.amount > 0.0 }
    }

    private fun priorSet(sets: List<RenderedSet>, set: RenderedSet): RenderedSet? {
        val idx = sets.indexOfFirst { it.slotId == set.slotId }
        if (idx <= 0) return null
        return sets[idx - 1]
    }

    /**
     * Owning cluster anchor for [mini] via [MyoMatchStructure] (floor activation or match backoff).
     */
    private fun findClusterAnchor(sets: List<RenderedSet>, mini: RenderedSet): RenderedSet? {
        val structureSlots = sets.map { it.toStructureSlot() }
        val miniSlot = mini.toStructureSlot()
        val anchor = MyoMatchStructure.resolveClusterAnchor(structureSlots, miniSlot) ?: return null
        return sets.firstOrNull { it.slotId == anchor.slotId }
    }

    /**
     * Load seed for a mini: match → [MyoMatchStructure.resolveMatchActivation]; floor → anchor.
     */
    private fun resolveMyoWeightSource(
        sets: List<RenderedSet>,
        mini: RenderedSet,
        clusterAnchor: RenderedSet,
    ): RenderedSet {
        val isMatch =
            mini.myoRepKind == MyoRepKind.RepMatching ||
                clusterAnchor.myoRepKind == MyoRepKind.RepMatching
        if (!isMatch) return clusterAnchor
        val structureSlots = sets.map { it.toStructureSlot() }
        val activation =
            MyoMatchStructure.resolveMatchActivation(structureSlots, mini.toStructureSlot())
                ?: return clusterAnchor
        return sets.firstOrNull { it.slotId == activation.slotId } ?: clusterAnchor
    }

    /** Minimal [SetSlot] projection so recompute shares [MyoMatchStructure] with live/edit. */
    private fun RenderedSet.toStructureSlot(): SetSlot =
        SetSlot(
            slotId = slotId,
            workoutLiftId = workoutLiftId,
            liftId = liftId,
            position = position,
            setType = setType,
            isTopSet = isTopSet,
            repMin = repMin,
            repMax = repMax,
            rirGoal = rirGoal,
            restSeconds = restSeconds,
            myoRepKind = myoRepKind,
        )

    internal fun isActivationTooHeavy(logged: LoggedSetValue, activation: RenderedSet): Boolean {
        if (logged.reps <= MyoRepLifecycle.DEFAULT_REP_FLOOR) return true
        if (activation.repMin <= 0) return false
        val effectiveRir = logged.rir ?: 0
        val achieved = logged.reps + effectiveRir - activation.rirGoal
        return achieved <= activation.repMin - TOO_HEAVY_REP_MARGIN
    }

    private fun reduceOneStep(
        weight: Weight,
        incrementTiers: List<IncrementTier>,
        loadType: LoadType,
        micro: List<Weight> = emptyList(),
    ): Weight {
        val step = incrementTiers.incrementFor(weight)?.amount
            ?: defaultStep(weight.unit)
        val lowered = Weight((weight.amount - step).coerceAtLeast(step), weight.unit)
        return if (incrementTiers.isEmpty()) {
            lowered
        } else {
            incrementTiers.roundToIncrement(lowered, loadType, micro)
        }
    }

    private fun defaultStep(unit: Weight.WeightUnit): Double =
        when (unit) {
            Weight.WeightUnit.Lb -> DEFAULT_LB_STEP
            Weight.WeightUnit.Kg -> DEFAULT_KG_STEP
        }

    companion object {
        private const val TOO_HEAVY_REP_MARGIN = 2
        private const val DEFAULT_LB_STEP = 5.0
        private const val DEFAULT_KG_STEP = 2.5
    }
}
