package com.stratum.core.infrastructure.sync

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred

/**
 * Signals that the first catalog seed attempt has finished (success or empty).
 * Cloud hydrate waits on this so remote `workout_lifts` / catalog overlays do not
 * hit missing-parent FK failures on cold start.
 */
@Singleton
class LiftCatalogSeedGate @Inject constructor() {
    private val ready = CompletableDeferred<Unit>()

    fun complete() {
        ready.complete(Unit)
    }

    val isReady: Boolean
        get() = ready.isCompleted

    suspend fun awaitReady() {
        ready.await()
    }
}
