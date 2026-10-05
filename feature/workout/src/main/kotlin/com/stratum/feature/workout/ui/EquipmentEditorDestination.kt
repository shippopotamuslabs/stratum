package com.stratum.feature.workout.ui

import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.inferIncrementLattice
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.abs

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
     * Set when launched from a post-workout proposal's "Edit steps" action
     * ([ProposalEditContext.PostWorkout]) or the live "Adjust Equipment" pill
     * ([ProposalEditContext.Live]): lets the editor warn about steps that miss logged
     * sets. Null for all other entry points (manage equipment, lift info).
     */
    val proposalContext: ProposalEditContext? = null,
)

/**
 * Launch context for an increments-editor session opened from an equipment proposal
 * ("Edit steps") or the live "Adjust Equipment" pill. The two launches differ in
 * dismissal, so they are distinct types instead of a nullable mode flag:
 *
 * - [PostWorkout] carries the stable analysis fingerprint — the editor dismisses it
 *   (same as "Not now") when the user saves, so a hand-corrected stack never
 *   resurrects the card.
 * - [Live] is warnings-only: saving never touches proposal preferences, and pill
 *   quieting stays with the session tracker's X + tier-save handling.
 *
 * Both carry the session's logged weight magnitudes in storage pounds, used to warn
 * about bands whose steps don't divide evenly (warning only — saving stays allowed).
 */
@Serializable
sealed interface ProposalEditContext {
    @Serializable
    @SerialName("postWorkout")
    data class PostWorkout(
        val proposalId: String,
        val sampleWeightsLb: List<Double>,
    ) : ProposalEditContext

    @Serializable
    @SerialName("live")
    data class Live(
        val sampleWeightsLb: List<Double>,
    ) : ProposalEditContext
}

/** Logged sample magnitudes regardless of launch variant (both warn identically). */
val ProposalEditContext.samplesLb: List<Double>
    get() =
        when (this) {
            is ProposalEditContext.PostWorkout -> sampleWeightsLb
            is ProposalEditContext.Live -> sampleWeightsLb
        }

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
 * Per-lift increment overrides were removed (STR-82/84/85): every "Edit equipment"
 * request now resolves to the shared equipment editor. A lift carrying its own
 * equipment clone (e.g. "<lift name> Stack" from the migration) simply edits that
 * shared equipment; a lift with no equipment resolves with a null [equipmentId]
 * and the editor degrades to a seeded, non-saving state.
 */
sealed interface EquipmentEditorDestination {
    data class SharedEquipment(
        val equipmentId: String?,
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
            ),
    )

/**
 * Builds the live "Adjust Equipment" pill's editor request the way the complete screen
 * builds its "Edit steps" request: runs the deterministic inference over the sets logged
 * so far this session (canonical Lb weights against the lift's current effective tiers,
 * [LoadType]-aware), prefills the editor with the inferred tiers, and attaches the
 * sample magnitudes so misfitting bands warn (same non-blocking semantics).
 *
 * [base] carries the lift routing (same shape as [equipmentEditRequestFor]). Empty or
 * inconclusive inference (on-lattice, bodyweight, empty/zero-weight, mixed units) falls
 * back to [base] unchanged: no seed, no warnings. No sparse-seed suppression — one
 * genuine off-lattice set is enough to prefill live. The context is warnings-only
 * ([ProposalEditContext.Live]): saving never dismisses a fingerprint.
 */
fun liveEquipmentEditRequestFor(
    base: EquipmentEditRequest,
    currentTiers: List<IncrementTier>,
    loadType: LoadType,
    loggedWeights: List<Weight>,
): EquipmentEditRequest {
    val inferred =
        inferIncrementLattice(
            sampleWeights = loggedWeights,
            currentTiers = currentTiers,
            loadType = loadType,
        ) ?: return base
    return base.copy(
        initialTiersLb =
            inferred.tiers.map { tier ->
                ProposalTierArgs(
                    lowerBoundLb = tier.lowerBound.amount,
                    upperBoundLb = tier.upperBound?.amount,
                    incrementLb = tier.increment.amount,
                )
            },
        proposalContext =
            ProposalEditContext.Live(
                sampleWeightsLb = loggedWeights.map { abs(it.amount) }.filter { it > 0.0 },
            ),
    )
}

/**
 * Every "Edit equipment" request resolves to the shared equipment editor (STR-86):
 * per-lift override editors no longer exist.
 */
fun resolveEquipmentEditorDestination(request: EquipmentEditRequest): EquipmentEditorDestination =
    EquipmentEditorDestination.SharedEquipment(
        equipmentId = request.target.equipmentId,
        sourceLiftId = request.liftId,
    )
