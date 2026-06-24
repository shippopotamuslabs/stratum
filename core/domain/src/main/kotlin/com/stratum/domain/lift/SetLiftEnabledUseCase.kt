package com.stratum.domain.lift

import com.stratum.domain.lift.identity.LiftId
import javax.inject.Inject

/**
 * Flips a single lift's availability. Used by the manual on/off toggle on the Gym & Lifts
 * step. Clearing the preset (when the user toggles a lift against the preset's defaults) is
 * handled by [SetSelectedPresetUseCase]; this use case only owns the per-lift flag.
 */
class SetLiftEnabledUseCase @Inject constructor(
    private val repository: LiftRepository,
) {
    suspend operator fun invoke(id: LiftId, enabled: Boolean) {
        repository.setLiftEnabled(id, enabled)
    }
}
