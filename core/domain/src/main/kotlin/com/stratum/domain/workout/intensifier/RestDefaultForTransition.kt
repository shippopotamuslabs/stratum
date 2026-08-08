package com.stratum.domain.workout.intensifier

import com.stratum.domain.program.DEFAULT_LIFT_SET_REST_SECONDS
import com.stratum.domain.program.SetType
import com.stratum.domain.workout.myorep.MyoRepKind

/**
 * Edge-based rest defaults for intensifier sets.
 *
 * - next is Drop → 0 (no rest into a drop, including Drop→Drop)
 * - current is MyoRep **RepFloor** (or unknown kind) → [MYO_MINI_DEFAULT_REST_SECONDS]
 *   (intra-cluster mini rest stored on the floor activation)
 * - current is MyoRep **RepMatching** (activation / match backoff) → main lift rest
 *   (full rest between activation and backoffs; minis always use 15s at log time)
 * - auto-generated myo minis → always [MYO_MINI_DEFAULT_REST_SECONDS]
 * - otherwise → [liftDefaultRestSeconds]
 *
 * **Myo dual-edge model:** After a mini while the cluster continues, [restAfterMyoLog]
 * uses mini rest; when the cluster ends, it uses main/lift default rest.
 */
object RestDefaultForTransition {
    const val REST_INTO_DROP_SECONDS: Int = 0
    const val MYO_MINI_DEFAULT_REST_SECONDS: Int = 15

    /**
     * Default rest **stored** on a set given its own type/kind and what follows it.
     * [nextType] null means end of lift / next exercise.
     *
     * Floor myo stores mini rest; match myo stores main rest. Runtime timers after
     * the final mini must use [restAfterMyoLog] instead.
     */
    fun restAfter(
        nextType: SetType?,
        liftDefaultRestSeconds: Int = DEFAULT_LIFT_SET_REST_SECONDS,
        currentType: SetType? = null,
        currentMyoKind: MyoRepKind? = null,
        isMyoMini: Boolean = false,
    ): Int {
        if (nextType == SetType.DropSet) return REST_INTO_DROP_SECONDS
        if (isMyoMini) return MYO_MINI_DEFAULT_REST_SECONDS
        if (currentType == SetType.MyoRep) {
            return when (currentMyoKind) {
                MyoRepKind.RepMatching -> liftDefaultRestSeconds.coerceAtLeast(0)
                MyoRepKind.RepFloor, null -> MYO_MINI_DEFAULT_REST_SECONDS
            }
        }
        return liftDefaultRestSeconds.coerceAtLeast(0)
    }

    /**
     * Rest after **logging** a Myo set at runtime.
     *
     * - [clusterContinues] true (another mini is next) → [miniRestSeconds] (typically 15s)
     * - cluster finished → [mainRestSeconds] (activation/backoff programmed rest or lift default)
     * - next is Drop → forced 0
     */
    fun restAfterMyoLog(
        miniRestSeconds: Int,
        clusterContinues: Boolean,
        liftDefaultRestSeconds: Int = DEFAULT_LIFT_SET_REST_SECONDS,
        nextType: SetType? = null,
        mainRestSeconds: Int? = null,
    ): Int {
        if (nextType == SetType.DropSet) return REST_INTO_DROP_SECONDS
        return if (clusterContinues) {
            miniRestSeconds.coerceAtLeast(0)
        } else {
            (mainRestSeconds ?: liftDefaultRestSeconds).coerceAtLeast(0)
        }
    }

    /**
     * Whether the rest control should be shown when the following set is [nextType].
     * Forced-zero edges hide the control (cleaner than a locked 0:00 chip).
     */
    fun showRestControl(nextType: SetType?): Boolean =
        nextType != SetType.DropSet

    /**
     * Re-defaults rest for every set in a template after a type change.
     * [setTypes] ordered by position; returns parallel rest seconds.
     * Without kinds, MyoRep defaults to floor (15s) for backward compatibility.
     */
    fun redefaultAll(
        setTypes: List<SetType>,
        liftDefaultRestSeconds: Int = DEFAULT_LIFT_SET_REST_SECONDS,
    ): List<Int> =
        redefaultAll(
            setTypes = setTypes,
            liftDefaultRestSeconds = liftDefaultRestSeconds,
            myoKinds = List(setTypes.size) { null },
        )

    /**
     * Kind-aware redefault: [myoKinds] parallel to [setTypes] (null for non-myo).
     * RepMatching rows get main rest; RepFloor / unknown myo get mini rest.
     * [isMyoMini] rows always get [MYO_MINI_DEFAULT_REST_SECONDS] (never main rest).
     */
    fun redefaultAll(
        setTypes: List<SetType>,
        liftDefaultRestSeconds: Int = DEFAULT_LIFT_SET_REST_SECONDS,
        myoKinds: List<MyoRepKind?>,
        isMyoMini: List<Boolean> = List(setTypes.size) { false },
    ): List<Int> {
        require(myoKinds.size == setTypes.size) {
            "myoKinds size (${myoKinds.size}) must match setTypes (${setTypes.size})"
        }
        require(isMyoMini.size == setTypes.size) {
            "isMyoMini size (${isMyoMini.size}) must match setTypes (${setTypes.size})"
        }
        return setTypes.mapIndexed { index, current ->
            val next = setTypes.getOrNull(index + 1)
            restAfter(
                nextType = next,
                liftDefaultRestSeconds = liftDefaultRestSeconds,
                currentType = current,
                currentMyoKind = myoKinds[index],
                isMyoMini = isMyoMini[index],
            )
        }
    }
}
