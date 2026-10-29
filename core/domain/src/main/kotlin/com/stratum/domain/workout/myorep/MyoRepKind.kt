package com.stratum.domain.workout.myorep

import com.stratum.domain.program.SetType

/**
 * Myo-rep sub-type (D6). NOT persisted on `SetType` — carried on the runtime/overlay
 * structure model only.
 *
 * - [RepFloor]: Type A — mini-sets until one hits the rep floor.
 * - [RepMatching]: Type B — mini-sets until cumulative reps equal activation reps.
 */
enum class MyoRepKind { RepFloor, RepMatching }
