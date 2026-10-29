package com.stratum.domain.checkin

import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.lift.ScoredLift

/**
 * Auto-swap only when similarity is high enough to stay on-pattern/muscle, but not a near-clone
 * (stimulus should actually change). Fail closed outside the band.
 */
const val MIN_CHECKIN_SWAP_SIMILARITY = 0.45

const val MAX_CHECKIN_SWAP_SIMILARITY = 0.95

/**
 * First ranked candidate not in [excludeLiftIds] with similarity in
 * `[MIN_CHECKIN_SWAP_SIMILARITY, MAX_CHECKIN_SWAP_SIMILARITY]`. Assumes [ranked] is already sorted
 * best-first (similarity desc).
 */
fun selectSwapCandidate(
    ranked: List<ScoredLift>,
    excludeLiftIds: Set<LiftId>,
): ScoredLift? =
    ranked.firstOrNull { scored ->
        scored.lift.id !in excludeLiftIds &&
            scored.similarity >= MIN_CHECKIN_SWAP_SIMILARITY &&
            scored.similarity <= MAX_CHECKIN_SWAP_SIMILARITY
    }
