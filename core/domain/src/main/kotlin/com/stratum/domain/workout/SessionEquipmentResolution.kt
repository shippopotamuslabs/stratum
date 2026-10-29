package com.stratum.domain.workout

import com.stratum.domain.lift.identity.EquipmentId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.training.SetEquipmentSource

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

/**
 * The template equipment assignment as seen by one (slot, lift) pair (STR-127):
 * the assignment belongs to the template lift's pair, so after a lift swap it is
 * skipped and the incoming lift falls back to its own default — unless an
 * equipment swap in this session already chose equipment for the new pair. A
 * swap back to the template lift restores the assignment (it is that pair's).
 *
 * Single canonical rule for render, session equipment projection, and
 * template save-back; pass the result as `templateEquipmentId` to
 * [resolveSessionEquipmentId].
 */
fun templateEquipmentIdForPair(
    swappedLiftId: LiftId?,
    templateLiftId: LiftId,
    hasSessionEquipmentOverride: Boolean,
    templateEquipmentId: EquipmentId?,
): EquipmentId? =
    if (swappedLiftId != null && swappedLiftId != templateLiftId && !hasSessionEquipmentOverride) {
        null
    } else {
        templateEquipmentId
    }
