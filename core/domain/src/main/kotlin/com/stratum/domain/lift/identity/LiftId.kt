package com.stratum.domain.lift.identity

import com.stratum.domain.lift.Lift

/**
 * Type-safe identifier for a [com.stratum.domain.lift.Lift]. Stable slug ids identify
 * bundled seed lifts (e.g. `"leg-press"`) so seeding stays deterministic across installs;
 * user-created lifts use `LiftId(UUID.randomUUID().toString())`. The wrapper prevents a
 * `LiftId` from being assigned to a different aggregate's id field.
 */
@JvmInline
value class LiftId(override val value: String) : EntityId {
    init {
        require(value.isNotBlank()) { "LiftId cannot be blank" }
    }
}
