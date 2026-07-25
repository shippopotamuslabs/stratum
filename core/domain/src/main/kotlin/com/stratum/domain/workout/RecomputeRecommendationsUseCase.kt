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
import com.stratum.domain.workout.myorep.MyoMiniRepProjection
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
 * **Myo minis exception:** unlogged auto-minis still get recs when their activation is
 * logged, even if a later cluster was completed first — multi-cluster out-of-order work
 * must not blank the earlier open mini.
 */
class RecomputeRecommendationsUseCase @Inject constructor(
    private val recommendationEngine: RecommendationEngine,
    private val fatigueModel: FatigueModel,
    private val estimateDropSetWeight: EstimateDropSetWeightUseCase = EstimateDropSetWeightUseCase(),
) {
    operator fun invoke(
        rendered: RenderedWorkout,
        liftsById: Map<LiftId, Lift>,
        fatigueByLiftId: Map<LiftId, FatigueDecay> = emptyMap(),
    ): Map<SetSlotId, LiveSetRecommendation> =
        rendered.lifts
            .map { lift -> recommendationsForLift(lift, liftsById, fatigueByLiftId) }
            .fold(emptyMap()) { acc, part -> acc + part }

    private fun recommendationsForLift(
        lift: RenderedWorkoutLift,
        liftsById: Map<LiftId, Lift>,
        fatigueByLiftId: Map<LiftId, FatigueDecay>,
    ): Map<SetSlotId, LiveSetRecommendation> {
        val setsByPosition = lift.sets.sortedBy { it.position }
        val incrementTiers = liftsById[lift.liftId]?.incrementTiers ?: emptyList()
        val lastPerformedSet = lift.sets
            .filter { it.loggedValue != null }
            .maxByOrNull { it.position }
        val lastPerformed = lastPerformedSet?.loggedValue

        // Cold open: no logs yet — still prescribe drops from prior snapshot weights so
        // incomplete parents do not leave drops at the shared straight-set load (150→120).
        if (lastPerformedSet == null || lastPerformed == null) {
            val unloggedDrops = setsByPosition.filter {
                it.loggedValue == null && it.setType == SetType.DropSet
            }
            if (unloggedDrops.isEmpty()) return emptyMap()
            return dropRecommendations(
                drops = unloggedDrops,
                setsByPosition = setsByPosition,
                lastPerformed = null,
                incrementTiers = incrementTiers,
                loadType = lift.loadType,
                priorResults = emptyMap(),
            )
        }

        val anchorOrdinal = setsByPosition.indexOfFirst { it.slotId == lastPerformedSet.slotId }
        val postAnchorUpcoming = if (anchorOrdinal < 0) {
            emptyList()
        } else {
            setsByPosition.drop(anchorOrdinal + 1).filter { it.loggedValue == null }
        }
        // Open myo minis anywhere in the lift (not only post-anchor) — cluster order of work
        // can leave an earlier activation's mini still open after a later cluster is done.
        val unloggedMyoMinis = setsByPosition.filter { it.loggedValue == null && isMyoMini(it) }
        if (postAnchorUpcoming.isEmpty() && unloggedMyoMinis.isEmpty()) return emptyMap()

        val fatigue = fatigueByLiftId[lift.liftId] ?: FatigueDecay(null, null)
        val results = linkedMapOf<SetSlotId, LiveSetRecommendation>()

        val straightUpcoming = postAnchorUpcoming.filter { isStraightWorking(it) }
        if (straightUpcoming.isNotEmpty() && lift.loadType == LoadType.Weighted) {
            results += straightRecommendations(
                lift = lift,
                lastPerformedSet = lastPerformedSet,
                lastPerformed = lastPerformed,
                setsByPosition = setsByPosition,
                anchorOrdinal = anchorOrdinal,
                straightUpcoming = straightUpcoming,
                incrementTiers = incrementTiers,
                fatigue = fatigue,
            )
        }

        results += dropRecommendations(
            drops = postAnchorUpcoming.filter { it.setType == SetType.DropSet },
            setsByPosition = setsByPosition,
            lastPerformed = lastPerformed,
            incrementTiers = incrementTiers,
            loadType = lift.loadType,
            priorResults = results,
        )
        results += myoMiniRecommendations(
            unloggedMinis = unloggedMyoMinis,
            setsByPosition = setsByPosition,
            incrementTiers = incrementTiers,
            loadType = lift.loadType,
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
    ): Map<SetSlotId, LiveSetRecommendation> =
        unloggedMinis
            .mapNotNull { set ->
                val activation = findActivation(setsByPosition, set) ?: return@mapNotNull null
                // No activation log yet → cannot seed mini from this cluster (wait for log).
                val activationLogged = activation.loggedValue ?: return@mapNotNull null
                var miniWeight = activationLogged.weight
                if (miniWeight.amount <= 0.0) return@mapNotNull null
                if (isActivationTooHeavy(activationLogged, activation)) {
                    miniWeight = reduceOneStep(miniWeight, incrementTiers, loadType)
                }
                val priorInChain = lastLoggedMyoBefore(setsByPosition, set)
                val priorLogged = priorInChain?.loggedValue ?: activationLogged
                val isFirstMini = priorInChain == null || priorInChain.slotId == activation.slotId
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

    private fun straightRecommendations(
        lift: RenderedWorkoutLift,
        lastPerformedSet: RenderedSet,
        lastPerformed: LoggedSetValue,
        setsByPosition: List<RenderedSet>,
        anchorOrdinal: Int,
        straightUpcoming: List<RenderedSet>,
        incrementTiers: List<IncrementTier>,
        fatigue: FatigueDecay,
    ): Map<SetSlotId, LiveSetRecommendation> {
        val block =
            resolveStraightBlock(
                setsByPosition = setsByPosition,
                lastPerformedSet = lastPerformedSet,
                lastPerformed = lastPerformed,
                anchorOrdinal = anchorOrdinal,
                straightUpcoming = straightUpcoming,
            ) ?: return emptyMap()

        // One engine entry: pure straights may keep snapshot on-track; post-cluster always
        // runs Hold/Reduce + last-set floor (keepSnapshotWhenOnTrack = !forceLive).
        val sharedOutcome =
            recommendationEngine.recommendNextSet(
                lastPerformed = block.performanceLogged,
                completedSlot = toSlot(block.performanceAnchor),
                targetSlot = toSlot(block.lastUpcoming),
                currentWeight = block.lastUpcoming.recommendedWeight,
                remainingCount = block.remainingCount,
                incrementTiers = incrementTiers,
                perLiftSlope = fatigue.slope,
                perLiftIntercept = fatigue.intercept,
                loadType = lift.loadType,
                completedSetPrescribedWeight = block.performanceAnchor.recommendedWeight,
                keepSnapshotWhenOnTrack = !block.forceLiveWeight,
            ) ?: return emptyMap()
        val sharedWeight = recommendationEngine.weightOf(sharedOutcome)
        if (sharedWeight.amount <= 0.0) return emptyMap()

        return straightUpcoming.associate { set ->
            val targetOrdinal = setsByPosition.indexOfFirst { it.slotId == set.slotId }
            val recommendedReps =
                projectNextSetReps(
                    lastPerformed = block.performanceLogged,
                    targetWeight = sharedWeight,
                    upcomingSetIndex =
                        fatigueSetIndex(
                            performanceOrdinal = block.performanceOrdinal,
                            targetOrdinal = targetOrdinal,
                        ),
                    repMax = set.repMax,
                    rirGoal = set.rirGoal,
                    perLiftSlope = fatigue.slope,
                    perLiftIntercept = fatigue.intercept,
                )
            set.slotId to
                LiveSetRecommendation(
                    weight = sharedWeight,
                    reps = recommendedReps.coerceAtLeast(BACKOFF_REP_FLOOR),
                )
        }
    }

    /**
     * Shared straight-block geometry for weight + rep projection.
     * [remainingCount] and [fatigueSetIndex] both use ordinals so open minis/drops
     * between activation and the last standard count the same way.
     */
    private data class StraightBlock(
        val performanceAnchor: RenderedSet,
        val performanceLogged: LoggedSetValue,
        val performanceOrdinal: Int,
        val lastUpcoming: RenderedSet,
        val remainingCount: Int,
        /** True when a mini/drop sits after the e1RM anchor (open or completed). */
        val forceLiveWeight: Boolean,
    )

    private fun resolveStraightBlock(
        setsByPosition: List<RenderedSet>,
        lastPerformedSet: RenderedSet,
        lastPerformed: LoggedSetValue,
        anchorOrdinal: Int,
        straightUpcoming: List<RenderedSet>,
    ): StraightBlock? {
        if (straightUpcoming.isEmpty()) return null
        val performanceAnchor =
            resolveStraightPerformanceAnchor(setsByPosition, lastPerformedSet)
        val performanceLogged = performanceAnchor.loggedValue ?: lastPerformed
        val performanceOrdinal =
            setsByPosition
                .indexOfFirst { it.slotId == performanceAnchor.slotId }
                .let { if (it < 0) anchorOrdinal else it }
        val lastUpcoming = straightUpcoming.last()
        val lastUpcomingOrdinal =
            setsByPosition
                .indexOfFirst { it.slotId == lastUpcoming.slotId }
                .let { if (it < 0) performanceOrdinal + straightUpcoming.size else it }
        val remainingCount =
            remainingCountAfterPerformanceAnchor(
                performanceOrdinal = performanceOrdinal,
                lastUpcomingOrdinal = lastUpcomingOrdinal,
            )
        val forceLiveWeight =
            mustForceLiveStraightWeight(
                performanceAnchor = performanceAnchor,
                lastPerformedSet = lastPerformedSet,
                setsByPosition = setsByPosition,
                performanceOrdinal = performanceOrdinal,
            )
        return StraightBlock(
            performanceAnchor = performanceAnchor,
            performanceLogged = performanceLogged,
            performanceOrdinal = performanceOrdinal,
            lastUpcoming = lastUpcoming,
            remainingCount = remainingCount,
            forceLiveWeight = forceLiveWeight,
        )
    }

    /**
     * Fatigue set-index of [targetOrdinal] when the performance anchor is set-index 1
     * at [performanceOrdinal] (includes intermediate minis/drops).
     */
    private fun fatigueSetIndex(performanceOrdinal: Int, targetOrdinal: Int): Int =
        (targetOrdinal - performanceOrdinal + 1).coerceAtLeast(1)

    /** Engine remainingCount so last set index matches [fatigueSetIndex] for last upcoming. */
    private fun remainingCountAfterPerformanceAnchor(
        performanceOrdinal: Int,
        lastUpcomingOrdinal: Int,
    ): Int =
        (fatigueSetIndex(performanceOrdinal, lastUpcomingOrdinal) - 1).coerceAtLeast(0)

    /**
     * Post-myo/drop standards must not keep pure on-track snapshot: either last log is an
     * intensifier (re-anchor past it) or a mini/drop still sits after the e1RM anchor.
     */
    private fun mustForceLiveStraightWeight(
        performanceAnchor: RenderedSet,
        lastPerformedSet: RenderedSet,
        setsByPosition: List<RenderedSet>,
        performanceOrdinal: Int,
    ): Boolean {
        if (performanceAnchor.slotId != lastPerformedSet.slotId) return true
        return setsByPosition.withIndex().any { (index, set) ->
            index > performanceOrdinal && isIntensifierSlot(set)
        }
    }

    /**
     * Latest logged straight-working set at or before [lastPerformedSet] (Standard or
     * Myo activation). Myo minis and drops must not own straight-set e1RM.
     */
    private fun resolveStraightPerformanceAnchor(
        setsByPosition: List<RenderedSet>,
        lastPerformedSet: RenderedSet,
    ): RenderedSet {
        val lastIdx = setsByPosition.indexOfFirst { it.slotId == lastPerformedSet.slotId }
        if (lastIdx < 0) return lastPerformedSet
        return setsByPosition
            .take(lastIdx + 1)
            .lastOrNull { it.loggedValue != null && isStraightWorking(it) }
            ?: lastPerformedSet
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
     * Owning activation for [mini] = nearest prior **non-mini** MyoRep.
     * Must not prefer [RenderedSet.isTopSet] alone — multi-cluster second activations are
     * often not the lift top set, and would incorrectly inherit the first cluster's load.
     */
    private fun findActivation(sets: List<RenderedSet>, mini: RenderedSet): RenderedSet? {
        val miniIdx = sets.indexOfFirst { it.slotId == mini.slotId }
        if (miniIdx < 0) return null
        val prior = sets.take(miniIdx)
        return prior.lastOrNull { it.setType == SetType.MyoRep && !isMyoMini(it) }
            ?: prior.lastOrNull { it.setType == SetType.MyoRep }
    }

    /**
     * Honest capacity at the slot RIR: never raises to `repMin`. Caps at `repMax` when set.
     */
    private fun projectNextSetReps(
        lastPerformed: LoggedSetValue?,
        targetWeight: Weight,
        upcomingSetIndex: Int,
        repMax: Int,
        rirGoal: Int,
        perLiftSlope: Double?,
        perLiftIntercept: Double?,
    ): Int {
        val lastLoadLb = lastPerformed?.let { WeightConversion.loadMagnitudeLb(it.weight) } ?: 0.0
        val targetLoadLb = WeightConversion.loadMagnitudeLb(targetWeight)
        if (lastPerformed == null || lastLoadLb <= 0.0 || targetLoadLb <= 0.0) {
            return 1
        }
        val effectiveRir = lastPerformed.rir ?: 0
        val baselineReps = lastPerformed.reps + effectiveRir
        if (baselineReps <= 0) return 1
        val baselineOneRm = EstimatedOneRm.calculate(
            lastLoadLb,
            baselineReps,
        )
        val rawCapacityReps = fatigueModel.expectedRepsForAddedSet(
            baselineOneRm = baselineOneRm,
            targetWeight = targetWeight,
            nextSetIndex = upcomingSetIndex,
            perLiftSlope = perLiftSlope,
            perLiftIntercept = perLiftIntercept,
        )
        val atTargetRir = rawCapacityReps - rirGoal
        val honest = atTargetRir.coerceAtLeast(1)
        return if (repMax > 0) {
            honest.coerceAtMost(repMax.coerceAtLeast(1))
        } else {
            honest
        }
    }

    private fun toSlot(rendered: RenderedSet): SetSlot =
        SetSlot(
            slotId = rendered.slotId,
            workoutLiftId = rendered.workoutLiftId,
            liftId = rendered.liftId,
            position = rendered.position,
            setType = rendered.setType,
            isTopSet = rendered.isTopSet,
            repMin = rendered.repMin,
            repMax = rendered.repMax,
            rirGoal = rendered.rirGoal,
            restSeconds = rendered.restSeconds,
            myoRepKind = null,
        )

    private fun isStraightWorking(set: RenderedSet): Boolean =
        when (set.setType) {
            SetType.Standard -> true
            // Myo activation (template / top) is a normal working set for load selection.
            // Live minis are overlay-only and never straight working.
            SetType.MyoRep -> !isMyoMini(set)
            SetType.DropSet -> false
        }

    /** Live-generated minis are overlay slots; template MyoRep is always the activation. */
    private fun isMyoMini(set: RenderedSet): Boolean =
        set.setType == SetType.MyoRep && set.slotId is SetSlotId.Overlay

    private fun isIntensifierSlot(set: RenderedSet): Boolean =
        isMyoMini(set) || set.setType == SetType.DropSet

    /**
     * Activation is way too heavy when logged reps hit the myo floor or landed ≥2 below
     * the prescribed repMin (RIR-adjusted).
     */
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
    ): Weight {
        val step = incrementTiers.incrementFor(weight)?.amount
            ?: defaultStep(weight.unit)
        val lowered = Weight((weight.amount - step).coerceAtLeast(step), weight.unit)
        return if (incrementTiers.isEmpty()) {
            lowered
        } else {
            incrementTiers.roundToIncrement(lowered, loadType)
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
