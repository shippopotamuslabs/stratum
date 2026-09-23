package com.stratum.liftconfig

import com.stratum.domain.lift.DismissEquipmentProposalUseCase
import com.stratum.feature.workout.ui.ProposalEditContext
import com.stratum.feature.workout.ui.ProposalTierArgs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Proposal launch input shared by the lift and equipment increments editors. */
data class ProposalInput(
    /** Prefill bands in storage pounds; empty means "edit current catalog tiers". */
    val initialTiersLb: List<ProposalTierArgs>,
    /**
     * Proposal fingerprint (dismissed on save) plus logged sample weights for the
     * non-blocking step warning. Null outside "Edit steps" launches.
     */
    val context: ProposalEditContext?,
)

/**
 * Shared proposal-prefill session for the lift and equipment increments editors, so the
 * prefill fallback, sample-warning input, and dismiss-on-save behave identically in both
 * (one implementation, two ViewModels). Plain holder — no Android dependencies.
 */
class ProposalEditorSession {
    private val _input = MutableStateFlow<ProposalInput?>(null)
    val input: StateFlow<ProposalInput?> = _input.asStateFlow()

    /**
     * Stores the launch input; returns true when it changed (callers reset drafts and
     * save flags only then). Empty tiers with no context normalize to null — the legacy
     * edit-current-tiers behavior.
     */
    fun resolveInput(
        initialTiersLb: List<ProposalTierArgs>,
        proposalContext: ProposalEditContext?,
    ): Boolean {
        val next =
            if (initialTiersLb.isEmpty() && proposalContext == null) {
                null
            } else {
                ProposalInput(initialTiersLb, proposalContext)
            }
        if (_input.value == next) return false
        _input.value = next
        return true
    }

    /**
     * Dismisses the launch proposal's fingerprint. Call only from successful saves —
     * cancellation paths must never call this (the card must survive a back-out).
     */
    suspend fun dismissIfPresent(dismiss: DismissEquipmentProposalUseCase) {
        _input.value?.context?.let { dismiss(it.proposalId) }
    }
}
