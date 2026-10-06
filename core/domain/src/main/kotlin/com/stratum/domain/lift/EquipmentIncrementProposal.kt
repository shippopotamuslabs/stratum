package com.stratum.domain.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import kotlin.math.min

/**
 * A ranked, pre-filled fix when logged weights disagree with a lift's effective increment
 * lattice. Pure domain model — presentation maps this to UI cards without re-fetching catalogs.
 *
 * [id] is a stable fingerprint of `(equipmentId|none, sorted lift ids, inferred step)` for
 * DataStore deferral keys — not a display string.
 *
 * [liftNames] / [equipmentName] are snapshot display labels captured at analysis time so the
 * presentation layer does not re-load the full catalog solely for strings.
 */
data class EquipmentIncrementProposal(
    val id: String,
    val clusterEquipmentId: EquipmentId?,
    val liftIds: List<LiftId>,
    /** Display names parallel to [liftIds] (same order); empty string when unknown. */
    val liftNames: List<String>,
    val equipmentName: String?,
    val sampleWeights: List<Weight>,
    val proposedTiers: List<IncrementTier>,
    val primary: EquipmentIncrementRemedy,
    val alternatives: List<EquipmentIncrementRemedy>,
    /**
     * Whether the clustered equipment is user-owned custom (or there is no equipment id).
     *
     * - `equipmentId != null` → taken from rank input [EquipmentIncrementRankInput.isCustom]
     * - `equipmentId == null` → `true` (fork-only path; no seed amend possible)
     */
    val equipmentIsCustom: Boolean,
    /**
     * True when one-tap Accept of [primary] would rewrite seed (shared) equipment defaults
     * other lifts inherit — UI must show blast-radius confirmation before applying.
     *
     * Prefer [requiresBlastRadius] for primary **or** alternative remedies.
     */
    val requiresBlastRadiusConfirmation: Boolean,
    val affectedInheritingLiftCount: Int,
    /** Residual logged-set count used for cross-cluster ranking (higher = stronger signal). */
    val residualSetCount: Int = 0,
    /** Distinct residual weight amounts used for cross-cluster ranking. */
    val distinctResidualAmounts: Int = 0,
)

/**
 * Result of post-session equipment-increment analysis: ranking-pure proposals
 * plus the card-copy enrichment kept out of the canonical entity.
 *
 * [baselineMicroByProposalId] carries the baseline adder stops each proposal
 * assumed (equipment stops, else the first anomalous lift's), keyed by proposal
 * id. The UI mapping boundary reads it to render the card suffix; ranking
 * never sees it.
 */
data class EquipmentProposalAnalysis(
    val proposals: List<EquipmentIncrementProposal> = emptyList(),
    val baselineMicroByProposalId: Map<String, List<Weight>> = emptyMap(),
)

/**
 * Whether applying [remedy] rewrites non-custom (seed/shared) equipment defaults that other
 * lifts inherit — UI must confirm blast radius before writing.
 */
fun EquipmentIncrementProposal.requiresBlastRadius(
    remedy: EquipmentIncrementRemedy,
): Boolean =
    remedy is EquipmentIncrementRemedy.AmendEquipmentTiers && !equipmentIsCustom

/**
 * Useful secondary action for the card: Amend when primary is Fork, Fork when primary is Amend.
 */
fun EquipmentIncrementProposal.usefulAlternative(): EquipmentIncrementRemedy? =
    when (primary) {
        is EquipmentIncrementRemedy.ForkCustomEquipment ->
            alternatives
                .filterIsInstance<EquipmentIncrementRemedy.AmendEquipmentTiers>()
                .firstOrNull()
        is EquipmentIncrementRemedy.AmendEquipmentTiers ->
            alternatives
                .filterIsInstance<EquipmentIncrementRemedy.ForkCustomEquipment>()
                .firstOrNull()
    }

/**
 * Concrete write the user may accept for an [EquipmentIncrementProposal].
 *
 * No lineage: [ForkCustomEquipment] copies tiers only and does not point back at a seed parent.
 */
sealed interface EquipmentIncrementRemedy {
    data class AmendEquipmentTiers(
        val equipmentId: EquipmentId,
        val tiers: List<IncrementTier>,
    ) : EquipmentIncrementRemedy

