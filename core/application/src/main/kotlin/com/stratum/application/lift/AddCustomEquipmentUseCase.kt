package com.stratum.application.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.application.diagnostics.OperationalDiagnostic
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
 * Outcome of adding custom equipment (STR-243; STR-237 Route (i)).
 *
 * Validation branches carry the validation results themselves
 * ([CatalogNames.Validation.Invalid], [IncrementTiersValidationResult.Invalid],
 * [MicroIncrementsValidationResult.Invalid]). The originating throwable of a failed
 * write rides the [PersistenceFailed] branch as an [OperationalDiagnostic]
 * (legacy tag + rendered message, real throwable as cause); the presentation
 * boundary drains it exactly once through its module's DiagnosticDrain.
 * Coroutine cancellation is not a branch: it propagates as cancellation.
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
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : AddCustomEquipmentResult
}

/**
 * Creates user-defined equipment with optional default increment tiers. Validates the name and
 * any provided tiers (against the [IncrementLattice] rules) before persisting. [micro] seeds the adder stops (empty = no adder);
 * a clone may or may not carry the knob.
 */
class AddCustomEquipmentUseCase @Inject constructor(
    private val repository: EquipmentRepository,
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
            AddCustomEquipmentResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to add custom equipment: ${t.message}",
                        cause = t,
                    ),
                ),
            )
        }
    }

    private companion object {
        const val TAG = "AddCustomEquipment"
    }
}
