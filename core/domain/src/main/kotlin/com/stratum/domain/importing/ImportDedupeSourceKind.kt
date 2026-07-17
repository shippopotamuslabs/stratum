package com.stratum.domain.importing

enum class ImportDedupeSourceKind {
    /** Prefer id equality on session/set ids from Stratum envelope. */
    StratumStableIds,

    /**
     * After id-skip: content-match fingerprint only — device-local calendar date of
     * `session.startedAt` plus multiset of sets (normalized exercise, weight, unit, reps, rir).
     * Skip when an existing session shares that full fingerprint. Same structure on a
     * different day imports; same day with different structure imports. No absolute
     * day occupancy.
     */
    ThirdParty,
}
