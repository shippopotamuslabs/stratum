package com.stratum.domain.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.logging.LoggingPort
import com.stratum.domain.logging.runCatchingLogged
import javax.inject.Inject

/**
 * Outcome of applying a chosen [EquipmentIncrementRemedy].
 */
sealed interface ApplyEquipmentIncrementResult {
    data class Amended(val equipmentId: EquipmentId) : ApplyEquipmentIncrementResult

    data class Forked(val equipmentId: EquipmentId) : ApplyEquipmentIncrementResult

    data class LiftOverridden(val liftId: LiftId) : ApplyEquipmentIncrementResult
}

/**
 * Validates and persists a user-accepted [EquipmentIncrementRemedy] from post-workout proposals
 * (or any caller that already ranked a fix).
 *
 * - [EquipmentIncrementRemedy.AmendEquipmentTiers] → equipment default tiers
 * - [EquipmentIncrementRemedy.ForkCustomEquipment] → transactional create + assign signal lifts
 * - [EquipmentIncrementRemedy.LiftOverride] → lift-scoped override tiers
 */
class ApplyEquipmentIncrementProposalUseCase @Inject constructor(
    private val equipmentRepository: EquipmentRepository,
    private val liftRepository: LiftRepository,
    private val validate: ValidateIncrementTiersUseCase,
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
        val tiers = remedy.tiers()
        when (val result = validate(tiers)) {
            is IncrementTiersValidationResult.Invalid ->
                return Result.failure(IncrementTiersInvalidException(result.errors))
            IncrementTiersValidationResult.Valid -> Unit
        }
        // Fail clearly when the clustered equipment was deleted between propose and accept
        // (setIncrementTiers alone may no-op on a missing row). A missing row is an expected
        // race, not an operational fault: breadcrumb only, never a Crashlytics non-fatal.
        if (remedy is EquipmentIncrementRemedy.AmendEquipmentTiers) {
            val existing =
                runCatchingLogged(loggingPort, TAG, "Failed to read equipment for increment remedy") {
                    equipmentRepository.getEquipment(remedy.equipmentId)
                }.getOrElse { return Result.failure(it) }
            if (existing == null) {
                loggingPort.warning(TAG, "Equipment ${remedy.equipmentId.value} not found for increment remedy")
                return Result.failure(IllegalArgumentException("Equipment not found"))
            }
        }
        if (remedy is EquipmentIncrementRemedy.ForkCustomEquipment && remedy.suggestedName.isBlank()) {
            loggingPort.warning(TAG, "Equipment name is required for increment remedy")
            return Result.failure(IllegalArgumentException("Equipment name is required"))
        }
        return runCatchingLogged(loggingPort, TAG, "Failed to apply equipment increment remedy") {
            when (remedy) {
                is EquipmentIncrementRemedy.AmendEquipmentTiers -> {
                    equipmentRepository.setIncrementTiers(remedy.equipmentId, remedy.tiers)
                    ApplyEquipmentIncrementResult.Amended(remedy.equipmentId)
                }
                is EquipmentIncrementRemedy.ForkCustomEquipment -> {
                    val name = remedy.suggestedName.trim()
                    val id =
                        equipmentRepository.createCustomEquipmentAndAssignLifts(
                            name = name,
                            tiers = remedy.tiers,
                            liftIds = remedy.assignLiftIds.distinct(),
                        )
                    ApplyEquipmentIncrementResult.Forked(id)
                }
                is EquipmentIncrementRemedy.LiftOverride -> {
                    liftRepository.setIncrementTiers(remedy.liftId, remedy.tiers)
                    ApplyEquipmentIncrementResult.LiftOverridden(remedy.liftId)
                }
            }
        }
    }

    private fun EquipmentIncrementRemedy.tiers(): List<IncrementTier> =
        when (this) {
            is EquipmentIncrementRemedy.AmendEquipmentTiers -> tiers
            is EquipmentIncrementRemedy.ForkCustomEquipment -> tiers
            is EquipmentIncrementRemedy.LiftOverride -> tiers
        }

    private companion object {
        const val TAG = "ApplyEquipmentIncrementProposal"
    }
}
