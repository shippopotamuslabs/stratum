package com.stratum.feature.workout.ui

import com.stratum.core.ui.component.formatIncrementTierSummary
import com.stratum.domain.lift.ApplyEquipmentIncrementResult
import com.stratum.domain.lift.EquipmentIncrementProposal
import com.stratum.domain.lift.EquipmentIncrementRemedy
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.requiresBlastRadius
import com.stratum.domain.lift.usefulAlternative

/**
 * Pure domain → presentation mapping for post-workout equipment-increment proposal cards.
 * Keeps [com.stratum.feature.workout.WorkoutCompleteViewModel] free of copy policy.
 */
internal object EquipmentProposalUiMapper {

    fun toCard(
        proposal: EquipmentIncrementProposal,
        isApplying: Boolean = false,
    ): EquipmentProposalCardUi {
        val alt = proposal.usefulAlternative()
        val copy = cardCopy(proposal, alt)
        return EquipmentProposalCardUi(
            id = proposal.id,
            title = copy.title,
            subtitle = copy.subtitle,
            tierSummary = formatTierSummary(proposal.proposedTiers),
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
        applyingIds: Set<String> = emptySet(),
    ): List<EquipmentProposalCardUi> =
        proposals.map { proposal ->
            toCard(proposal, isApplying = proposal.id in applyingIds)
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
