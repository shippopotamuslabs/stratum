package com.stratum.domain.lift

import com.stratum.domain.lift.identity.LiftId
import javax.inject.Inject

/**
 * Creates a user-defined lift (`isCustom = true`, `enabled = true`) and returns its id. The
 * minimal v1 custom form is name + `allowWeightChangeBetweenSets` + an optional single
 * increment; full tier editing is deferred. The repository supplies sensible default tiers
 * when [increment] is null.
 */
class AddCustomLiftUseCase @Inject constructor(
    private val repository: LiftRepository,
) {
    suspend operator fun invoke(
        name: String,
        allowWeightChangeBetweenSets: Boolean,
        increment: Weight?,
    ): LiftId = repository.addCustomLift(name, allowWeightChangeBetweenSets, increment)
}
