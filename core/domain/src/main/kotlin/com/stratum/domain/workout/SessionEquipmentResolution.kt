package com.stratum.domain.workout

import com.stratum.domain.lift.identity.EquipmentId

/**
 * Resolves the equipment in use for a template lift this session.
 *
 * Priority: live-swap override first, then the template assignment, then the lift
 * default. Null means "no usable equipment" (bodyweight / unassigned); set-logging
 * call sites map that to `SetEquipmentSource.FromLift` so the lift default still
 * applies at log time.
 */
fun resolveSessionEquipmentId(
    swapOverrideId: EquipmentId?,
    templateEquipmentId: EquipmentId?,
    liftEquipmentId: EquipmentId?,
): EquipmentId? = swapOverrideId ?: templateEquipmentId ?: liftEquipmentId