    data class ForkCustomEquipment(
        val suggestedName: String,
        val tiers: List<IncrementTier>,
        val assignLiftIds: List<LiftId>,
    ) : EquipmentIncrementRemedy
}

/**
 * Inputs for pure remedy ranking (no I/O). Session analysis assembles this per equipment cluster.
 *
 * @param existingEquipmentNames catalog names used to unique-ify [ForkCustomEquipment]
 *   suggested names via [uniqueifyForkEquipmentName] / [EquipmentNameMatching.normalizeKey].
 */
data class EquipmentIncrementRankInput(
    val equipmentId: EquipmentId?,
    val equipmentName: String?,
    val isCustom: Boolean,
    val inheritingCount: Int,
    val anomalousLiftIds: List<LiftId>,
    val liftNamesById: Map<LiftId, String>,
    val sampleWeights: List<Weight>,
    val proposedTiers: List<IncrementTier>,
    val inferredStep: Double,
    val residualSetCount: Int = 0,
    val distinctResidualAmounts: Int = 0,
    val existingEquipmentNames: Collection<String> = emptyList(),
)

/**
 * Seed / shared-default cutoff: when more than [EQUIPMENT_INCREMENT_FORK_THRESHOLD_K] lifts
 * inherit equipment defaults, prefer forking a custom machine over quietly amending seed.
 */
const val EQUIPMENT_INCREMENT_FORK_THRESHOLD_K: Int = 3

/**
 * Stable fingerprint for proposal identity and deferral prefs.
 *
 * Format: `{equipmentId|none}|{sortedLiftIds csv}|{step}`
 */
fun equipmentIncrementProposalFingerprint(
    equipmentId: EquipmentId?,
    liftIds: Collection<LiftId>,
    inferredStep: Double,
): String {
    val eq = equipmentId?.value ?: "none"
    val lifts = liftIds.map { it.value }.sorted().joinToString(",")
    return "$eq|$lifts|${formatInferredStep(inferredStep)}"
}

/**
 * Prefill name for a forked custom equipment row.
 *
 * - Single anomalous lift → `"{liftName} stack"`
 * - Multi-lift with equipment name → `"{equipmentName} (gym)"`
 * - Otherwise → `"Custom stack"`
 */
fun suggestForkEquipmentName(
    anomalousLiftIds: List<LiftId>,
    liftNamesById: Map<LiftId, String>,
    equipmentName: String?,
): String {
    if (anomalousLiftIds.size == 1) {
        val liftName = liftNamesById[anomalousLiftIds.first()]?.trim().orEmpty()
        if (liftName.isNotEmpty()) return "$liftName stack"
    }
    val eq = equipmentName?.trim().orEmpty()
    if (eq.isNotEmpty()) return "$eq (gym)"
    return "Custom stack"
}

/**
 * Ensures a fork [suggestedName] does not collide with existing equipment display names.
 *
 * Collision is defined by [EquipmentNameMatching.normalizeKey] (not raw equality), matching
 * how free-text equipment resolution collapses punctuation/plurals. When the base name
 * collides, appends `" (2)"`, `" (3)"`, … until [normalizeKey] is unique among [existingNames].
 *
 * Deliberately unique-ifies rather than reassigning to the colliding custom row: Amend does
 * not reassign lifts, and silently amending a different machine is surprising.
 */
fun uniqueifyForkEquipmentName(
    suggestedName: String,
    existingNames: Collection<String>,
): String {
    val existingKeys =
        existingNames
            .map { EquipmentNameMatching.normalizeKey(it) }
            .filter { it.isNotEmpty() }
            .toHashSet()
    if (existingKeys.isEmpty()) return suggestedName

    val base = suggestedName.trim().ifEmpty { "Custom stack" }
    var candidate = base
    var suffix = 2
    while (EquipmentNameMatching.normalizeKey(candidate) in existingKeys) {
        candidate = "$base ($suffix)"
        suffix++
    }
    return candidate
}

/**
 * Cross-cluster ranking score for session cap (higher = stronger signal). Pure function of
 * residual evidence and blast-radius context — not insertion order.
 */
