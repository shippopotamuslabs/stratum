package com.stratum.domain.training

import com.stratum.domain.program.SetType

/**
 * Whether a logged set should count toward **within-session main-block** fatigue math
 * (fatigue decay slope, post-workout rep-range shift gate).
 *
 * Excludes intensifier cluster sets that intentionally hit very low reps under short rest
 * or reduced load — those must not look like "the last working set failed the rep floor"
 * or steepen the fatigue curve for the main prescription band.
 *
 * - [SetType.DropSet] — reduced load chain, not main-band work.
 * - Auto-generated myo minis — stamped as MyoRep with RIR-only zeros (`repMin`/`repMax` 0
 *   and `rirGoal` 0). Match backoffs keep a positive `rirGoal` and still count.
 * - Unknown / null set type (imports) — counted so historical imports stay eligible.
 */
fun LoggedSet.countsTowardMainBlockFatigue(): Boolean =
    liftContext.countsTowardMainBlockFatigue()

fun LoggedLiftContext.countsTowardMainBlockFatigue(): Boolean =
    when (setType) {
        SetType.DropSet -> false
        SetType.MyoRep -> {
            // Minis: repMin=0, repMax=0, rirGoal=0 (see MyoRepLifecycle.miniSet).
            // Floor activations carry a positive band; match backoffs carry rirGoal > 0.
            val goal = rirGoal ?: 0
            val min = repMin ?: 0
            val max = repMax ?: 0
            !(goal == 0 && min == 0 && max == 0)
        }
        SetType.Standard, null -> true
    }
