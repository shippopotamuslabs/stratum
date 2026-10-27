package com.stratum.domain.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.expectedFailure
import com.stratum.domain.logging.runCatchingLogged
import javax.inject.Inject

/**
 * Outcome of applying a chosen [EquipmentIncrementRemedy].
 */
sealed interface ApplyEquipmentIncrementResult {
    data class Amended(val equipmentId: EquipmentId) : ApplyEquipmentIncrementResult

    data class Forked(val equipmentId: EquipmentId) : ApplyEquipmentIncrementResult
}

/**
 * Validates (by constructing the [IncrementLattice]) and persists a user-accepted
 * [EquipmentIncrementRemedy] from post-workout proposals
 * (or any caller that already ranked a fix).
 *
 * - [EquipmentIncrementRemedy.AmendEquipmentTiers] → equipment default tiers
 * - [EquipmentIncrementRemedy.ForkCustomEquipment] → transactional create + assign signal lifts
 *
 * Per-lift overrides were removed (STR-82/84): there is no lift-override remedy anymore.
 */
class ApplyEquipmentIncrementProposalUseCase @Inject constructor(
    private val equipmentRepository: EquipmentRepository,
    private val loggingPort: LoggingPort,
) {
    /**
     * Applies [remedy] (defaults to [EquipmentIncrementProposal.primary] when called with a
     * proposal). Tiers are validated before any write.
     */
    suspend operator fun invoke(
        proposal: EquipmentIncrementProposal,
        remedy: EquipmentIncrementRemedy = proposal.primary,
    ): Result<ApplyEquipmentIncrementResult> = invoke(remedy)

    suspend operator fun invoke(
        remedy: EquipmentIncrementRemedy,
    ): Result<ApplyEquipmentIncrementResult> {
        validateRemedy(remedy)?.let { return it }
        return runCatchingLogged(loggingPort, TAG, "Failed to apply equipment increment remedy") {
            when (remedy) {
                is EquipmentIncrementRemedy.AmendEquipmentTiers -> {
                    equipmentRepository.setIncrementTiers(remedy.equipmentId, remedy.tiers)
                    ApplyEquipmentIncrementResult.Amended(remedy.equipmentId)
                }
                is EquipmentIncrementRemedy.ForkCustomEquipment -> {
                    // Validated by validateRemedy before any write (STR-169 name rule).
                    val name = CatalogNames.equipmentNameResult(remedy.suggestedName).getOrThrow()
                    val id =
                        equipmentRepository.createCustomEquipmentAndAssignLifts(
                            name = name,
                            tiers = remedy.tiers,
                            liftIds = remedy.assignLiftIds.distinct(),
                        )
                    ApplyEquipmentIncrementResult.Forked(id)
                }
            }
        }
    }

    private fun EquipmentIncrementRemedy.tiers(): List<IncrementTier> =
        when (this) {
            is EquipmentIncrementRemedy.AmendEquipmentTiers -> tiers
            is EquipmentIncrementRemedy.ForkCustomEquipment -> tiers
        }

    /**
     * Validates [remedy] before any write. Returns the failure to surface, or null when the
     * remedy may be applied. Validation failures are expected caller outcomes, not
     * operational faults: breadcrumb only, never a Crashlytics non-fatal.
     */
    private suspend fun validateRemedy(
        remedy: EquipmentIncrementRemedy,
    ): Result<ApplyEquipmentIncrementResult>? {
        // Constructing the lattice is the validation (STR-165).
        IncrementLattice.resultOf(remedy.tiers()).onFailure { return Result.failure(it) }
        // Fail clearly when the clustered equipment was deleted between propose and accept
        // (setIncrementTiers alone may no-op on a missing row). A missing row is an expected
        // race, not an operational fault: breadcrumb only, never a Crashlytics non-fatal.
        if (remedy is EquipmentIncrementRemedy.AmendEquipmentTiers) {
            val existing =
                runCatchingLogged(loggingPort, TAG, "Failed to read equipment for increment remedy") {
                    equipmentRepository.getEquipment(remedy.equipmentId)
                }.getOrElse { return Result.failure(it) }
            if (existing == null) {
                return loggingPort.expectedFailure(
                    TAG,
                    IllegalArgumentException("Equipment not found"),
                    "Equipment ${remedy.equipmentId.value} not found for increment remedy",
                )
            }
        }
        if (remedy is EquipmentIncrementRemedy.ForkCustomEquipment) {
            CatalogNames.equipmentNameResult(remedy.suggestedName).exceptionOrNull()?.let { error ->
                return loggingPort.expectedFailure(
                    TAG,
                    error,
                    "Equipment name is required for increment remedy",
                )
            }
        }
        return null
    }

    private companion object {
        const val TAG = "ApplyEquipmentIncrementProposal"
    }
}
