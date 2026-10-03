package com.stratum.domain.workout

import com.stratum.domain.importing.FatigueDecay
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.WeightConversion
import com.stratum.domain.program.SetType
import com.stratum.domain.training.EstimatedOneRm
import com.stratum.domain.workout.bodyweight.BodyweightRepProjection
import com.stratum.domain.workout.fatigue.FatigueModel
import javax.inject.Inject

/**
 * Straight-set live recommendation policy: exact-match freeze vs adaptive re-anchor,
 * cold-open fatigue reps, and block geometry.
 *
 * Extracted from [RecomputeRecommendationsUseCase] so drop/myo pipelines stay separate
 * and straight-set control flow is testable without the full recompute surface.
 */
class StraightSetRecommendationPolicy @Inject constructor(
    private val recommendationEngine: RecommendationEngine,
    private val fatigueModel: FatigueModel,
) {
    fun recommend(
        lift: RenderedWorkoutLift,
        lastPerformedSet: RenderedSet,
        lastPerformed: LoggedSetValue,
        setsByPosition: List<RenderedSet>,
        anchorOrdinal: Int,
        straightUpcoming: List<RenderedSet>,
        incrementTiers: List<IncrementTier>,
        fatigue: FatigueDecay,
        priorRecommendations: Map<SetSlotId, LiveSetRecommendation>,
        micro: List<Weight> = emptyList(),
    ): Map<SetSlotId, LiveSetRecommendation> {
        val block =
            resolveStraightBlock(
                setsByPosition = setsByPosition,
                lastPerformedSet = lastPerformedSet,
                lastPerformed = lastPerformed,
                anchorOrdinal = anchorOrdinal,
                straightUpcoming = straightUpcoming,
            ) ?: return emptyMap()

        val priorForLogged = priorRecommendations[lastPerformedSet.slotId]
        val completedPrescribedWeight =
            resolveCompletedPrescribedWeight(
                priorForLogged = priorForLogged,
                lastPerformedSet = lastPerformedSet,
                performanceAnchor = block.performanceAnchor,
            )
        val freeze =
            resolveFreezeDecision(
                block = block,
                lastPerformedSet = lastPerformedSet,
                lastPerformed = lastPerformed,
                priorForLogged = priorForLogged,
                completedPrescribedWeight = completedPrescribedWeight,
                straightUpcoming = straightUpcoming,
                priorRecommendations = priorRecommendations,
                setsByPosition = setsByPosition,
            )
        if (freeze != null) {
            return freezeStraightUpcoming(
                freeze = freeze,
                block = block,
                lastPerformed = lastPerformed,
                straightUpcoming = straightUpcoming,
                setsByPosition = setsByPosition,
                priorRecommendations = priorRecommendations,
                completedPrescribedWeight = completedPrescribedWeight,
                fatigue = fatigue,
            )
        }
        return adaptiveStraightUpcoming(
            lift = lift,
            block = block,
            lastPerformed = lastPerformed,
            straightUpcoming = straightUpcoming,
            setsByPosition = setsByPosition,
            incrementTiers = incrementTiers,
            fatigue = fatigue,
            completedPrescribedWeight = completedPrescribedWeight,
            micro = micro,
        )
    }

    private fun resolveCompletedPrescribedWeight(
        priorForLogged: LiveSetRecommendation?,
        lastPerformedSet: RenderedSet,
        performanceAnchor: RenderedSet,
    ): Weight =
        priorForLogged?.weight?.takeIf { it.amount > 0.0 }
            ?: lastPerformedSet.recommendedWeight.takeIf { it.amount > 0.0 }
            ?: performanceAnchor.recommendedWeight

    /**
     * Freeze modes for remaining straight sets:
     * - [FreezeDecision.Exact]: logged the shown prescription (or first-set on-track).
     * - [FreezeDecision.EstablishedUpcoming]: restore/second-pass when upcoming priors still
     *   share the performed load (pre-log prior row already wiped).
     */
    private sealed interface FreezeDecision {
        data object Exact : FreezeDecision
        data object EstablishedUpcoming : FreezeDecision
    }

    private fun resolveFreezeDecision(
        block: StraightBlock,
        lastPerformedSet: RenderedSet,
        lastPerformed: LoggedSetValue,
        priorForLogged: LiveSetRecommendation?,
        completedPrescribedWeight: Weight,
        straightUpcoming: List<RenderedSet>,
        priorRecommendations: Map<SetSlotId, LiveSetRecommendation>,
        setsByPosition: List<RenderedSet>,
    ): FreezeDecision? {
        if (block.forceLiveWeight) return null
        // Primary freeze: logged exactly what the live overlay showed (weight + reps + RIR).
        // Omitted RIR is not exact — adaptive path re-evaluates load. Do NOT require
        // isOnTrackPerformance — template rep bands can disagree with live fatigue recs.
        val priorExact =
            priorForLogged != null &&
                priorForLogged.reps > 0 &&
                lastPerformed.reps == priorForLogged.reps &&
                WeightConversion.nearlyEqualLb(
                    WeightConversion.amountInLb(lastPerformed.weight),
                    WeightConversion.amountInLb(priorForLogged.weight),
                ) &&
                lastPerformed.rir != null &&
                lastPerformed.rir == lastPerformedSet.rirGoal
        if (priorExact) return FreezeDecision.Exact

        // Fallback freeze: engine load on-track when there is no prior live row for this slot
        // (e.g. first set of the lift, which never has an upcoming rec).
        if (priorForLogged == null) {
            val loadOnTrack =
                recommendationEngine.isOnTrackPerformance(
                    lastPerformed = lastPerformed,
                    completedSlot = toSlot(lastPerformedSet),
                    completedPrescribed = completedPrescribedWeight,
                )
            val shownMatch =
                matchesShownPrescription(
                    set = lastPerformedSet,
                    logged = lastPerformed,
                    priorRecommendations = priorRecommendations,
                )
            if (loadOnTrack && shownMatch) return FreezeDecision.Exact
            // Restore / cycle re-emit: upcoming priors already share the performed load after
            // a prior freeze (or adaptive reduce). Never apply this for the first straight
            // set of the lift — cold-open priors share snapshot weight with that set, so an
            // off-track top (wild reps / different RIR) would freeze stale fatigue reps.
            // Weight-only mismatch still goes adaptive because established freeze fails.
            if (
                !isFirstStraightWorking(setsByPosition, lastPerformedSet) &&
                isEstablishedUpcomingFreeze(straightUpcoming, priorRecommendations, lastPerformed)
            ) {
                return FreezeDecision.EstablishedUpcoming
            }
        }
        return null
    }

    /** True when [set] is the earliest straight-working slot in the lift. */
    private fun isFirstStraightWorking(
        setsByPosition: List<RenderedSet>,
        set: RenderedSet,
    ): Boolean {
        val first = setsByPosition.firstOrNull { isStraightWorking(it) } ?: return false
        return first.slotId == set.slotId
    }

    /**
     * Second-pass / restore freeze: the logged set's prior row is wiped after the recompute
     * that first froze the block. If upcoming priors still share the performed load, keep
     * them — re-running last-set floor Reduce would drop weight (e.g. 315→295) solely because
     * the pre-log "shown" row is gone. Matching weights so intentional adaptive Reduce is kept.
     */
    private fun isEstablishedUpcomingFreeze(
        straightUpcoming: List<RenderedSet>,
        priorRecommendations: Map<SetSlotId, LiveSetRecommendation>,
        lastPerformed: LoggedSetValue,
    ): Boolean {
        if (straightUpcoming.isEmpty()) return false
        val performedLb = WeightConversion.amountInLb(lastPerformed.weight)
        return straightUpcoming.all { set ->
            val prior = priorRecommendations[set.slotId] ?: return@all false
            prior.weight.amount > 0.0 &&
                WeightConversion.nearlyEqualLb(
                    WeightConversion.amountInLb(prior.weight),
                    performedLb,
                )
        }
    }

    private fun freezeStraightUpcoming(
        freeze: FreezeDecision,
        block: StraightBlock,
        lastPerformed: LoggedSetValue,
        straightUpcoming: List<RenderedSet>,
        setsByPosition: List<RenderedSet>,
        priorRecommendations: Map<SetSlotId, LiveSetRecommendation>,
        completedPrescribedWeight: Weight,
        fatigue: FatigueDecay,
    ): Map<SetSlotId, LiveSetRecommendation> {
        val heldWeight =
            when (freeze) {
                FreezeDecision.EstablishedUpcoming ->
                    priorRecommendations[straightUpcoming.first().slotId]?.weight
                        ?.takeIf { it.amount > 0.0 }
                        ?: lastPerformed.weight
                FreezeDecision.Exact ->
                    block.lastUpcoming.recommendedWeight
                        .takeIf { it.amount > 0.0 }
                        ?: completedPrescribedWeight.takeIf { it.amount > 0.0 }
                        ?: lastPerformed.weight
            }
        val preserved =
            preservePriorStraightRecs(
                straightUpcoming = straightUpcoming,
                priorRecommendations = priorRecommendations,
                heldWeight = heldWeight,
            )
        if (preserved != null) return preserved
        // No prior coverage (e.g. first set of session): project with block-stable geometry.
        val geometry =
            stableBlockGeometry(setsByPosition)
                ?: FatigueGeometry(block.performanceLogged, block.performanceOrdinal)
        return projectStraightUpcoming(
            straightUpcoming = straightUpcoming,
            setsByPosition = setsByPosition,
            sharedWeight = heldWeight,
            geometry = geometry,
            fatigue = fatigue,
        )
    }

    private fun adaptiveStraightUpcoming(
        lift: RenderedWorkoutLift,
        block: StraightBlock,
        lastPerformed: LoggedSetValue,
        straightUpcoming: List<RenderedSet>,
        setsByPosition: List<RenderedSet>,
        incrementTiers: List<IncrementTier>,
        fatigue: FatigueDecay,
        completedPrescribedWeight: Weight,
        micro: List<Weight> = emptyList(),
    ): Map<SetSlotId, LiveSetRecommendation> {
        // keepSnapshotWhenOnTrack when not post-myo/drop so engine null means "keep weight"
        // (caller still projects fatigue reps) rather than wiping live straight recs.
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
                micro = micro,
            )
        val sharedWeight =
            sharedOutcome?.let { recommendationEngine.weightOf(it) }
                ?.takeIf { it.amount > 0.0 }
                ?: block.lastUpcoming.recommendedWeight.takeIf { it.amount > 0.0 }
                ?: completedPrescribedWeight.takeIf { it.amount > 0.0 }
                ?: lastPerformed.weight.takeIf { it.amount > 0.0 }
                ?: return emptyMap()
        return projectStraightUpcoming(
            straightUpcoming = straightUpcoming,
            setsByPosition = setsByPosition,
            sharedWeight = sharedWeight,
            geometry = FatigueGeometry(block.performanceLogged, block.performanceOrdinal),
            fatigue = fatigue,
        )
    }

    private fun preservePriorStraightRecs(
        straightUpcoming: List<RenderedSet>,
        priorRecommendations: Map<SetSlotId, LiveSetRecommendation>,
        heldWeight: Weight,
    ): Map<SetSlotId, LiveSetRecommendation>? {
        if (straightUpcoming.isEmpty() || priorRecommendations.isEmpty()) return null
        val preserved =
            straightUpcoming.mapNotNull { set ->
                val prior = priorRecommendations[set.slotId] ?: return@mapNotNull null
                set.slotId to
                    LiveSetRecommendation(
                        weight = heldWeight.takeIf { it.amount > 0.0 } ?: prior.weight,
                        reps = prior.reps.coerceAtLeast(BACKOFF_REP_FLOOR),
                    )
            }.toMap()
        return preserved.takeIf { it.size == straightUpcoming.size }
    }

    private fun projectStraightUpcoming(
        straightUpcoming: List<RenderedSet>,
        setsByPosition: List<RenderedSet>,
        sharedWeight: Weight,
        geometry: FatigueGeometry,
        fatigue: FatigueDecay,
    ): Map<SetSlotId, LiveSetRecommendation> =
        straightUpcoming.associate { set ->
            val targetOrdinal = setsByPosition.indexOfFirst { it.slotId == set.slotId }
            val recommendedReps =
                projectNextSetReps(
                    lastPerformed = geometry.capacityLogged,
                    targetWeight = sharedWeight,
                    upcomingSetIndex =
                        fatigueSetIndex(
                            performanceOrdinal = geometry.indexOriginOrdinal,
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

    /**
     * Capacity + set-index origin for projecting remaining straight-set reps.
     * [indexOriginOrdinal] is the "set 1" of the fatigue story in [setsByPosition] order.
     */
    private data class FatigueGeometry(
        val capacityLogged: LoggedSetValue,
        val indexOriginOrdinal: Int,
    )

    private fun matchesShownPrescription(
        set: RenderedSet,
        logged: LoggedSetValue,
        priorRecommendations: Map<SetSlotId, LiveSetRecommendation>,
    ): Boolean {
        val prior = priorRecommendations[set.slotId]
        // Top set never has a live prior row — fall back to snapshot recommendedReps so
        // "exact prescription" still requires matching the shown rep target, not only RIR.
        val shownReps =
            prior?.reps?.takeIf { it > 0 }
                ?: set.recommendedReps.takeIf { it > 0 }
        if (shownReps != null && logged.reps != shownReps) return false
        // Null RIR is never "as prescribed" — adaptive path re-evaluates.
        if (logged.rir != set.rirGoal) return false
        return true
    }

    private fun stableBlockGeometry(setsByPosition: List<RenderedSet>): FatigueGeometry? {
        val firstStraight =
            setsByPosition.firstOrNull { isStraightWorking(it) } ?: return null
        val firstOrdinal =
            setsByPosition.indexOfFirst { it.slotId == firstStraight.slotId }
                .takeIf { it >= 0 } ?: return null
        val capacityLogged =
            firstStraight.loggedValue
                ?: setsByPosition
                    .firstOrNull { isStraightWorking(it) && it.loggedValue != null }
                    ?.loggedValue
                ?: run {
                    val weight =
                        firstStraight.recommendedWeight.takeIf { it.amount > 0.0 }
                            ?: return null
                    val reps =
                        firstStraight.recommendedReps.takeIf { it > 0 } ?: return null
                    LoggedSetValue(
                        slotId = firstStraight.slotId,
                        weight = weight,
                        reps = reps,
                        rir = firstStraight.rirGoal.coerceAtLeast(0),
                    )
                }
        return FatigueGeometry(
            capacityLogged = capacityLogged,
            indexOriginOrdinal = firstOrdinal,
        )
    }

    fun coldOpenReps(
        setsByPosition: List<RenderedSet>,
        fatigue: FatigueDecay,
    ): Map<SetSlotId, LiveSetRecommendation> {
        val straights = setsByPosition.filter { isStraightWorking(it) && it.loggedValue == null }
        if (straights.size <= 1) return emptyMap()
        val first = straights.first()
        val anchorWeight = first.recommendedWeight.takeIf { it.amount > 0.0 } ?: return emptyMap()
        val anchorReps = first.recommendedReps.takeIf { it > 0 } ?: return emptyMap()
        val virtualLogged =
            LoggedSetValue(
                slotId = first.slotId,
                weight = anchorWeight,
                reps = anchorReps,
                rir = first.rirGoal.coerceAtLeast(0),
            )
        val firstOrdinal = setsByPosition.indexOfFirst { it.slotId == first.slotId }
        return straights.drop(1).associate { set ->
            val targetOrdinal = setsByPosition.indexOfFirst { it.slotId == set.slotId }
            val recommendedReps =
                projectNextSetReps(
                    lastPerformed = virtualLogged,
                    targetWeight = anchorWeight,
                    upcomingSetIndex =
                        fatigueSetIndex(
                            performanceOrdinal = firstOrdinal,
                            targetOrdinal = targetOrdinal,
                        ),
                    repMax = set.repMax,
                    rirGoal = set.rirGoal,
                    perLiftSlope = fatigue.slope,
                    perLiftIntercept = fatigue.intercept,
                )
            set.slotId to
                LiveSetRecommendation(
                    weight = anchorWeight,
                    reps = recommendedReps.coerceAtLeast(BACKOFF_REP_FLOOR),
                )
        }
    }

    /**
     * Shared straight-block geometry for weight + rep projection.
     * [remainingCount] and [fatigueSetIndex] both use ordinals so open minis/drops
     * between activation and the last standard count the same way.
     */
    /**
     * Bodyweight straight-set rep projections (STR-29). Weight is always zero for
     * bodyweight (suppressed) — only reps are projected, via the load-free
     * [BodyweightRepProjection] anchored on performed reps+RIR.
     *
     * Rep geometry mirrors the weighted path: an exact-prescription log keeps stable
     * block geometry (no rep jumping); anything else re-anchors on the just-performed
     * set. There is no Hold/Reduce weight machinery — [RecommendationEngine] is
     * bypassed entirely (it returns null for bodyweight by contract).
     */
    fun recommendBodyweight(
        lastPerformedSet: RenderedSet,
        lastPerformed: LoggedSetValue,
        setsByPosition: List<RenderedSet>,
        anchorOrdinal: Int,
        straightUpcoming: List<RenderedSet>,
        fatigue: FatigueDecay,
        priorRecommendations: Map<SetSlotId, LiveSetRecommendation>,
    ): Map<SetSlotId, LiveSetRecommendation> {
        val block =
            resolveStraightBlock(
                setsByPosition = setsByPosition,
                lastPerformedSet = lastPerformedSet,
                lastPerformed = lastPerformed,
                anchorOrdinal = anchorOrdinal,
                straightUpcoming = straightUpcoming,
            ) ?: return emptyMap()
        val geometry =
            if (isExactBodyweightPrescription(lastPerformedSet, lastPerformed, priorRecommendations)) {
                stableBlockGeometry(setsByPosition)
                    ?: FatigueGeometry(block.performanceLogged, block.performanceOrdinal)
            } else {
                FatigueGeometry(block.performanceLogged, block.performanceOrdinal)
            }
        return projectStraightUpcomingBodyweight(
            straightUpcoming = straightUpcoming,
            setsByPosition = setsByPosition,
            zeroWeight = Weight(0.0, lastPerformed.weight.unit),
            geometry = geometry,
            fatigue = fatigue,
        )
    }

    /**
     * Cold-open bodyweight rep projections (STR-29): straight sets after the first get
     * fatigue rep projections from the first set's snapshot prescription (weight stays
     * zero). Mirrors [coldOpenReps] without the load anchor.
     */
    fun coldOpenBodyweightReps(
        setsByPosition: List<RenderedSet>,
        fatigue: FatigueDecay,
    ): Map<SetSlotId, LiveSetRecommendation> {
        val straights = setsByPosition.filter { isStraightWorking(it) && it.loggedValue == null }
        if (straights.size <= 1) return emptyMap()
        val first = straights.first()
        val anchorReps = first.recommendedReps.takeIf { it > 0 } ?: return emptyMap()
        val firstOrdinal = setsByPosition.indexOfFirst { it.slotId == first.slotId }
        val zeroWeight = Weight(0.0, first.recommendedWeight.unit)
        return straights.drop(1).associate { set ->
            val targetOrdinal = setsByPosition.indexOfFirst { it.slotId == set.slotId }
            val projected =
                BodyweightRepProjection.expectedReps(
                    performedReps = anchorReps,
                    performedRir = first.rirGoal.coerceAtLeast(0),
                    setIndex =
                        fatigueSetIndex(
                            performanceOrdinal = firstOrdinal,
                            targetOrdinal = targetOrdinal,
                        ),
                    perLiftSlope = fatigue.slope,
                    perLiftIntercept = fatigue.intercept,
                    rirGoal = set.rirGoal,
                )
            val capped =
                if (set.repMax > 0) projected.coerceAtMost(set.repMax.coerceAtLeast(1)) else projected
            set.slotId to LiveSetRecommendation(weight = zeroWeight, reps = capped)
        }
    }

    /** Exact-prescription check for bodyweight: reps + RIR, no weight machinery. */
    private fun isExactBodyweightPrescription(
        lastPerformedSet: RenderedSet,
        lastPerformed: LoggedSetValue,
        priorRecommendations: Map<SetSlotId, LiveSetRecommendation>,
    ): Boolean {
        val priorForLogged = priorRecommendations[lastPerformedSet.slotId]
        // Primary: logged exactly what the live overlay showed (reps + RIR).
        if (priorForLogged != null && priorForLogged.reps > 0 &&
            lastPerformed.reps == priorForLogged.reps &&
            lastPerformed.rir != null &&
            lastPerformed.rir == lastPerformedSet.rirGoal
        ) {
            return true
        }
        // Fallback: no prior live row (e.g. first set) — match the shown prescription.
        if (priorForLogged == null) {
            return matchesShownPrescription(
                set = lastPerformedSet,
                logged = lastPerformed,
                priorRecommendations = priorRecommendations,
            )
        }
        return false
    }

    private fun projectStraightUpcomingBodyweight(
        straightUpcoming: List<RenderedSet>,
        setsByPosition: List<RenderedSet>,
        zeroWeight: Weight,
        geometry: FatigueGeometry,
        fatigue: FatigueDecay,
    ): Map<SetSlotId, LiveSetRecommendation> =
        straightUpcoming.associate { set ->
            val targetOrdinal = setsByPosition.indexOfFirst { it.slotId == set.slotId }
            val projected =
                BodyweightRepProjection.expectedReps(
                    performedReps = geometry.capacityLogged.reps,
                    performedRir = geometry.capacityLogged.rir,
                    setIndex =
                        fatigueSetIndex(
                            performanceOrdinal = geometry.indexOriginOrdinal,
                            targetOrdinal = targetOrdinal,
                        ),
                    perLiftSlope = fatigue.slope,
                    perLiftIntercept = fatigue.intercept,
                    rirGoal = set.rirGoal,
                )
            // Bodyweight has no load to reduce, so the floor is honest capacity
            // (MIN_REPS from the projector) rather than BACKOFF_REP_FLOOR.
            val capped =
                if (set.repMax > 0) projected.coerceAtMost(set.repMax.coerceAtLeast(1)) else projected
            set.slotId to LiveSetRecommendation(weight = zeroWeight, reps = capped)
        }

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

    private fun fatigueSetIndex(performanceOrdinal: Int, targetOrdinal: Int): Int =
        (targetOrdinal - performanceOrdinal + 1).coerceAtLeast(1)

    private fun remainingCountAfterPerformanceAnchor(
        performanceOrdinal: Int,
        lastUpcomingOrdinal: Int,
    ): Int =
        (fatigueSetIndex(performanceOrdinal, lastUpcomingOrdinal) - 1).coerceAtLeast(0)

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
            myoRepKind = rendered.myoRepKind,
        )

    fun isStraightWorking(set: RenderedSet): Boolean =
        when (set.setType) {
            SetType.Standard -> true
            // Myo activation (template / top) is a normal working set for load selection.
            // Live minis are overlay-only and never straight working.
            SetType.MyoRep -> !isMyoMini(set)
            SetType.DropSet -> false
        }

    fun isMyoMini(set: RenderedSet): Boolean {
        if (set.setType != SetType.MyoRep) return false
        val overlayId = set.slotId as? SetSlotId.Overlay ?: return false
        return overlayId.instanceId.value.startsWith("mini-")
    }

    private fun isIntensifierSlot(set: RenderedSet): Boolean =
        isMyoMini(set) || set.setType == SetType.DropSet


}
