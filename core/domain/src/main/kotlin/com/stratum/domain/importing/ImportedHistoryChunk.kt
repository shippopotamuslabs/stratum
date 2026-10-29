package com.stratum.domain.importing

import com.stratum.domain.training.LoggedSet
import com.stratum.domain.training.TrainingSession

data class ImportedHistoryChunk(
    val sourceLabel: String,
    val sessions: List<TrainingSession>,
    val sets: List<LoggedSet>,
    /**
     * When true (Stratum export), keep session/set ids verbatim instead of `fN::` namespacing.
     * Later preserve-stable chunks that collide with earlier ids are namespaced only for the
     * colliding rows.
     */
    val preserveStableIds: Boolean = false,
)

/**
 * Result of combining per-file normalized history.
 *
 * [stableIdSessionIds] are post-merge session ids that originated from chunks with
 * [ImportedHistoryChunk.preserveStableIds] (Stratum). Callers use this for per-session
 * merge dedupe (id-only for Stratum, content fingerprint for third-party).
 */