fun scoreEquipmentIncrementProposal(
    residualSetCount: Int,
    distinctResidualAmounts: Int,
    primaryIsFork: Boolean,
    affectedInheritingLiftCount: Int,
): Int {
    var score = residualSetCount * SCORE_RESIDUAL_SET + distinctResidualAmounts * SCORE_DISTINCT
    if (primaryIsFork) score += SCORE_FORK_PRIMARY
    score += min(affectedInheritingLiftCount, SCORE_BLAST_CAP)
    return score
}

/**
 * Deterministic remedy ranking for one anomaly cluster.
 *
 * - **Amend primary** when **every** anomalous lift inherits defaults **and** equipment is
 *   custom, or seed with `inheritingCount ≤ K`.
 * - **Fork primary** when seed with `inheritingCount > K`, no equipment id, or other
 *   non-amend cases. Per-lift overrides were removed (STR-82/84): clusters that would
 *   previously prefer a lift override now fork.
 * - Blast radius: use [requiresBlastRadius] for any remedy (primary or alternative).
 * - Fork names are unique-ified against [EquipmentIncrementRankInput.existingEquipmentNames].
 */
fun rankEquipmentIncrementProposal(input: EquipmentIncrementRankInput): EquipmentIncrementProposal {
    val liftIds = input.anomalousLiftIds.distinct()
    require(liftIds.isNotEmpty()) { "anomalousLiftIds must not be empty" }
    require(input.proposedTiers.isNotEmpty()) { "proposedTiers must not be empty" }

    // No equipment id → treat as custom-ish so fork-only never needs a seed-amend flag.
    val equipmentIsCustom = if (input.equipmentId != null) input.isCustom else true

    val forkName =
        uniqueifyForkEquipmentName(
            suggestedName =
                suggestForkEquipmentName(
                    anomalousLiftIds = liftIds,
                    liftNamesById = input.liftNamesById,
                    equipmentName = input.equipmentName,
                ),
            existingNames = input.existingEquipmentNames,
        )
    val fork =
        EquipmentIncrementRemedy.ForkCustomEquipment(
            suggestedName = forkName,
            tiers = input.proposedTiers,
            assignLiftIds = liftIds,
        )
    val amend: EquipmentIncrementRemedy.AmendEquipmentTiers? =
        input.equipmentId?.let { id ->
            EquipmentIncrementRemedy.AmendEquipmentTiers(
                equipmentId = id,
                tiers = input.proposedTiers,
            )
        }
    // Amend rewrites equipment defaults, which every lift inherits (STR-82/83 removed
    // per-lift overrides) — so Amend remediates the whole anomalous cluster.
    val preferAmend =
        amend != null &&
            (input.isCustom || input.inheritingCount <= EQUIPMENT_INCREMENT_FORK_THRESHOLD_K)

    val primary: EquipmentIncrementRemedy
    val alternatives: List<EquipmentIncrementRemedy>
    when {
        preferAmend -> {
            primary = checkNotNull(amend)
            alternatives = listOf(fork)
        }
        else -> {
            primary = fork
            alternatives = buildList {
                if (amend != null) add(amend)
            }
        }
    }

    val proposal =
        EquipmentIncrementProposal(
            id =
                equipmentIncrementProposalFingerprint(
                    equipmentId = input.equipmentId,
                    liftIds = liftIds,
                    inferredStep = input.inferredStep,
                ),
            clusterEquipmentId = input.equipmentId,
            liftIds = liftIds,
            liftNames = liftIds.map { id -> input.liftNamesById[id].orEmpty() },
            equipmentName = input.equipmentName,
            sampleWeights = input.sampleWeights,
            proposedTiers = input.proposedTiers,
            primary = primary,
            alternatives = alternatives,
            equipmentIsCustom = equipmentIsCustom,
            requiresBlastRadiusConfirmation = false, // filled below from primary
            affectedInheritingLiftCount = input.inheritingCount,
            residualSetCount = input.residualSetCount,
            distinctResidualAmounts = input.distinctResidualAmounts,
        )
    return proposal.copy(
        requiresBlastRadiusConfirmation = proposal.requiresBlastRadius(primary),
    )
}

internal fun formatInferredStep(step: Double): String {
    val asLong = step.toLong()
    return if (step == asLong.toDouble()) asLong.toString() else step.toString()
}

private const val SCORE_RESIDUAL_SET = 100
private const val SCORE_DISTINCT = 10
private const val SCORE_FORK_PRIMARY = 5
private const val SCORE_BLAST_CAP = 20
