package com.stratum.domain.lift.identity

/**
 * Type-safe identifier for an [com.stratum.domain.lift.Equipment] row.
 *
 * Bundled seed equipment uses stable slugs (e.g. `"dumbbell"`, `"cable"`). User-created
 * equipment uses UUID strings. The wrapper prevents accidental assignment to [LiftId] or
 * other aggregate ids.
 */
@JvmInline
value class EquipmentId(override val value: String) : EntityId {
    init {
        require(value.isNotBlank()) { "EquipmentId cannot be blank" }
    }
}
