package com.stratum.application.lift

import com.stratum.application.training.TrainingLogRepository
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.EquipmentIncrementProposal
import com.stratum.domain.lift.EquipmentIncrementRankInput
import com.stratum.domain.lift.EquipmentIncrementRemedy
import com.stratum.domain.lift.EquipmentProposalAnalysis
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.LoadType
import com.stratum.domain.lift.Weight
import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.screenLatticeWeights
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.identity.TrainingSessionId
import com.stratum.domain.training.isEligibleCompletedLiveSession
import javax.inject.Inject
import kotlin.math.abs
import kotlinx.coroutines.flow.first
import com.stratum.domain.lift.inferIncrementLattice
import com.stratum.domain.lift.rankEquipmentIncrementProposal
import com.stratum.domain.lift.scoreEquipmentIncrementProposal
import com.stratum.domain.lift.shouldShowLiveEquipmentAdjust
import com.stratum.domain.lift.EQUIPMENT_INCREMENT_FORK_THRESHOLD_K
import com.stratum.domain.lift.EQUIPMENT_INCREMENT_TYPO_MULTIPLIER
import com.stratum.domain.lift.LATTICE_EPSILON
import com.stratum.domain.lift.residual

/**
 * Max proposal cards per completed session (product decision).
 */
const val MAX_SESSION_EQUIPMENT_PROPOSALS: Int = 3


class AnalyzeSessionEquipmentProposalsUseCase @Inject constructor(
    private val trainingLogRepository: TrainingLogRepository,
    private val liftRepository: LiftRepository,
    private val equipmentRepository: EquipmentRepository,
    private val preferences: EquipmentProposalPreferences,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(sessionId: TrainingSessionId): EquipmentProposalAnalysis =
        runCatchingLogged(
            loggingPort = loggingPort,
            tag = TAG,
            message = "Failed to analyze session equipment proposals for ${sessionId.value}",
        ) {
            analyze(sessionId)
        }.getOrElse { EquipmentProposalAnalysis() }

    private suspend fun analyze(sessionId: TrainingSessionId): EquipmentProposalAnalysis {
        if (!preferences.isEnabled()) return EquipmentProposalAnalysis()
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
        if (sets.isEmpty()) return EquipmentProposalAnalysis()

        val lifts = liftRepository.observeLifts().first()
        val equipment = equipmentRepository.observeEquipment().first()
        val liftsById = lifts.associateBy { it.id }
        val equipmentById = equipment.associateBy { it.id }
        val existingEquipmentNames = equipment.map { it.name }

        val anomalies = collectSessionAnomalies(sets, liftsById)
        if (anomalies.isEmpty()) return EquipmentProposalAnalysis()

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
            built.sortedByDescending { result ->
                scoreEquipmentIncrementProposal(
                    residualSetCount = result.proposal.residualSetCount,
                    distinctResidualAmounts = result.proposal.distinctResidualAmounts,
                    primaryIsFork =
                        result.proposal.primary is EquipmentIncrementRemedy.ForkCustomEquipment,
                    affectedInheritingLiftCount = result.proposal.affectedInheritingLiftCount,
                )
            }
        val proposals = ArrayList<EquipmentIncrementProposal>(MAX_SESSION_EQUIPMENT_PROPOSALS)
        val baselineMicroByProposalId = LinkedHashMap<String, List<Weight>>()
        for (result in ranked) {
            if (proposals.size >= MAX_SESSION_EQUIPMENT_PROPOSALS) break
            if (!preferences.isDismissed(result.proposal.id)) {
                proposals.add(result.proposal)
                baselineMicroByProposalId[result.proposal.id] = result.baselineMicro
            }
        }
        return EquipmentProposalAnalysis(proposals, baselineMicroByProposalId)
    }

    /**
     * A ranked cluster proposal plus the baseline adder stops it assumed — kept
     * out of the canonical proposal; the analysis result carries them to the
     * UI mapping boundary keyed by proposal id.
     */
    private data class ClusterResult(
        val proposal: EquipmentIncrementProposal,
        val baselineMicro: List<Weight>,
    )

    private fun buildClusterProposal(
        clusterAnomalies: List<LiftSessionAnomaly>,
        liftsById: Map<LiftId, Lift>,
        equipmentById: Map<EquipmentId, Equipment>,
        allLifts: List<Lift>,
        existingEquipmentNames: List<String>,
    ): ClusterResult? {
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

        val proposal =
            rankEquipmentIncrementProposal(
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
                    residualSetCount = residualSetCount,
                    distinctResidualAmounts = distinctResidualAmounts,
                    existingEquipmentNames = existingEquipmentNames,
                ),
            )
        return ClusterResult(proposal, currentMicro)
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
