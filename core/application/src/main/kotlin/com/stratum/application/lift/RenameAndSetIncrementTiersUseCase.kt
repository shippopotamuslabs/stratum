package com.stratum.application.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.logging.LoggingPort
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import com.stratum.domain.lift.CatalogNames
import com.stratum.domain.lift.IncrementLattice
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.IncrementTiersValidationResult
import com.stratum.domain.lift.MicroIncrementsValidationResult
import com.stratum.domain.lift.Weight

/**
 * Outcome of the rename-and-set-tiers editor save (STR-243).
 *
 * Validation branches carry the validation results themselves
 * ([CatalogNames.Validation.Invalid], [IncrementTiersValidationResult.Invalid],
 * [MicroIncrementsValidationResult.Invalid]). The originating throwable of a failed
 * write goes once to the diagnostics channel ([LoggingPort.error]) at the use-case
 * boundary; it never crosses as a payload. Coroutine cancellation is not a branch:
 * it propagates as cancellation.
 */
sealed interface RenameAndSetIncrementTiersResult {
    data object Success : RenameAndSetIncrementTiersResult

    data class InvalidName(val validation: CatalogNames.Validation.Invalid) :
        RenameAndSetIncrementTiersResult

    data class InvalidTiers(val validation: IncrementTiersValidationResult.Invalid) :
        RenameAndSetIncrementTiersResult

    data class InvalidMicro(val validation: MicroIncrementsValidationResult.Invalid) :
        RenameAndSetIncrementTiersResult

    /** The combined rename + tier write failed (repository / store I/O). */
    data object PersistenceFailed : RenameAndSetIncrementTiersResult
}

/**
 * Editor save that renames equipment and replaces its increment tiers as one
 * atomic write (STR-135): validates the name like [RenameEquipmentUseCase] and
 * the tiers like [SetEquipmentIncrementTiersUseCase], then persists both via a
 * single repository transaction so neither half can land without the other.
 */
class RenameAndSetIncrementTiersUseCase @Inject constructor(
    private val repository: EquipmentRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(
        id: EquipmentId,
        name: String,
        tiers: List<IncrementTier>,
        micro: List<Weight> = emptyList(),
    ): RenameAndSetIncrementTiersResult {
        val trimmed =
            when (val nameResult = CatalogNames.validateEquipmentName(name)) {
                is CatalogNames.Validation.Valid -> nameResult.name
                is CatalogNames.Validation.Invalid ->
                    return RenameAndSetIncrementTiersResult.InvalidName(nameResult)
            }
        // The lattice rules are the validation (STR-165), in constructor order.
        when (val tiersResult = IncrementLattice.validateTiers(tiers)) {
            is IncrementTiersValidationResult.Invalid ->
                return RenameAndSetIncrementTiersResult.InvalidTiers(tiersResult)
            IncrementTiersValidationResult.Valid -> Unit
        }
        when (val microResult = IncrementLattice.validateMicro(tiers, micro)) {
            is MicroIncrementsValidationResult.Invalid ->
                return RenameAndSetIncrementTiersResult.InvalidMicro(microResult)
            MicroIncrementsValidationResult.Valid -> Unit
        }
        return try {
            repository.renameAndSetIncrementTiers(id, trimmed, tiers, micro)
            RenameAndSetIncrementTiersResult.Success
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            loggingPort.error(
                TAG,
                "Failed to rename equipment and set increment tiers: ${t.message}",
                t,
            )
            RenameAndSetIncrementTiersResult.PersistenceFailed
        }
    }

    private companion object {
        const val TAG = "RenameAndSetIncrementTiers"
    }
}
