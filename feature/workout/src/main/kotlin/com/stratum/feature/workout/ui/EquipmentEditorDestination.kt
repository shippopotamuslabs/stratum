package com.stratum.feature.workout.ui

import kotlinx.serialization.Serializable

/**
 * Everything the router needs for one "Edit equipment" request: the catalog lift plus
 * its catalog equipment join. Threaded as one value through the sheet, actions, route,
 * and NavHost so no boundary unpacks and repacks the triple.
 *
 * [initialTiersLb] optionally seeds the editor with proposed tiers (storage convention:
 * magnitudes are pounds — see `IncrementTierDraftOps`). Empty means "edit current
 * catalog tiers". Carried as primitives (never domain types) so `:app` NavKeys can
 * embed this request without depending on domain.
 */
data class EquipmentEditRequest(
    val liftId: String,
    val target: EquipmentEditTarget,
    val initialTiersLb: List<ProposalTierArgs> = emptyList(),
    /**
     * Set when launched from a post-workout proposal's "Edit steps" action: lets the
     * editor dismiss the proposal on save and warn about steps that miss logged sets.
     * Null for all other entry points (manage equipment, lift info, live workout).
     */
    val proposalContext: ProposalEditContext? = null,
)

/**
 * Proposal provenance for an increments-editor session launched via "Edit steps".
 *
 * [proposalId] is the stable analysis fingerprint — the editor dismisses it (same as
 * "Not now") when the user saves, so a hand-corrected stack never resurrects the card.
 * [sampleWeightsLb] are the session's logged weights in storage pounds, used to warn
 * about bands whose steps don't divide evenly (warning only — saving stays allowed).
 */
@Serializable
data class ProposalEditContext(
    val proposalId: String,
    val sampleWeightsLb: List<Double>,
)

/**
 * One proposed increment band in storage pounds, suitable for `@Serializable` NavKeys.
 * Mapped to editor drafts (with display-unit conversion) by the increments editor.
 */
@Serializable
data class ProposalTierArgs(
    val lowerBoundLb: Double,
    val upperBoundLb: Double?,
    val incrementLb: Double,
)

/**
 * Which increments editor an "Edit equipment" request resolves to.
 *
 * A lift carrying its own per-lift override tiers edits the override; a lift inheriting
 * shared equipment defaults edits the shared equipment; a lift with neither (global
 * fallback bands) edits the per-lift override screen. Pure decision — navigation keys
 * are pushed by `:app` from this result.
 */
sealed interface EquipmentEditorDestination {
    data class LiftOverride(val liftId: String) : EquipmentEditorDestination
    data class SharedEquipment(
        val equipmentId: String,
        /** Catalog lift to reassign on Save As New (null when not launched from a lift). */
        val sourceLiftId: String? = null,
    ) : EquipmentEditorDestination
}

/**
 * Builds the live-workout "Edit equipment" request for one rendered lift card.
 * Same shape as the lift-options sheet entry point (see `WorkoutSessionSheets`).
 */
fun equipmentEditRequestFor(lift: RenderedLiftUi): EquipmentEditRequest =
    EquipmentEditRequest(
        liftId = lift.liftId,
        target =
            EquipmentEditTarget(
                equipmentId = lift.equipmentId,
                hasIncrementOverride = lift.hasIncrementOverride,
            ),
    )

fun resolveEquipmentEditorDestination(request: EquipmentEditRequest): EquipmentEditorDestination =
    if (request.target.hasIncrementOverride || request.target.equipmentId == null) {
        EquipmentEditorDestination.LiftOverride(request.liftId)
    } else {
        EquipmentEditorDestination.SharedEquipment(
            equipmentId = request.target.equipmentId,
            sourceLiftId = request.liftId,
        )
    }
