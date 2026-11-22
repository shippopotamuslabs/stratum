package com.stratum.domain.workout

import com.stratum.domain.importing.FatigueDecay
import com.stratum.domain.lift.Weight
import com.stratum.domain.workout.bodyweight.BodyweightRepProjection
import javax.inject.Inject

/**
 * Load-free straight-set live recommendations (STR-29 bodyweight, STR-326
 * assisted): reps project from performed reps+RIR via [BodyweightRepProjection]
 * while the carried weight stays fixed across the block (zero for bodyweight,
 * the performed assistance for assisted). Split from
 * [StraightSetRecommendationPolicy], which owns the weighted path; both share
 * the block geometry in StraightBlockGeometry.kt.
 */
class LoadFreeSetRecommendationPolicy @Inject constructor() {
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
    ): Map<SetSlotId, LiveSetRecommendation> =
        recommendLoadFree(
            lastPerformedSet = lastPerformedSet,
            lastPerformed = lastPerformed,
            setsByPosition = setsByPosition,
            anchorOrdinal = anchorOrdinal,
            straightUpcoming = straightUpcoming,
            fatigue = fatigue,
            priorRecommendations = priorRecommendations,
            carriedWeight = { anchor -> Weight(0.0, anchor.weight.unit) },
        )

    /**
     * Assisted straight-set recommendations (STR-326). The assistance amount is
     * carried across the block unchanged: the machine setting is the prescription,
     * and changing assistance mid-block is a programming decision the live overlay
     * does not make. Reps project exactly like bodyweight (load-free
     * [BodyweightRepProjection] on performed reps+RIR), because effective load is
     * bodyweight minus assistance and bodyweight is not measured. The carried
     * weight keeps its stored sign (assistance is stored negative; the UI displays
     * the magnitude).
     */
    fun recommendAssisted(
        lastPerformedSet: RenderedSet,
        lastPerformed: LoggedSetValue,
        setsByPosition: List<RenderedSet>,
        anchorOrdinal: Int,
        straightUpcoming: List<RenderedSet>,
        fatigue: FatigueDecay,
        priorRecommendations: Map<SetSlotId, LiveSetRecommendation>,
    ): Map<SetSlotId, LiveSetRecommendation> =
        recommendLoadFree(
            lastPerformedSet = lastPerformedSet,
            lastPerformed = lastPerformed,
            setsByPosition = setsByPosition,
            anchorOrdinal = anchorOrdinal,
            straightUpcoming = straightUpcoming,
            fatigue = fatigue,
            priorRecommendations = priorRecommendations,
            carriedWeight = { anchor -> anchor.weight },
        )

    /**
     * Shared load-free geometry for bodyweight and assisted: an
     * exact-prescription log keeps stable block geometry (no rep jumping);
     * anything else re-anchors on the just-performed set. [carriedWeight]
     * picks the weight each projection carries from the performance anchor.
     */
    private fun recommendLoadFree(
        lastPerformedSet: RenderedSet,
        lastPerformed: LoggedSetValue,
        setsByPosition: List<RenderedSet>,
        anchorOrdinal: Int,
        straightUpcoming: List<RenderedSet>,
        fatigue: FatigueDecay,
        priorRecommendations: Map<SetSlotId, LiveSetRecommendation>,
        carriedWeight: (LoggedSetValue) -> Weight,
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
                    ?: FatigueGeometry(
                        block.performanceLogged,
                        block.performanceOrdinal,
                        block.skipMinisInFatigueIndex,
                    )
            } else {
                FatigueGeometry(
                    block.performanceLogged,
                    block.performanceOrdinal,
                    block.skipMinisInFatigueIndex,
                )
            }
        return projectBodyweightSets(
            targets = straightUpcoming,
            setsByPosition = setsByPosition,
            baselineReps = geometry.capacityLogged.reps,
            baselineRir = geometry.capacityLogged.rir,
            originOrdinal = geometry.indexOriginOrdinal,
            fatigue = fatigue,
            carriedWeight = carriedWeight(block.performanceLogged),
            skipMinisInFatigueIndex = geometry.skipMinisInFatigueIndex,
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
        val straights = setsByPosition.filter { it.isStraightWorkingSet() && it.loggedValue == null }
        if (straights.size <= 1) return emptyMap()
        val first = straights.first()
        val anchorReps = first.recommendedReps.takeIf { it > 0 } ?: return emptyMap()
        val firstOrdinal = setsByPosition.indexOfFirst { it.slotId == first.slotId }
        return projectBodyweightSets(
            targets = straights.drop(1),
            setsByPosition = setsByPosition,
            baselineReps = anchorReps,
            baselineRir = first.rirGoal.coerceAtLeast(0),
            originOrdinal = firstOrdinal,
            fatigue = fatigue,
            carriedWeight = Weight(0.0, first.recommendedWeight.unit),
        )
    }

    /** Exact-prescription check for bodyweight: reps + RIR, no weight machinery. */

    private fun isExactBodyweightPrescription(
        lastPerformedSet: RenderedSet,
        lastPerformed: LoggedSetValue,
        priorRecommendations: Map<SetSlotId, LiveSetRecommendation>,
    ): Boolean {
        val priorForLogged = priorRecommendations[lastPerformedSet.slotId]
        if (priorForLogged != null) {
            // Primary: logged exactly what the live overlay showed (reps + RIR).
            return matchesLiveOverlay(priorForLogged, lastPerformed, lastPerformedSet)
        }
        // Fallback: no prior live row (e.g. first set) — match the shown prescription.
        return matchesShownPrescription(
            set = lastPerformedSet,
            logged = lastPerformed,
            priorRecommendations = priorRecommendations,
        )
    }

    private fun matchesLiveOverlay(
        prior: LiveSetRecommendation,
        lastPerformed: LoggedSetValue,
        lastPerformedSet: RenderedSet,
    ): Boolean {
        if (prior.reps <= 0) return false
        if (lastPerformed.reps != prior.reps) return false
        val rir = lastPerformed.rir ?: return false
        return rir == lastPerformedSet.rirGoal
    }

    /**
     * Shared load-free projection loop: for each target set, projects reps from the
     * failure-rep baseline via [BodyweightRepProjection], applies the repMax cap, and
     * wraps in a [LiveSetRecommendation] carrying [carriedWeight] (zero for
     * bodyweight, the performed assistance for assisted). Callers differ only in
     * where the baseline comes from (snapshot prescription vs. performed set).
     */
    private fun projectBodyweightSets(
        targets: List<RenderedSet>,
        setsByPosition: List<RenderedSet>,
        baselineReps: Int,
        baselineRir: Int?,
        originOrdinal: Int,
        fatigue: FatigueDecay,
        carriedWeight: Weight,
        skipMinisInFatigueIndex: Boolean = false,
    ): Map<SetSlotId, LiveSetRecommendation> =
        targets.associate { set ->
            val targetOrdinal = setsByPosition.indexOfFirst { it.slotId == set.slotId }
            val projected =
                BodyweightRepProjection.expectedReps(
                    performedReps = baselineReps,
                    performedRir = baselineRir,
                    setIndex =
                        fatigueSetIndex(
                            setsByPosition = setsByPosition,
                            performanceOrdinal = originOrdinal,
                            targetOrdinal = targetOrdinal,
                            skipMinisInFatigueIndex = skipMinisInFatigueIndex,
                        ),
                    perLiftSlope = fatigue.slope,
                    perLiftIntercept = fatigue.intercept,
                    rirGoal = set.rirGoal,
                )
            // Bodyweight has no load to reduce, so the floor is honest capacity
            // (MIN_REPS from the projector) rather than BACKOFF_REP_FLOOR.
            val capped =
                if (set.repMax > 0) projected.coerceAtMost(set.repMax.coerceAtLeast(1)) else projected
            set.slotId to LiveSetRecommendation(weight = carriedWeight, reps = capped)
        }
}
