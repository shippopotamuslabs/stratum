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
 * (set-index + remainingCount) so the engine still sees the cluster volume; unlogged
 * intensifiers stay excluded from remainingCount so planned minis/drops do not
 * over-cut shared straight load.
 * **Drop sets** use 20% off the immediate prior (performed, else prior live rec, else
 * snapshot prescription) and project reps with zero-rest [DropSetRepProjection] from
 * that prior — not normal-rest population fatigue. **Myo minis** copy activation load
 * (reduced when activation was way too heavy) and project reps with short-rest
 * [MyoMiniRepProjection] from the last logged set in the myo chain.
 *
 * **Post-anchor only:** unlogged sets before the last performed set do not receive live recs.
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
        val lastPerformedSet = lift.sets
            .filter { it.loggedValue != null }
            .maxByOrNull { it.position }
        val lastPerformed = lastPerformedSet?.loggedValue
        if (lastPerformedSet == null || lastPerformed == null) return emptyMap()

        val setsByPosition = lift.sets.sortedBy { it.position }
        val anchorOrdinal = setsByPosition.indexOfFirst { it.slotId == lastPerformedSet.slotId }
        val postAnchorUpcoming = if (anchorOrdinal < 0) {
            emptyList()
        } else {
            setsByPosition.drop(anchorOrdinal + 1).filter { it.loggedValue == null }
        }
        if (postAnchorUpcoming.isEmpty()) return emptyMap()

        val incrementTiers = liftsById[lift.liftId]?.incrementTiers ?: emptyList()
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
            postAnchorUpcoming = postAnchorUpcoming,
            setsByPosition = setsByPosition,
            lastPerformed = lastPerformed,
            incrementTiers = incrementTiers,
            loadType = lift.loadType,
            priorResults = results,
        )
        results += myoMiniRecommendations(
            postAnchorUpcoming = postAnchorUpcoming,
            setsByPosition = setsByPosition,
            lastPerformed = lastPerformed,
            incrementTiers = incrementTiers,
            loadType = lift.loadType,
            priorResults = results,
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
        postAnchorUpcoming: List<RenderedSet>,
        setsByPosition: List<RenderedSet>,
        lastPerformed: LoggedSetValue,
        incrementTiers: List<IncrementTier>,
        loadType: LoadType,
        priorResults: Map<SetSlotId, LiveSetRecommendation>,
    ): Map<SetSlotId, LiveSetRecommendation> {
        val out = linkedMapOf<SetSlotId, LiveSetRecommendation>()
        // Merge live drop recs as we go so multi-drop chains resolve prior weight/reps.
        val resolved = priorResults.toMutableMap()
        for (set in postAnchorUpcoming.filter { it.setType == SetType.DropSet }) {
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
        lastPerformed: LoggedSetValue,
        incrementTiers: List<IncrementTier>,
        loadType: LoadType,
        resolved: Map<SetSlotId, LiveSetRecommendation>,
    ): LiveSetRecommendation? {
        val prior = priorSet(setsByPosition, set) ?: return null
        val priorWeight = resolvePriorWeight(prior, resolved) ?: return null
        val (priorReps, priorRir) = resolvePriorRepsAndRir(
            prior = prior,
            lastPerformed = lastPerformed,
            resolved = resolved,
        )
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
     * Immediate prior set's performed reps/RIR when logged; otherwise the live rec for
     * that prior (chained drop); else the lift's last performed set.
     */
    private fun resolvePriorRepsAndRir(
        prior: RenderedSet,
        lastPerformed: LoggedSetValue,
        resolved: Map<SetSlotId, LiveSetRecommendation>,
    ): Pair<Int, Int?> {
        prior.loggedValue?.let { return it.reps to it.rir }
        resolved[prior.slotId]?.reps?.takeIf { it > 0 }?.let { return it to 0 }
        return lastPerformed.reps to lastPerformed.rir
    }

    /**
     * Myo mini load copies activation (with too-heavy reduce). Reps use short-rest
     * [MyoMiniRepProjection] from the last logged set in the myo chain — not normal-rest
     * [FatigueDecay] (that slope is multi-set rest, not ~15s minis).
     */
    private fun myoMiniRecommendations(
        postAnchorUpcoming: List<RenderedSet>,
        setsByPosition: List<RenderedSet>,
        lastPerformed: LoggedSetValue,
        incrementTiers: List<IncrementTier>,
        loadType: LoadType,
        priorResults: Map<SetSlotId, LiveSetRecommendation>,
    ): Map<SetSlotId, LiveSetRecommendation> =
        postAnchorUpcoming
            .filter { isMyoMini(it) }
            .mapNotNull { set ->
                val activation = findActivation(setsByPosition, set) ?: return@mapNotNull null
                val activationLogged = activation.loggedValue
                var miniWeight = activationLogged?.weight
                    ?: priorResults[activation.slotId]?.weight
                    ?: activation.recommendedWeight
                if (miniWeight.amount <= 0.0) return@mapNotNull null
                if (activationLogged != null && isActivationTooHeavy(activationLogged, activation)) {
                    miniWeight = reduceOneStep(miniWeight, incrementTiers, loadType)
                }
                val priorInChain = lastLoggedMyoBefore(setsByPosition, set)
                val priorLogged = priorInChain?.loggedValue ?: activationLogged ?: lastPerformed
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
        // Prefer activation / last standard over a mini or drop as the e1RM source.
        val performanceAnchor = resolveStraightPerformanceAnchor(setsByPosition, lastPerformedSet)
        val performanceLogged = performanceAnchor.loggedValue ?: lastPerformed
        val performanceOrdinal = setsByPosition
            .indexOfFirst { it.slotId == performanceAnchor.slotId }
            .let { if (it < 0) anchorOrdinal else it }

        // Completed intensifiers (and any other logged slots) after the performance
        // anchor already spent fatigue; count them so Hold/Reduce targets the real
        // last remaining set, not "activation + N straights" as if the cluster never ran.
        val lastPerfOrdinal = setsByPosition
            .indexOfFirst { it.slotId == lastPerformedSet.slotId }
            .let { if (it < 0) performanceOrdinal else it }
        val completedFatigueAfterAnchor = setsByPosition
            .filterIndexed { i, s ->
                i > performanceOrdinal && i <= lastPerfOrdinal && s.loggedValue != null
            }
            .size
        val remainingCount = completedFatigueAfterAnchor + straightUpcoming.size
        val lastUpcoming = straightUpcoming.last()
        val reanchoredPastIntensifiers = performanceAnchor.slotId != lastPerformedSet.slotId
        val sharedOutcome = recommendationEngine.recommendNextSet(
            lastPerformed = performanceLogged,
            completedSlot = toSlot(performanceAnchor),
            targetSlot = toSlot(lastUpcoming),
            currentWeight = lastUpcoming.recommendedWeight,
            remainingCount = remainingCount,
            incrementTiers = incrementTiers,
            perLiftSlope = fatigue.slope,
            perLiftIntercept = fatigue.intercept,
            loadType = lift.loadType,
            completedSetPrescribedWeight = performanceAnchor.recommendedWeight,
        )
        // Engine null = on-track top set → leave snapshot weight alone for normal
        // straight blocks. After a completed myo/drop cluster we still need live
        // recs so minis/drops contribute set-index fatigue; keep activation load.
        val sharedWeight = when (sharedOutcome) {
            is RecommendationOutcome.Hold -> sharedOutcome.weight
            is RecommendationOutcome.Reduce -> sharedOutcome.weight
            null -> if (reanchoredPastIntensifiers) {
                recommendationEngine.resolveWorkingWeight(
                    performed = performanceLogged.weight,
                    targetPrescribed = lastUpcoming.recommendedWeight,
                    completedPrescribed = performanceAnchor.recommendedWeight,
                )
            } else {
                null
            }
        }
        if (sharedWeight == null || sharedWeight.amount <= 0.0) return emptyMap()

        return straightUpcoming.associate { set ->
            val targetOrdinal = setsByPosition.indexOfFirst { it.slotId == set.slotId }
            // Set index walks from the performance anchor so logged minis/drops between
            // activation and this standard contribute normal-rest fatigue decay steps.
            val stepsAhead = (targetOrdinal - performanceOrdinal).coerceAtLeast(1)
            val upcomingSetIndex = (1 + stepsAhead).coerceAtLeast(1)
            val recommendedReps = projectNextSetReps(
                lastPerformed = performanceLogged,
                targetWeight = sharedWeight,
                upcomingSetIndex = upcomingSetIndex,
                repMax = set.repMax,
                rirGoal = set.rirGoal,
                perLiftSlope = fatigue.slope,
                perLiftIntercept = fatigue.intercept,
            )
            set.slotId to LiveSetRecommendation(
                weight = sharedWeight,
                reps = recommendedReps.coerceAtLeast(1),
            )
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

    private fun findActivation(sets: List<RenderedSet>, mini: RenderedSet): RenderedSet? {
        val miniIdx = sets.indexOfFirst { it.slotId == mini.slotId }
        if (miniIdx < 0) return null
        val prior = sets.take(miniIdx)
        return prior.lastOrNull { it.setType == SetType.MyoRep && it.isTopSet }
            ?: prior.lastOrNull { it.setType == SetType.MyoRep && it.slotId is SetSlotId.Template }
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
