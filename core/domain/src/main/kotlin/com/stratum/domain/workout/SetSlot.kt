package com.stratum.domain.workout

import com.stratum.domain.program.SetType
import com.stratum.domain.program.identity.LiftSetId
import com.stratum.domain.program.identity.WorkoutLiftId
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.workout.myorep.MyoRepKind

/** Type-safe identifier for a dynamically-added overlay slot instance. */
@JvmInline
value class OverlaySetInstanceId(val value: String)

/**
 * Identity for a slot that is stable across snapshot/overlay merge. Template slots
 * key on [LiftSetId]; dynamic/generated slots key on [OverlaySetInstanceId].
 */
sealed interface SetSlotId {
    data class Template(val liftSetId: LiftSetId) : SetSlotId
    data class Overlay(val instanceId: OverlaySetInstanceId) : SetSlotId
}

/**
 * Structure projection slot (D1). Carries set type, rep range, RIR, top-set marking —
 * **no load**. The snapshot layer supplies the recommended weight; the overlay layer
 * supersedes it.
 */
data class SetSlot(
    val slotId: SetSlotId,
    val workoutLiftId: WorkoutLiftId,
    val liftId: LiftId,
    val position: Int,
    val setType: SetType,
    val isTopSet: Boolean,
    val repMin: Int,
    val repMax: Int,
    val rirGoal: Int,
    val restSeconds: Int,
    val myoRepKind: MyoRepKind? = null,
)
