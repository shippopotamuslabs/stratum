package com.stratum.feature.workout.ui

import com.stratum.core.ui.component.formatIncrementAmount
import com.stratum.core.ui.component.formatIncrementTierSummary
import com.stratum.domain.lift.ApplyEquipmentIncrementResult
import com.stratum.domain.lift.EquipmentIncrementProposal
import com.stratum.domain.lift.EquipmentIncrementRemedy
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.requiresBlastRadius
import com.stratum.domain.lift.usefulAlternative

/**
 * Pure domain → presentation mapping for post-workout equipment-increment proposal cards.
 * Keeps [com.stratum.feature.workout.WorkoutCompleteViewModel] free of copy policy.
 */
internal object EquipmentProposalUiMapper {

    /**
     * @param baselineMicro the analysis-assumed adder stops for this proposal,
     * carried alongside (not on) the domain entity; explicitly composed here so
     * the canonical proposal stays ranking-pure.
     */
    fun toCard(
        proposal: EquipmentIncrementProposal,
        baselineMicro: List<Weight>,
        isApplying: Boolean = false,
    ): EquipmentProposalCardUi {
        val alt = proposal.usefulAlternative()
        val copy = cardCopy(proposal, alt)
        return EquipmentProposalCardUi(
            id = proposal.id,
            title = copy.title,
            subtitle = copy.subtitle,
            tierSummary =
                formatTierSummary(proposal.proposedTiers) +
                    formatAdderSuffix(baselineMicro),
            liftNames =
                proposal.liftNames
                    .filter { it.isNotBlank() }
                    .joinToString(", "),
            primaryButtonLabel = copy.primaryLabel,
            requiresConfirm = proposal.requiresBlastRadius(proposal.primary),
            blastRadiusCount = proposal.affectedInheritingLiftCount,
            alternativeButtonLabel = copy.altLabel,
            isApplying = isApplying,
        )
    }

    fun toCards(
        proposals: Collection<EquipmentIncrementProposal>,
        baselineMicroByProposalId: Map<String, List<Weight>>,
        applyingIds: Set<String> = emptySet(),
    ): List<EquipmentProposalCardUi> =
        proposals.map { proposal ->
            toCard(
                proposal,
                baselineMicro = baselineMicroByProposalId[proposal.id].orEmpty(),
                isApplying = proposal.id in applyingIds,
            )
        }

    /**
     * Builds the "Edit steps" navigation request for [proposal]: the editor target matching
     * the primary remedy's write destination, seeded with the proposed tiers so the user
     * corrects the inference guess instead of accepting or ignoring it.
     *
     * - Amend → shared equipment editor (same write Accept would perform).
     * - Fork / LiftOverride → per-lift override editor (the fork target does not exist yet,
     *   so manual correction lands on the first affected lift's override).
     */
    fun toEditRequest(proposal: EquipmentIncrementProposal): EquipmentEditRequest {
        val initialTiers =
            proposal.proposedTiers.map { tier ->
                ProposalTierArgs(
                    lowerBoundLb = tier.lowerBound.amount,
                    upperBoundLb = tier.upperBound?.amount,
                    incrementLb = tier.increment.amount,
                )
            }
        val context =
            ProposalEditContext.PostWorkout(
                proposalId = proposal.id,
                sampleWeightsLb = proposal.sampleWeights.map { it.amount },
            )
        return when (val remedy = proposal.primary) {
            is EquipmentIncrementRemedy.AmendEquipmentTiers ->
                EquipmentEditRequest(
                    liftId = proposal.liftIds.firstOrNull()?.value.orEmpty(),
                    target =
                        EquipmentEditTarget(
                            equipmentId = remedy.equipmentId.value,
                            hasIncrementOverride = false,
                        ),
                    initialTiersLb = initialTiers,
                    proposalContext = context,
                )
            is EquipmentIncrementRemedy.ForkCustomEquipment -> {
                val liftId =
                    (
                        remedy.assignLiftIds.firstOrNull()
                            ?: proposal.liftIds.firstOrNull()
                    )?.value
                val clusterEquipmentId = proposal.clusterEquipmentId
                if (liftId == null && clusterEquipmentId != null) {
                    // No lift to anchor an override editor: correct the shared stack
                    // instead (blank liftId normalizes to a null source downstream).
                    EquipmentEditRequest(
                        liftId = "",
                        target =
                            EquipmentEditTarget(
                                equipmentId = clusterEquipmentId.value,
                                hasIncrementOverride = false,
                            ),
                        initialTiersLb = initialTiers,
                        proposalContext = context,
                    )
                } else {
                    EquipmentEditRequest(
                        liftId = liftId.orEmpty(),
                        target =
                            EquipmentEditTarget(
                                equipmentId = proposal.clusterEquipmentId?.value,
                                hasIncrementOverride = true,
                            ),
                        initialTiersLb = initialTiers,
                        proposalContext = context,
                    )
                }
            }
            is EquipmentIncrementRemedy.LiftOverride ->
                EquipmentEditRequest(
                    liftId = remedy.liftId.value,
                    target =
                        EquipmentEditTarget(
                            equipmentId = proposal.clusterEquipmentId?.value,
                            hasIncrementOverride = true,
                        ),
                    initialTiersLb = initialTiers,
                    proposalContext = context,
                )
        }
    }

