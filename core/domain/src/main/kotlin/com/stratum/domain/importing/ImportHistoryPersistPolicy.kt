package com.stratum.domain.importing

enum class ImportHistoryPersistPolicy {
    /** Onboarding: replace prior IMPORT-origin rows only; keep LIVE. */
    ReplaceImportOrigin,

    /** Settings: never replace; skip duplicates per D4. */
    MergeSkipDuplicates,
}
