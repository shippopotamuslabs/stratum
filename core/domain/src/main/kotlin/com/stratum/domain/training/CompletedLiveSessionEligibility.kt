package com.stratum.domain.training

/**
 * Shared eligibility for post-workout proposal analysis (equipment, rep-range, …).
 * Completed **live** sessions only — imports and in-progress work never surface proposals.
 */
fun isEligibleCompletedLiveSession(
    status: TrainingSessionStatus,
    origin: TrainingSessionOrigin,
): Boolean =
    status == TrainingSessionStatus.COMPLETED && origin == TrainingSessionOrigin.LIVE
