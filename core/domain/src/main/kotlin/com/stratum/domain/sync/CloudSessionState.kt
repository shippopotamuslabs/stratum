package com.stratum.domain.sync

/**
 * Cloud sync session lifecycle for the signed-in user.
 *
 * Distinct from [SyncStatus] (coarse push/pull progress): this answers whether the app shell
 * may treat local data as owned by the active account.
 */
sealed interface CloudSessionState {
    /** No active sync session (signed out or never started). */
    data object Inactive : CloudSessionState

    /** Ownership / first establish in progress for [userId]. */
    data class Establishing(val userId: String) : CloudSessionState

    /**
     * Local Room is owned by [userId] and safe to show.
     * Network push/pull may still be running or recovering in the background.
     */
    data class Ready(val userId: String) : CloudSessionState

    /**
     * Could not establish a usable local session for [userId] (e.g. first hydrate failed
     * with no prior local data). Shell should offer retry / sign-out.
     */
    data class Failed(val userId: String, val message: String) : CloudSessionState
}
