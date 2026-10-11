package com.stratum.domain.lift

/**
 * Return-path state machine for Manage Equipment selection mode, shared by the live
 * workout and template editor swap sheets (STR-121).
 *
 * Protocol: the host arms [arm] with the open sheet's lift id immediately before
 * navigating to selection mode, then offers every published pick via [onPick]. The
 * awaiter delivers a pick exactly once — only when armed, only for generations
 * published after [arm], and only when the currently open sheet still targets the
 * armed lift. Anything else is ignored (stale generations and mismatched targets
 * still consume, so a late pick can never fire twice or land on a moved-on sheet).
 *
 * Hosts must [disarm] when the sheet closes or the user picks manually: the armed
 * target would otherwise survive dismissal and a later unrelated publish (e.g. the
 * increments quick-swap) would mutate the abandoned lift.
 */
class EquipmentPickAwaiter(private val picks: EquipmentPickResult) {
    /** A fresh pick delivered for its armed target. */
    data class AwaitedPick(
        val targetId: String,
        val pick: EquipmentPickResult.Pick,
    )

    private var lastSeenGen = 0L
    private var pendingTargetId: String? = null

    /**
     * Arms [targetId] against picks published after this call. Re-arming replaces
     * any pending target.
     */
    fun arm(targetId: String) {
        lastSeenGen = picks.currentGeneration()
        pendingTargetId = targetId
    }

    /** Abandons the pending target; later picks are ignored until the next [arm]. */
    fun disarm() {
        pendingTargetId = null
    }

    /**
     * Offers a published [pick] for the currently open [openTargetId] (null when no
     * sheet is open). Returns the delivery, or null to ignore.
     */
    fun onPick(pick: EquipmentPickResult.Pick?, openTargetId: String?): AwaitedPick? {
        val pending = pendingTargetId ?: return null
        if (pick == null || pick.generation <= lastSeenGen) return null
        lastSeenGen = pick.generation
        pendingTargetId = null
        if (openTargetId != pending) return null
        return AwaitedPick(pending, pick)
    }
}
