package com.stratum.feature.workout

import com.stratum.domain.lift.EquipmentIncrementProposal
import com.stratum.domain.lift.EquipmentIncrementRemedy
import com.stratum.domain.lift.requiresBlastRadius
import com.stratum.domain.lift.usefulAlternative
import com.stratum.feature.workout.ui.BlastRadiusConfirmUi
import com.stratum.feature.workout.ui.EquipmentProposalCardUi
import com.stratum.feature.workout.ui.EquipmentProposalUiMapper

/**
 * In-memory session state for post-workout equipment proposals on the complete screen.
 *
 * Owns the domain map, blast-radius staging, and per-proposal apply in-flight set so
 * [WorkoutCompleteViewModel] stays an orchestrator. Not a ViewModel — no coroutines / DI.
 */
internal class EquipmentProposalSession {

    data class Snapshot(
        val cards: List<EquipmentProposalCardUi> = emptyList(),
        val blastRadiusConfirm: BlastRadiusConfirmUi? = null,
        val userMessage: String? = null,
    )

    private var proposals: Map<String, EquipmentIncrementProposal> = emptyMap()
    private var pendingConfirmRemedy: EquipmentIncrementRemedy? = null
    private var blastRadiusConfirm: BlastRadiusConfirmUi? = null
    private val applyingIds: MutableSet<String> = linkedSetOf()
    private var userMessage: String? = null

    fun load(proposals: List<EquipmentIncrementProposal>): Snapshot {
        this.proposals = proposals.associateBy { it.id }
        pendingConfirmRemedy = null
        blastRadiusConfirm = null
        applyingIds.clear()
        userMessage = null
        return snapshot()
    }

    fun proposal(id: String): EquipmentIncrementProposal? = proposals[id]

    fun pendingRemedy(): EquipmentIncrementRemedy? = pendingConfirmRemedy

    /**
     * Stage blast-radius confirm for [remedy] on [proposalId]. Clears any prior confirm
     * session so only one dialog is active.
     */
    fun stageConfirm(
        proposalId: String,
        remedy: EquipmentIncrementRemedy,
    ): Snapshot? {
        val proposal = proposals[proposalId] ?: return null
        pendingConfirmRemedy = remedy
        blastRadiusConfirm = EquipmentProposalUiMapper.blastRadiusConfirm(proposal)
        return snapshot()
    }

    fun dismissConfirm(): Snapshot {
        pendingConfirmRemedy = null
        blastRadiusConfirm = null
        return snapshot()
    }

    /**
     * Clears confirm UI when the user begins applying any proposal (including a different
     * card) so the dialog cannot desync from [pendingConfirmRemedy].
     */
    private fun clearConfirmSession() {
        pendingConfirmRemedy = null
        blastRadiusConfirm = null
    }

    /**
     * Marks [proposalId] as applying. Returns the proposal when the apply may proceed, or
     * null when missing / already in flight for this id.
     *
     * Does **not** remove the card — removal happens only on [completeSuccess].
     */
    fun beginApply(proposalId: String): EquipmentIncrementProposal? {
        if (proposalId !in proposals) return null
        if (proposalId in applyingIds) return null
        applyingIds.add(proposalId)
        clearConfirmSession()
        return proposals[proposalId]
    }

    fun completeSuccess(proposalId: String, message: String): Snapshot {
        applyingIds.remove(proposalId)
        proposals = proposals - proposalId
        // If confirm was for this id it is already cleared; keep session clean.
        if (blastRadiusConfirm?.proposalId == proposalId) {
            clearConfirmSession()
        }
        userMessage = message
        return snapshot()
    }

    /** Keeps the proposal card so the user can retry after a transient failure. */
    fun completeFailure(proposalId: String, message: String): Snapshot {
        applyingIds.remove(proposalId)
        userMessage = message
        return snapshot()
    }

    /** Cancellation / unexpected exit: release in-flight without dropping the card. */
    fun releaseApply(proposalId: String): Snapshot {
        applyingIds.remove(proposalId)
        return snapshot()
    }

    fun remove(proposalId: String): Snapshot {
        proposals = proposals - proposalId
        applyingIds.remove(proposalId)
        if (blastRadiusConfirm?.proposalId == proposalId) {
            clearConfirmSession()
        }
        return snapshot()
    }

    fun clearUserMessage(): Snapshot {
        userMessage = null
        return snapshot()
    }

    fun acceptPrimary(proposalId: String): AcceptAction {
        val proposal = proposals[proposalId] ?: return AcceptAction.Ignored
        if (proposalId in applyingIds) return AcceptAction.Ignored
        val remedy = proposal.primary
        return if (proposal.requiresBlastRadius(remedy)) {
            AcceptAction.NeedsConfirm(proposalId, remedy)
        } else {
            AcceptAction.ApplyNow(proposalId, remedy)
        }
    }

    fun acceptAlternative(proposalId: String): AcceptAction {
        val proposal = proposals[proposalId] ?: return AcceptAction.Ignored
        if (proposalId in applyingIds) return AcceptAction.Ignored
        val remedy = proposal.usefulAlternative() ?: return AcceptAction.Ignored
        return if (proposal.requiresBlastRadius(remedy)) {
            AcceptAction.NeedsConfirm(proposalId, remedy)
        } else {
            AcceptAction.ApplyNow(proposalId, remedy)
        }
    }

    fun confirmAccept(): AcceptAction {
        val confirm = blastRadiusConfirm ?: return AcceptAction.Ignored
        val remedy = pendingConfirmRemedy ?: return AcceptAction.Ignored
        val proposalId = confirm.proposalId
        if (proposalId !in proposals) return AcceptAction.Ignored
        if (proposalId in applyingIds) return AcceptAction.Ignored
        // Clear staged confirm before apply so a second Confirm cannot re-enter.
        clearConfirmSession()
        return AcceptAction.ApplyNow(proposalId, remedy)
    }

    fun snapshot(): Snapshot =
        Snapshot(
            cards = EquipmentProposalUiMapper.toCards(proposals.values, applyingIds),
            blastRadiusConfirm = blastRadiusConfirm,
            userMessage = userMessage,
        )

    sealed interface AcceptAction {
        data object Ignored : AcceptAction

        data class NeedsConfirm(
            val proposalId: String,
            val remedy: EquipmentIncrementRemedy,
        ) : AcceptAction

        data class ApplyNow(
            val proposalId: String,
            val remedy: EquipmentIncrementRemedy,
        ) : AcceptAction
    }
}
