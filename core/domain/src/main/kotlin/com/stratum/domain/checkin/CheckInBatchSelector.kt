package com.stratum.domain.checkin

import com.stratum.domain.lift.KinematicProfile
import com.stratum.domain.onboarding.MuscleGroup

/**
 * Provisional recommendation after per-lift decide **and** swap catalog enrichment (kinds final).
 * Batch one-lever selection runs only on this shape so demotions never outlive a failed swap.
 */
data class CheckInProvisionalRecommendation(
    val input: LiftCheckInInput,
    val action: CheckInAction,
    val rationale: CheckInRationale,
) {
    val kind: CheckInActionKind
        get() = action.toKind()
}

/**
 * Multi-lift coaching hygiene: at most one non-cut lever per primary muscle per check-in.
 *
 * Runs **after** swap enrichment so failed swaps (→ +1 or Maintain) re-compete with peers under
 * G4 ranking. Cuts are never demoted (user still accept/rejects). Contenders are greedily
 * accepted in priority order; losers become [Maintain] with [CheckInRationale.MuscleLeverAlreadyTaken].
 *
 * Empty [LiftCheckInInput.primaryMuscles] never accept a non-cut lever (no muscle identity).
 */
object CheckInBatchSelector {

    fun select(slots: List<CheckInProvisionalRecommendation>): List<CheckInProvisionalRecommendation> {
        if (slots.isEmpty()) return emptyList()

        val cuts = slots.filter { it.kind == CheckInActionKind.CutVolume }
        val maintains = slots.filter { it.kind == CheckInActionKind.Maintain }
        val contenders =
            slots
                .filter {
                    it.kind == CheckInActionKind.SwapLift ||
                        it.kind == CheckInActionKind.IncreaseVolume
                }
                .sortedWith(contenderComparator())

        val claimed = mutableSetOf<MuscleGroup>()
        val accepted = mutableListOf<CheckInProvisionalRecommendation>()
        val demoted = mutableListOf<CheckInProvisionalRecommendation>()

        for (slot in contenders) {
            val muscles = slot.input.primaryMuscles
            when {
                muscles.isEmpty() ->
                    demoted +=
                        slot.copy(
                            action = CheckInAction.Maintain,
                            rationale = CheckInRationale.NoClearChange,
                        )
                muscles.any { it in claimed } ->
                    demoted +=
                        slot.copy(
                            action = CheckInAction.Maintain,
                            rationale = CheckInRationale.MuscleLeverAlreadyTaken,
                        )
                else -> {
                    accepted += slot
                    claimed += muscles
                }
            }
        }

        return (cuts + accepted + demoted + maintains)
            .sortedWith(displayComparator())
    }

    /**
     * Greedy claim order: Swap before Increase; among increases apply volume-target rank keys
     * (Dropping, lower sets, isolation, higher tenure, name).
     */
    fun contenderComparator(): Comparator<CheckInProvisionalRecommendation> =
        compareBy<CheckInProvisionalRecommendation> { it.kind.sortPriority() }
            .thenByDescending { it.input.performanceTrend == PerformanceTrend.Dropping }
            .thenBy { it.input.currentSetCount }
            .thenBy { it.input.kinematicProfile.isolationRank }
            .thenByDescending { it.input.rotationTenureWeeks }
            .thenBy { it.input.liftName.lowercase() }

    /** Public display / output order (kind, dropping, name) — shared with engine comparator keys. */
    fun displayComparator(): Comparator<CheckInProvisionalRecommendation> =
        compareBy<CheckInProvisionalRecommendation> { it.kind.sortPriority() }
            .thenByDescending { it.input.performanceTrend == PerformanceTrend.Dropping }
            .thenBy { it.input.liftName.lowercase() }

    /** Isolation first (0), unknown next, compounds last. */
    private val KinematicProfile?.isolationRank: Int
        get() =
            when (this) {
                KinematicProfile.Isolation -> 0
                null -> 1
                else -> 2
            }
}
