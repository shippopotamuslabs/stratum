package com.stratum.feature.workout.ui

import com.stratum.domain.workout.RepRangeShiftProposal
import com.stratum.domain.workout.fatigue.LAST_SET_REP_FLOOR

/**
 * Pure domain → presentation mapping for post-workout rep-range shift cards.
 *
 * User-visible templates that need localization are applied in the composable via
 * [com.stratum.feature.workout.R.string]; this mapper only formats domain numbers and
 * carries lift names / band pairs for string resources.
 */
internal object RepRangeShiftUiMapper {

    const val MSG_APPLY_FAILED = "Couldn't update rep range."

    fun toCard(
        proposal: RepRangeShiftProposal,
        isApplying: Boolean = false,
    ): RepRangeShiftCardUi {
        val current = formatBand(proposal.currentRepMin, proposal.currentRepMax)
        val proposed = formatBand(proposal.proposedRepMin, proposal.proposedRepMax)
        return RepRangeShiftCardUi(
            id = proposal.id,
            liftName = proposal.liftName,
            currentBand = current,
            proposedBand = proposed,
            lastSetFloor = LAST_SET_REP_FLOOR,
            bandSummary = "$current → $proposed",
            isApplying = isApplying,
        )
    }

    fun toCards(
        proposals: Collection<RepRangeShiftProposal>,
        applyingIds: Set<String> = emptySet(),
    ): List<RepRangeShiftCardUi> =
        proposals.map { proposal ->
            toCard(proposal, isApplying = proposal.id in applyingIds)
        }

    fun successMessage(proposal: RepRangeShiftProposal): String {
        val proposed = formatBand(proposal.proposedRepMin, proposal.proposedRepMax)
        return "Updated ${proposal.liftName} to $proposed."
    }

    fun formatBand(min: Int, max: Int): String = "$min–$max"
}
