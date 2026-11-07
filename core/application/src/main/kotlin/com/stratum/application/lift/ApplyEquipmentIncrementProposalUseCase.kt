package com.stratum.application.lift

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.application.diagnostics.OperationalDiagnostic
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import com.stratum.domain.lift.CatalogNames
import com.stratum.domain.lift.Equipment
import com.stratum.domain.lift.EquipmentIncrementProposal
import com.stratum.domain.lift.EquipmentIncrementRemedy
import com.stratum.domain.lift.IncrementLattice
import com.stratum.domain.lift.IncrementTier
import com.stratum.domain.lift.IncrementTiersValidationResult

/**
 * Outcome of applying a chosen [EquipmentIncrementRemedy] (STR-243: the success
 * branches are the original pair; the failure branches complete the type;
 * STR-237 Route (i) for the operational-failure carrier).
 *
 * Validation branches carry the validation results themselves
 * ([CatalogNames.Validation.Invalid], [IncrementTiersValidationResult.Invalid]).
 * Expected refusals (missing equipment, invalid fork name) are carried by their
 * typed branches ([EquipmentNotFound], [InvalidName]) — the branch is the record
 * the consumer acts on, so they leave no separate breadcrumb and never become a
 * Crashlytics non-fatal. The originating throwable of a failed read or write
 * rides the [PersistenceFailed] branch as an [OperationalDiagnostic] (legacy
 * tag + rendered message, real throwable as cause); the presentation boundary
 * drains it exactly once through its module's DiagnosticDrain. Coroutine
 * cancellation is not a branch: it propagates as cancellation.
 */
sealed interface ApplyEquipmentIncrementResult {
    data class Amended(val equipmentId: EquipmentId) : ApplyEquipmentIncrementResult

    data class Forked(val equipmentId: EquipmentId) : ApplyEquipmentIncrementResult

    data class InvalidName(val validation: CatalogNames.Validation.Invalid) :
        ApplyEquipmentIncrementResult

    data class InvalidTiers(val validation: IncrementTiersValidationResult.Invalid) :
        ApplyEquipmentIncrementResult

    data class EquipmentNotFound(val equipmentId: EquipmentId) : ApplyEquipmentIncrementResult

    /** A repository read or write failed (repository / store I/O). */
    data class PersistenceFailed(
        val diagnostics: List<OperationalDiagnostic>,
    ) : ApplyEquipmentIncrementResult
}

/**
 * Validates (against the [IncrementLattice] rules) and persists a user-accepted
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
) {
    /**
     * Applies [remedy] (defaults to [EquipmentIncrementProposal.primary] when called with a
     * proposal). Tiers are validated before any write.
     */
    suspend operator fun invoke(
        proposal: EquipmentIncrementProposal,
        remedy: EquipmentIncrementRemedy = proposal.primary,
    ): ApplyEquipmentIncrementResult = invoke(remedy)

    suspend operator fun invoke(
        remedy: EquipmentIncrementRemedy,
    ): ApplyEquipmentIncrementResult {
        validateRemedy(remedy)?.let { return it }
        // The fork name is resolved to a value here (STR-169 name rule), not
        // re-derived inside the write: an invalid name is a failure branch.
        // (validateRemedy already rejected an invalid fork name, so this is the
        // valid name or null for non-fork remedies.)
        val forkName =
            (remedy as? EquipmentIncrementRemedy.ForkCustomEquipment)?.let {
                (CatalogNames.validateEquipmentName(it.suggestedName) as? CatalogNames.Validation.Valid)
                    ?.name
            }
        return try {
            when (remedy) {
                is EquipmentIncrementRemedy.AmendEquipmentTiers -> {
                    equipmentRepository.setIncrementTiers(remedy.equipmentId, remedy.tiers)
                    ApplyEquipmentIncrementResult.Amended(remedy.equipmentId)
                }
                is EquipmentIncrementRemedy.ForkCustomEquipment -> {
                    val name = requireNotNull(forkName)
                    val id =
                        equipmentRepository.createCustomEquipmentAndAssignLifts(
                            name = name,
                            tiers = remedy.tiers,
                            liftIds = remedy.assignLiftIds.distinct(),
                        )
                    ApplyEquipmentIncrementResult.Forked(id)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            ApplyEquipmentIncrementResult.PersistenceFailed(
                diagnostics = listOf(
                    OperationalDiagnostic(
                        tag = TAG,
                        message = "Failed to apply equipment increment remedy: ${t.message}",
                        cause = t,
                    ),
                ),
            )
        }
    }

    private fun EquipmentIncrementRemedy.tiers(): List<IncrementTier> =
        when (this) {
            is EquipmentIncrementRemedy.AmendEquipmentTiers -> tiers
            is EquipmentIncrementRemedy.ForkCustomEquipment -> tiers
        }

    /**
     * Validates [remedy] before any write. Returns the failure branch to surface, or null
     * when the remedy may be applied. Validation failures are expected caller outcomes,
     * not operational faults: the typed branch is the record, never a Crashlytics non-fatal.
     */
    private suspend fun validateRemedy(
        remedy: EquipmentIncrementRemedy,
    ): ApplyEquipmentIncrementResult? {
        // The lattice rules are the validation (STR-165).
        when (val tiersResult = IncrementLattice.validateTiers(remedy.tiers())) {
            is IncrementTiersValidationResult.Invalid ->
                return ApplyEquipmentIncrementResult.InvalidTiers(tiersResult)
            IncrementTiersValidationResult.Valid -> Unit
        }
        // Fail clearly when the clustered equipment was deleted between propose and accept
        // (setIncrementTiers alone may no-op on a missing row). A missing row is an expected
        // race, not an operational fault: the EquipmentNotFound branch carries the fact,
        // never a Crashlytics non-fatal.
        if (remedy is EquipmentIncrementRemedy.AmendEquipmentTiers) {
            val existing =
                try {
                    equipmentRepository.getEquipment(remedy.equipmentId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (t: Throwable) {
                    return ApplyEquipmentIncrementResult.PersistenceFailed(
                        diagnostics = listOf(
                            OperationalDiagnostic(
                                tag = TAG,
                                message = "Failed to read equipment for increment remedy: ${t.message}",
                                cause = t,
                            ),
                        ),
                    )
                }
            if (existing == null) {
                return ApplyEquipmentIncrementResult.EquipmentNotFound(remedy.equipmentId)
            }
        }
        if (remedy is EquipmentIncrementRemedy.ForkCustomEquipment) {
            val nameResult = CatalogNames.validateEquipmentName(remedy.suggestedName)
            if (nameResult is CatalogNames.Validation.Invalid) {
                return ApplyEquipmentIncrementResult.InvalidName(nameResult)
            }
        }
        return null
    }

    private companion object {
        const val TAG = "ApplyEquipmentIncrementProposal"
    }
}
