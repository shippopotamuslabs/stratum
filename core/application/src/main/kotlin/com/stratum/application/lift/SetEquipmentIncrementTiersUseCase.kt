package com.stratum.application.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.logging.LoggingPort
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import com.stratum.domain.lift.IncrementLattice
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.IncrementTiersValidationResult
import com.stratum.domain.lift.MicroIncrementsValidationResult
import com.stratum.domain.lift.Weight

/**
 * Outcome of setting equipment increment tiers (STR-243).
 *
 * Validation branches carry the domain validation results themselves
 * ([IncrementTiersValidationResult.Invalid] / [MicroIncrementsValidationResult.Invalid]).
 * The originating throwable of a failed write goes once to the diagnostics channel
 * ([LoggingPort.error]) at the use-case boundary; it never crosses as a payload.
 * Coroutine cancellation is not a branch: it propagates as cancellation.
 */
sealed interface SetEquipmentIncrementTiersResult {
    data object Success : SetEquipmentIncrementTiersResult

    data class InvalidTiers(val validation: IncrementTiersValidationResult.Invalid) :
        SetEquipmentIncrementTiersResult

    data class InvalidMicro(val validation: MicroIncrementsValidationResult.Invalid) :
        SetEquipmentIncrementTiersResult

    /** The tier write failed (repository / store I/O). */
    data object PersistenceFailed : SetEquipmentIncrementTiersResult
}

/**
 * Validates against the [IncrementLattice] rules then persists equipment default
 * increment tiers and adder stops. Empty tiers
 * clear equipment defaults so dependent lifts without overrides fall back to global
 * fallback; empty [micro] means no adder.
 */
class SetEquipmentIncrementTiersUseCase @Inject constructor(
    private val repository: EquipmentRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(
        id: EquipmentId,
        tiers: List<IncrementTier>,
        micro: List<Weight> = emptyList(),
    ): SetEquipmentIncrementTiersResult {
        // The lattice rules are the validation (STR-165), in constructor order:
        // tiers first, then micro against those tiers.
        when (val tiersResult = IncrementLattice.validateTiers(tiers)) {
            is IncrementTiersValidationResult.Invalid ->
                return SetEquipmentIncrementTiersResult.InvalidTiers(tiersResult)
            IncrementTiersValidationResult.Valid -> Unit
        }
        when (val microResult = IncrementLattice.validateMicro(tiers, micro)) {
            is MicroIncrementsValidationResult.Invalid ->
                return SetEquipmentIncrementTiersResult.InvalidMicro(microResult)
            MicroIncrementsValidationResult.Valid -> Unit
        }
        return try {
            repository.setIncrementTiers(id, tiers, micro)
            SetEquipmentIncrementTiersResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            loggingPort.error(TAG, "Failed to set equipment increment tiers: ${t.message}", t)
            SetEquipmentIncrementTiersResult.PersistenceFailed
        }
    }

    private companion object {
        const val TAG = "SetEquipmentIncrementTiers"
    }
}
