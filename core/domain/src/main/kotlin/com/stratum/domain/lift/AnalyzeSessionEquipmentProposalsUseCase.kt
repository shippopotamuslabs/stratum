package com.stratum.domain.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingLogRepository
import com.stratum.domain.training.TrainingSessionOrigin
import com.stratum.domain.training.TrainingSessionStatus
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.training.isEligibleCompletedLiveSession
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.flow.first

/**
 * Max proposal cards per completed session (product decision).
 */
const val MAX_SESSION_EQUIPMENT_PROPOSALS: Int = 3

/**
 * Typo guard: a weight more than this multiple of the max of *other* sets on the same lift
 * in the session is treated as a mistype (e.g. 625 vs 62.5), not a stack signal.
 */
const val EQUIPMENT_INCREMENT_TYPO_MULTIPLIER: Double = 3.0

/**
 * Analyzes a completed **live** session for off-lattice logged weights and ranks up to
 * [MAX_SESSION_EQUIPMENT_PROPOSALS] equipment-increment proposals.
 *
 * Session-scoped, offline, deterministic (no LLM). Filters dismissed fingerprints via
 * [EquipmentProposalPreferences]. Import sessions and incomplete sessions emit nothing.
 * Failures are logged and surface as an empty list (complete screen still shows the summary).
 */
class AnalyzeSessionEquipmentProposalsUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val liftRepository: LiftRepository,
    private val equipmentRepository: EquipmentRepository,
    private val preferences: EquipmentProposalPreferences,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(sessionId: TrainingSessionId): List<EquipmentIncrementProposal> =
        runCatchingLogged(
            loggingPort = loggingPort,
            tag = TAG,
            message = "Failed to analyze session equipment proposals for ${sessionId.value}",
        ) {
            analyze(sessionId)
        }.getOrElse { emptyList() }

    private suspend fun analyze(sessionId: TrainingSessionId): List<EquipmentIncrementProposal> {
        if (!preferences.isEnabled()) return emptyList()
        val session = trainingLogRepository.getSession(sessionId)
        val sets =
            if (
                session != null &&
                isEligibleCompletedLiveSession(session.status, session.origin)
            ) {
                trainingLogRepository.getSetsForSession(sessionId)
            } else {
                emptyList()
            }
        if (sets.isEmpty()) return emptyList()

        val lifts = liftRepository.observeLifts().first()
        val equipment = equipmentRepository.observeEquipment().first()
        val liftsById = lifts.associateBy { it.id }
        val equipmentById = equipment.associateBy { it.id }
        val existingEquipmentNames = equipment.map { it.name }

        val anomalies = collectSessionAnomalies(sets, liftsById)
        if (anomalies.isEmpty()) return emptyList()

        val clusters = anomalies.groupBy { it.clusterKey }
        val built =
            clusters.values.mapNotNull { clusterAnomalies ->
                buildClusterProposal(
                    clusterAnomalies = clusterAnomalies,
                    liftsById = liftsById,
                    equipmentById = equipmentById,
                    allLifts = lifts,
                    existingEquipmentNames = existingEquipmentNames,
                )
            }

        // Rank by residual signal strength, then filter dismissals, then cap — never insertion order.
        val ranked =
            built.sortedByDescending { proposal ->
                scoreEquipmentIncrementProposal(
                    residualSetCount = proposal.residualSetCount,
                    distinctResidualAmounts = proposal.distinctResidualAmounts,
                    primaryIsFork =
                        proposal.primary is EquipmentIncrementRemedy.ForkCustomEquipment,
                    affectedInheritingLiftCount = proposal.affectedInheritingLiftCount,
                )
            }
        val proposals = ArrayList<EquipmentIncrementProposal>(MAX_SESSION_EQUIPMENT_PROPOSALS)
        for (proposal in ranked) {
            if (proposals.size >= MAX_SESSION_EQUIPMENT_PROPOSALS) break
            if (!preferences.isDismissed(proposal.id)) {
                proposals.add(proposal)
            }
        }
        return proposals
    }

    private fun buildClusterProposal(
        clusterAnomalies: List<LiftSessionAnomaly>,
        liftsById: Map<LiftId, Lift>,
        equipmentById: Map<EquipmentId, Equipment>,
        allLifts: List<Lift>,
        existingEquipmentNames: List<String>,
    ): EquipmentIncrementProposal? {
        val first = clusterAnomalies.first()
        val equipmentId = first.equipmentId
        val equipment = equipmentId?.let { equipmentById[it] }
        val isCustom = equipment?.isCustom == true
        // Unassigned cluster: no seed amend path.
        val effectiveIsCustom = if (equipmentId == null) true else isCustom

        val sampleWeights =
            clusterAnomalies
                .flatMap { it.usableWeights }
                .distinct()
        val residualWeights =
            clusterAnomalies
                .flatMap { it.residualWeights }
                .distinct()
        if (residualWeights.isEmpty()) return null

        // Current equipment defaults for inference baseline (not lift overrides).
        // Empty non-null tier lists are treated as missing so we fall back to effective tiers.
        // Micro mirrors the same baseline so adder-exact samples stay silent here too.
        val currentTiers = resolveBaseline(equipment?.incrementTiers, first.effectiveTiers)
        val currentMicro = resolveBaseline(equipment?.microIncrements, first.micro)

        val loadType =
            clusterAnomalies
                .mapNotNull { liftsById[it.liftId]?.loadType }
                .firstOrNull()
                ?: LoadType.Weighted

        val inferred =
            inferIncrementLattice(
                sampleWeights = sampleWeights,
                currentTiers = currentTiers,
                loadType = loadType,
                micro = currentMicro,
            ) ?: return null

        val anomalousLiftIds = clusterAnomalies.map { it.liftId }.distinct()
        val residualSetCount = clusterAnomalies.sumOf { it.residualSetCount }
        val distinctResidualAmounts =
            residualWeights.map { abs(it.amount) }.distinct().size

        val inheritingCount =
            if (equipmentId != null) {
                filterEquipmentAffectedLifts(equipmentId, allLifts).count
            } else {
                anomalousLiftIds.size
            }
        // One stray off-lattice set on seed Cables/machines (e.g. 33.3 among 35/35) is more
        // often a typo than a new stack — require repeated residual evidence before proposing.
        if (
            shouldSuppressSparseSeedSignal(
                effectiveIsCustom = effectiveIsCustom,
                inheritingCount = inheritingCount,
                anomalousLiftCount = anomalousLiftIds.size,
                residualSetCount = residualSetCount,
                distinctResidualAmounts = distinctResidualAmounts,
            )
        ) {
            return null
        }

        val liftNamesById =
            anomalousLiftIds.associateWith { id -> liftsById[id]?.name.orEmpty() }
        val inheritsByLift =
            anomalousLiftIds.map { id ->
                liftsById[id]?.usesEquipmentDefaultIncrements == true
            }
        val anyAnomalousInheritsDefaults = inheritsByLift.any { it }
        val allAnomalousInheritsDefaults = inheritsByLift.isNotEmpty() && inheritsByLift.all { it }

        return rankEquipmentIncrementProposal(
            EquipmentIncrementRankInput(
                equipmentId = equipmentId,
                equipmentName = equipment?.name,
                isCustom = effectiveIsCustom,
                inheritingCount = inheritingCount,
                anomalousLiftIds = anomalousLiftIds,
                liftNamesById = liftNamesById,
                sampleWeights = sampleWeights,
                proposedTiers = inferred.tiers,
                inferredStep = inferred.step,
                allAnomalousInheritsDefaults = allAnomalousInheritsDefaults,
                anyAnomalousInheritsDefaults = anyAnomalousInheritsDefaults,
                residualSetCount = residualSetCount,
                distinctResidualAmounts = distinctResidualAmounts,
                existingEquipmentNames = existingEquipmentNames,
            ),
        )
    }

    /**
     * Empty-as-missing baseline: null or empty equipment values fall back to the
     * lift's effective values, keeping explicit null handling at this single point.
     */
    private fun <T> resolveBaseline(equipmentValues: List<T>?, fallback: List<T>): List<T> =
        equipmentValues?.takeIf { it.isNotEmpty() } ?: fallback

    private companion object {
        const val TAG = "AnalyzeSessionEquipmentProposals"
    }
}

