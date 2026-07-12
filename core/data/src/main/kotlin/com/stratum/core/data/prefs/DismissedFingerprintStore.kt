package com.stratum.core.data.prefs

/**
 * Pure list-mutation helpers for DataStore-backed dismiss fingerprint stores
 * (equipment proposals, rep-range shifts, …). Keeps LRU-ish cap logic in one place.
 */
internal object DismissedFingerprintStore {

    fun withDismissed(
        current: List<String>,
        fingerprint: String,
        maxRetained: Int,
    ): List<String> {
        val key = fingerprint.trim()
        if (key.isEmpty()) return current
        val without = current.filterNot { it == key }
        val appended = without + key
        return if (appended.size > maxRetained) {
            appended.takeLast(maxRetained)
        } else {
            appended
        }
    }

    fun without(
        current: List<String>,
        fingerprint: String,
    ): List<String> {
        val key = fingerprint.trim()
        if (key.isEmpty()) return current
        return current.filterNot { it == key }
    }
}
