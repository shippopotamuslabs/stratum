package com.stratum.domain.workout

import com.stratum.domain.lift.Lift
import com.stratum.domain.lift.identity.LiftId
import com.stratum.domain.program.identity.WorkoutLiftId

/** Overlay pin wins; otherwise the template/projection value. */
fun resolvedWorkoutPinnedNote(
    overlayNotes: Map<WorkoutLiftId, String>,
    workoutLiftId: WorkoutLiftId,
    templateNote: String?,
): String? = overlayNotes[workoutLiftId] ?: templateNote

/**
 * Catalog row is authoritative when present, including a cleared (null) pin.
 * Missing catalog falls back to the current projection.
 */
fun resolvedLiftPinnedNote(
    catalog: Lift?,
    projectedNote: String?,
): String? = if (catalog != null) catalog.pinnedNote else projectedNote

/** List form of [resolvedWorkoutPinnedNote]: overlay list wins; otherwise the template list. */
fun resolvedWorkoutPinnedNotes(
    overlayNotes: Map<WorkoutLiftId, List<String>>,
    workoutLiftId: WorkoutLiftId,
    templateNotes: List<String>,
): List<String> = overlayNotes[workoutLiftId] ?: templateNotes

/**
 * List form that also honors pre-list overlay entries: overlay list wins, then the
 * legacy single overlay pin (older persisted sessions), then the template list.
 */
fun resolvedWorkoutPinnedNotes(
    overlay: WorkoutOverlay,
    workoutLiftId: WorkoutLiftId,
    templateNotes: List<String>,
): List<String> =
    overlay.workoutLiftPinnedNoteLists[workoutLiftId]
        ?: overlay.workoutLiftPinnedNotes[workoutLiftId]?.let(::listOf)
        ?: templateNotes

/**
 * Pair-aware list resolution (STR-127): slot pinned notes belong to the
 * (slot, lift) pair, not the slot alone.
 *
 * - An explicit overlay pin for the slot (session-added slots, in-session pin
 *   edits) always wins — those maps hold the current pair's notes by construction.
 * - An unswapped slot resolves exactly like [resolvedWorkoutPinnedNotes].
 * - A swapped slot reads the stash for the swapped-in pair; a lift that was never
 *   stashed for this slot in this session and is not the template lift has no
 *   pair notes (empty), so the template lift's notes never leak onto it.
 * - A swap back to the template lift restores its stash when one was captured,
 *   and otherwise falls back to the template list.
 */
fun resolvedWorkoutPinnedNotes(
    overlay: WorkoutOverlay,
    workoutLiftId: WorkoutLiftId,
    templateLiftId: LiftId,
    templateNotes: List<String>,
): List<String> {
    if (
        overlay.workoutLiftPinnedNoteLists.containsKey(workoutLiftId) ||
        overlay.workoutLiftPinnedNotes.containsKey(workoutLiftId)
    ) {
        return resolvedWorkoutPinnedNotes(overlay, workoutLiftId, templateNotes)
    }
    val swappedLiftId = overlay.swappedLifts[workoutLiftId]
        ?: return resolvedWorkoutPinnedNotes(overlay, workoutLiftId, templateNotes)
    return overlay.pinnedNoteStash[SlotLiftPair(workoutLiftId, swappedLiftId)]
        ?: if (swappedLiftId == templateLiftId) templateNotes else emptyList()
}

/**
 * Display form: first overlay list entry wins; otherwise the legacy single-note
 * resolution (overlay pin, then template). Keeps single-string readers working
 * while slots carry ordered lists.
 */
fun resolvedWorkoutPinnedNoteFirst(
    overlay: WorkoutOverlay,
    workoutLiftId: WorkoutLiftId,
    templateNote: String?,
): String? =
    overlay.workoutLiftPinnedNoteLists[workoutLiftId]?.firstOrNull()
        ?: resolvedWorkoutPinnedNote(overlay.workoutLiftPinnedNotes, workoutLiftId, templateNote)

/**
 * List form of [resolvedLiftPinnedNote]: catalog list is authoritative when the
 * catalog row is present (including an explicitly cleared empty list).
 * Missing catalog falls back to the current projection.
 */
fun resolvedLiftPinnedNotes(
    catalogNotes: List<String>?,
    projectedNotes: List<String>,
): List<String> = catalogNotes ?: projectedNotes