// isEligibleCompletedLiveSession lives in training/CompletedLiveSessionEligibility.kt

/**
 * Suppresses weak single-lift signals on high-blast-radius seed equipment.
 *
 * Example kept quiet: one mistyped 33.3 next to normal 35 / 35 on seed Cables used by many
 * lifts. Repeated residual sets (e.g. 62.5, 62.5, 67.5) or ≥2 distinct residual amounts still
 * surface. Custom equipment is never suppressed here — the user already owns that machine.
 */
internal fun shouldSuppressSparseSeedSignal(
    effectiveIsCustom: Boolean,
    inheritingCount: Int,
    anomalousLiftCount: Int,
    residualSetCount: Int,
    distinctResidualAmounts: Int,
): Boolean {
    if (effectiveIsCustom) return false
    if (inheritingCount <= EQUIPMENT_INCREMENT_FORK_THRESHOLD_K) return false
    if (anomalousLiftCount != 1) return false
    if (residualSetCount >= 2) return false
    return distinctResidualAmounts < 2
}

/**
 * Per-lift anomaly extracted from a session (internal analysis intermediate).
 */
internal data class LiftSessionAnomaly(
    val liftId: LiftId,
    val equipmentId: EquipmentId?,
    val effectiveTiers: List<IncrementTier>,
    val usableWeights: List<Weight>,
    val residualWeights: List<Weight>,
    val residualSetCount: Int,
    /** The lift's effective adder stops (equipment-inherited until lift overrides exist). */
    val micro: List<Weight> = emptyList(),
) {
    /** Cluster key: equipment id value, or "none" when unassigned. */
    val clusterKey: String
        get() = equipmentId?.value ?: "none"
}

