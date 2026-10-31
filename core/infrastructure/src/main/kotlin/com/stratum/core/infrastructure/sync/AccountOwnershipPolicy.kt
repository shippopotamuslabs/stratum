package com.stratum.core.infrastructure.sync

/**
 * Decides whether local Room must be wiped before [incomingUserId] may use it.
 *
 * [lastSyncedUserId] is the durable owner and is **kept across logout** so a later login as a
 * different uid still sees a switch even if in-memory session state was cleared. A blank durable
 * owner (first install, or prefs corruption) is untrusted — wipe rather than mix leftover rows.
 */
internal object AccountOwnershipPolicy {
    fun previousOwner(durableOwner: String?, memoryOwner: String?): String? =
        durableOwner?.takeIf { it.isNotBlank() } ?: memoryOwner

    fun mustWipe(
        durableOwner: String?,
        memoryOwner: String?,
        incomingUserId: String,
    ): Boolean = previousOwner(durableOwner, memoryOwner) != incomingUserId
}
