package com.stratum.domain.onboarding

/**
 * Tracks additive edits to a set-valued field against a persisted base. Used for weekday
 * toggles (profile) and muscle toggles (specialization) so back/forward navigation composes
 * correctly.
 */
data class AdditiveSetEdit<T>(
    val added: Set<T> = emptySet(),
    val removed: Set<T> = emptySet(),
)

/**
 * Computes the next additive edit when toggling [item] in [currentEffective] against
 * [persistedBase].
 */
fun <T> toggleInAdditiveSetEdit(
    item: T,
    currentEffective: Set<T>,
    persistedBase: Set<T>,
    currentEdit: AdditiveSetEdit<T>,
): AdditiveSetEdit<T> {
    val isCurrentlySelected = item in currentEffective
    val isPersisted = item in persistedBase

    return if (isCurrentlySelected) {
        if (isPersisted) {
            currentEdit.copy(
                removed = currentEdit.removed + item,
                added = currentEdit.added - item,
            )
        } else {
            currentEdit.copy(added = currentEdit.added - item)
        }
    } else {
        if (isPersisted) {
            currentEdit.copy(removed = currentEdit.removed - item)
        } else {
            currentEdit.copy(
                added = currentEdit.added + item,
                removed = currentEdit.removed - item,
            )
        }
    }
}

/** Applies additive set edits to a persisted base set. */
fun <T> Set<T>.applyAdditiveSetEdit(edit: AdditiveSetEdit<T>): Set<T> =
    (this + edit.added) - edit.removed
