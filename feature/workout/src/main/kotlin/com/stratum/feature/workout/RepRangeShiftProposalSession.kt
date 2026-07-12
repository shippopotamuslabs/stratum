package com.stratum.feature.workout

import com.stratum.domain.workout.RepRangeShiftProposal
import com.stratum.feature.workout.ui.RepRangeShiftCardUi
import com.stratum.feature.workout.ui.RepRangeShiftUiMapper

/**
 * In-memory session state for post-workout rep-range shift proposals.
 * No blast-radius path — Accept applies immediately.
 */
internal class RepRangeShiftProposalSession {

    data class Snapshot(
        val cards: List<RepRangeShiftCardUi> = emptyList(),
        val userMessage: String? = null,
    )

    private var proposals: Map<String, RepRangeShiftProposal> = emptyMap()
    private val applyingIds: MutableSet<String> = linkedSetOf()
    private var userMessage: String? = null

    fun load(proposals: List<RepRangeShiftProposal>): Snapshot {
        this.proposals = proposals.associateBy { it.id }
        applyingIds.clear()
        userMessage = null
        return snapshot()
    }

    fun proposal(id: String): RepRangeShiftProposal? = proposals[id]

    fun isApplying(proposalId: String): Boolean = proposalId in applyingIds

    fun beginApply(proposalId: String): RepRangeShiftProposal? {
        if (proposalId !in proposals) return null
        if (proposalId in applyingIds) return null
        // Serialize accepts: only one rep-range write in flight (program RMW safety).
        if (applyingIds.isNotEmpty()) return null
        applyingIds.add(proposalId)
        return proposals[proposalId]
    }

    fun completeSuccess(proposalId: String, message: String): Snapshot {
        applyingIds.remove(proposalId)
        proposals = proposals - proposalId
        userMessage = message
        return snapshot()
    }

    fun completeFailure(proposalId: String, message: String): Snapshot {
        applyingIds.remove(proposalId)
        userMessage = message
        return snapshot()
    }

    fun releaseApply(proposalId: String): Snapshot {
        applyingIds.remove(proposalId)
        return snapshot()
    }

    fun remove(proposalId: String): Snapshot? {
        // Do not drop a card while its apply is in flight (skip/not-now race).
        if (proposalId in applyingIds) return null
        proposals = proposals - proposalId
        return snapshot()
    }

    fun clearUserMessage(): Snapshot {
        userMessage = null
        return snapshot()
    }

    fun snapshot(): Snapshot =
        Snapshot(
            cards = RepRangeShiftUiMapper.toCards(proposals.values, applyingIds),
            userMessage = userMessage,
        )
}
