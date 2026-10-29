package com.stratum.domain.lift

import com.stratum.domain.lift.identity.LiftId

data class AffectedLift(
    val id: LiftId,
    val name: String,
    val enabled: Boolean,
)

/**
 * Blast-radius snapshot for editing equipment default increments.
 *
 * [count] / [lifts] include disabled lifts that inherit defaults. Per-lift overrides were
 * removed (STR-82/84), so every lift linked to the equipment inherits its defaults.
 */