    fun blastRadiusConfirm(
        proposal: EquipmentIncrementProposal,
    ): BlastRadiusConfirmUi {
        val count = proposal.affectedInheritingLiftCount
        return BlastRadiusConfirmUi(
            proposalId = proposal.id,
            title = MSG_BLAST_TITLE,
            body = "This'll alter defaults for $count lifts.",
            confirmLabel = MSG_BLAST_CONFIRM,
        )
    }

    fun successMessage(
        outcome: ApplyEquipmentIncrementResult,
        remedy: EquipmentIncrementRemedy,
    ): String =
        when (outcome) {
            is ApplyEquipmentIncrementResult.Forked -> {
                val name =
                    (remedy as? EquipmentIncrementRemedy.ForkCustomEquipment)
                        ?.suggestedName
                        ?.trim()
                        .orEmpty()
                if (name.isNotEmpty()) "Created $name." else "Created custom equipment."
            }
            is ApplyEquipmentIncrementResult.Amended -> "Updated equipment steps."
            is ApplyEquipmentIncrementResult.LiftOverridden -> "Updated lift steps."
        }

    /** Bands-only tier summary; adder copy composes via [formatAdderSuffix]. */
    fun formatTierSummary(tiers: List<IncrementTier>): String =
        formatIncrementTierSummary(
            tiers.map { tier ->
                Triple(
                    tier.lowerBound.amount,
                    tier.upperBound?.amount,
                    tier.increment.amount,
                )
            },
        )

    /**
     * Adder suffix for a tier summary; empty when there is no knob so callers
     * compose `formatTierSummary(tiers) + formatAdderSuffix(stops)` without
     * knowing an empty-means-bands-only contract.
     */
    fun formatAdderSuffix(micro: List<Weight>): String {
        if (micro.isEmpty()) return ""
        val stops = micro.joinToString("/") { formatIncrementAmount(it.amount) }
        return " + adder $stops"
    }

    private fun cardCopy(
        proposal: EquipmentIncrementProposal,
        alt: EquipmentIncrementRemedy?,
    ): CardCopy =
        when (val remedy = proposal.primary) {
            is EquipmentIncrementRemedy.ForkCustomEquipment ->
                CardCopy(
                    title = "Weight steps look different",
                    subtitle =
                        "Create \"${remedy.suggestedName}\" with matching steps" +
                            (proposal.equipmentName?.let { " (instead of $it)" } ?: "") +
                            ".",
                    primaryLabel = MSG_ACCEPT,
                    altLabel =
                        if (alt is EquipmentIncrementRemedy.AmendEquipmentTiers) {
                            MSG_ALT_AMEND
                        } else {
                            null
                        },
                )
            is EquipmentIncrementRemedy.AmendEquipmentTiers ->
                CardCopy(
                    title =
                        if (proposal.equipmentIsCustom) {
                            "Update your equipment steps"
                        } else {
                            "Update shared equipment steps"
                        },
                    subtitle =
                        proposal.equipmentName?.let {
                            "Adjust \"$it\" defaults to match this session."
                        } ?: "Adjust equipment defaults to match this session.",
                    primaryLabel = MSG_ACCEPT,
                    altLabel =
                        if (alt is EquipmentIncrementRemedy.ForkCustomEquipment) {
                            MSG_ALT_FORK
                        } else {
                            null
                        },
                )
            is EquipmentIncrementRemedy.LiftOverride ->
                CardCopy(
                    title = "Adjust steps for one lift",
                    subtitle = "Apply matching steps only to this lift.",
                    primaryLabel = MSG_ACCEPT,
                    altLabel = null,
                )
        }

    const val MSG_APPLY_FAILED = "Couldn't apply equipment update."

    private const val MSG_ACCEPT = "Accept"
    private const val MSG_ALT_FORK = "Create custom instead"
    private const val MSG_ALT_AMEND = "Update shared instead"
    private const val MSG_BLAST_TITLE = "Update shared steps?"
    private const val MSG_BLAST_CONFIRM = "Update steps"

    private data class CardCopy(
        val title: String,
        val subtitle: String,
        val primaryLabel: String,
        val altLabel: String?,
    )
}