/**
 * Collects lifts with at least one off-lattice positive work weight in [sets].
 *
 * Applies bodyweight skip, empty-tiers skip, zero-weight skip, and typo guard.
 */
internal fun collectSessionAnomalies(
    sets: List<LoggedSet>,
    liftsById: Map<LiftId, Lift>,
    epsilon: Double = LATTICE_EPSILON,
): List<LiftSessionAnomaly> {
    val byLift =
        sets
            .filter { it.liftId != null }
            .groupBy { it.liftId!! }

    return byLift.mapNotNull { (liftId, liftSets) ->
        anomalyForLift(
            liftId = liftId,
            liftSets = liftSets,
            liftsById = liftsById,
            epsilon = epsilon,
        )
    }
}

/**
 * Usable work weights and their off-lattice residual subset — the shared core of the
 * post-workout ([anomalyForLift]) and live ([shouldShowLiveEquipmentAdjust]) stack
 * signals. One pipeline so epsilon, typo, and [LoadType] semantics cannot drift apart.
 */
internal data class LatticeScreening(
    val usableWeights: List<Weight>,
    val residualWeights: List<Weight>,
)

/**
 * Splits [weights] into usable work weights and the off-lattice residual subset:
 * typo-guard ([filterTypoWeights]), near-zero skip, then [isOnLattice]
 * ([LoadType]-aware). Bodyweight lifts and empty tier lists screen empty (no signal).
 */
internal fun screenLatticeWeights(
    weights: List<Weight>,
    tiers: List<IncrementTier>,
    loadType: LoadType,
    epsilon: Double = LATTICE_EPSILON,
    micro: List<Weight> = emptyList(),
): LatticeScreening {
    if (loadType == LoadType.Bodyweight || tiers.isEmpty()) {
        return LatticeScreening(emptyList(), emptyList())
    }
    val usable =
        filterTypoWeights(weights, epsilon).filter { weight ->
            abs(weight.amount) > epsilon
        }
    return LatticeScreening(
        usableWeights = usable,
        residualWeights =
            usable.filter { weight ->
                !tiers.isOnLattice(weight, loadType, epsilon, micro)
            },
    )
}

/**
 * Builds an anomaly for one lift's sets, or `null` when the lift is not an off-lattice signal.
 */
internal fun anomalyForLift(
    liftId: LiftId,
    liftSets: List<LoggedSet>,
    liftsById: Map<LiftId, Lift>,
    epsilon: Double = LATTICE_EPSILON,
): LiftSessionAnomaly? {
    val lift = liftsById[liftId] ?: return null
    val effectiveTiers = lift.incrementTiers
    val screening =
        screenLatticeWeights(
            weights = liftSets.map { it.weight },
            tiers = effectiveTiers,
            loadType = lift.loadType,
            epsilon = epsilon,
            micro = lift.microIncrements,
        )
    val usable = screening.usableWeights
    val residual = screening.residualWeights
    if (usable.isEmpty() || residual.isEmpty()) return null

    // Count residual sets by matching residual amounts (one pass over residual weights).
    val residualKeys =
        residual.map { residualKey(it) }.toHashSet()
    val residualSetCount =
        liftSets.count { set ->
            val w = set.weight
            abs(w.amount) > epsilon && residualKey(w) in residualKeys
        }

    return LiftSessionAnomaly(
        liftId = liftId,
        equipmentId = lift.equipmentId,
        effectiveTiers = effectiveTiers,
        usableWeights = usable,
        residualWeights = residual,
        residualSetCount = residualSetCount,
        micro = lift.microIncrements,
    )
}

private fun residualKey(weight: Weight): String = "${weight.amount}|${weight.unit}"

/**
 * Drops weights that look like typos: amount &gt; [EQUIPMENT_INCREMENT_TYPO_MULTIPLIER]× the
 * max of the other amounts in the same list. When fewer than two weights remain after a
 * candidate drop, the candidate is kept (no peer baseline).
 *
 * When every peer is near-zero, a lone large spike is treated as a mistype (e.g. 0, 0, 625).
 */
internal fun filterTypoWeights(
    weights: List<Weight>,
    epsilon: Double = LATTICE_EPSILON,
): List<Weight> {
    if (weights.size < 2) return weights
    return weights.filterIndexed { index, weight ->
        val amount = abs(weight.amount)
        if (amount <= epsilon) return@filterIndexed true
        val others =
            weights.mapIndexedNotNull { i, w ->
                if (i == index) null else abs(w.amount)
            }
        val peerMax = others.maxOrNull() ?: return@filterIndexed true
        if (peerMax <= epsilon) {
            // No non-zero peer baseline → drop lone spikes among zeros.
            return@filterIndexed false
        }
        amount <= peerMax * EQUIPMENT_INCREMENT_TYPO_MULTIPLIER + epsilon
    }
}
