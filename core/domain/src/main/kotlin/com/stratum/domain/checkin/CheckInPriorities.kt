package com.stratum.domain.checkin


/**
 * Shared sort priority for check-in actions (lower = more urgent / listed first).
 * Used by batch contender order, display order, and [CheckInDecisionEngine.recommendationComparator].
 */
fun CheckInActionKind.sortPriority(): Int =
    when (this) {
        CheckInActionKind.CutVolume -> 0
        CheckInActionKind.SwapLift -> 1
        CheckInActionKind.IncreaseVolume -> 2
        CheckInActionKind.Maintain -> 3
    }

fun CheckInAction.sortPriority(): Int =
    when (this) {
        is CheckInAction.CutVolume -> 0
        is CheckInAction.SwapLift -> 1
        is CheckInAction.IncreaseVolume -> 2
        is CheckInAction.Maintain -> 3
    }

fun CheckInAction.toKind(): CheckInActionKind =
    when (this) {
        is CheckInAction.CutVolume -> CheckInActionKind.CutVolume
        is CheckInAction.SwapLift -> CheckInActionKind.SwapLift
        is CheckInAction.IncreaseVolume -> CheckInActionKind.IncreaseVolume
        is CheckInAction.Maintain -> CheckInActionKind.Maintain
    }
