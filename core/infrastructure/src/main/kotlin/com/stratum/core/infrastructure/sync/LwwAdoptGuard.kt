package com.stratum.core.infrastructure.sync

import kotlinx.coroutines.CancellationException

/**
 * Runs LWW-loss remote adopt [block].
 *
 * - Rethrows [CancellationException] so structured cancellation never force-cleans dirty local.
 * - Other exceptions are reported via [onError] and mapped to `false` (caller may force-clean).
 */
internal suspend fun runLwwAdopt(
    onError: (Exception) -> Unit,
    block: suspend () -> Boolean,
): Boolean {
    return try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onError(e)
        false
    }
}
