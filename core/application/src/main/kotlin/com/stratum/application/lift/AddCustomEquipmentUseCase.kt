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
import com.stratum.domain.lift.defaultSingleTiers

/**
 * Outcome of adding custom equipment (STR-243).
 *
 * Validation branches carry the validation results themselves
 * ([CatalogNames.Validation.Invalid], [IncrementTiersValidationResult.Invalid],
 * [MicroIncrementsValidationResult.Invalid]). The originating throwable of a failed
 * write goes once to the diagnostics channel ([LoggingPort.error]) at the use-case
 * boundary; it never crosses as a payload. Coroutine cancellation is not a branch:
 * it propagates as cancellation.
 */
sealed interface AddCustomEquipmentResult {
    data class Success(val equipmentId: EquipmentId) : AddCustomEquipmentResult

    data class InvalidName(val validation: CatalogNames.Validation.Invalid) :
        AddCustomEquipmentResult

    data class InvalidTiers(val validation: IncrementTiersValidationResult.Invalid) :
        AddCustomEquipmentResult

    data class InvalidMicro(val validation: MicroIncrementsValidationResult.Invalid) :
        AddCustomEquipmentResult

    /** The equipment write failed (repository / store I/O). */
    data object PersistenceFailed : AddCustomEquipmentResult
}

/**
 * Creates user-defined equipment with optional default increment tiers. Validates the name and
 * any provided tiers (against the [IncrementLattice] rules) before persisting. [micro] seeds the adder stops (empty = no adder);
 * a clone may or may not carry the knob.
 */
class AddCustomEquipmentUseCase @Inject constructor(
    private val repository: EquipmentRepository,
    private val loggingPort: LoggingPort,
) {
    suspend operator fun invoke(
        name: String,
        tiers: List<IncrementTier> = emptyList(),
        micro: List<Weight> = emptyList(),
    ): AddCustomEquipmentResult {
        val trimmed =
            when (val nameResult = CatalogNames.validateEquipmentName(name)) {
                is CatalogNames.Validation.Valid -> nameResult.name
                is CatalogNames.Validation.Invalid ->
                    return AddCustomEquipmentResult.InvalidName(nameResult)
            }
        // The lattice rules are the validation (STR-165). Micro validates
        // against the persisted context: empty tiers become the repository's default
        // single band on write, so stops legal for that band (e.g. ≤ 5 lb) must not
        // be rejected as MicroWithoutTiers here.
        val effectiveTiers = tiers.ifEmpty { defaultSingleTiers() }
        if (tiers.isNotEmpty() || micro.isNotEmpty()) {
            when (val tiersResult = IncrementLattice.validateTiers(effectiveTiers)) {
                is IncrementTiersValidationResult.Invalid ->
                    return AddCustomEquipmentResult.InvalidTiers(tiersResult)
                IncrementTiersValidationResult.Valid -> Unit
            }
            when (val microResult = IncrementLattice.validateMicro(effectiveTiers, micro)) {
                is MicroIncrementsValidationResult.Invalid ->
                    return AddCustomEquipmentResult.InvalidMicro(microResult)
                MicroIncrementsValidationResult.Valid -> Unit
            }
        }
        return try {
            AddCustomEquipmentResult.Success(repository.addCustomEquipment(trimmed, tiers, micro))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            loggingPort.error(TAG, "Failed to add custom equipment: ${t.message}", t)
            AddCustomEquipmentResult.PersistenceFailed
        }
    }

    private companion object {
        const val TAG = "AddCustomEquipment"
    }
}
