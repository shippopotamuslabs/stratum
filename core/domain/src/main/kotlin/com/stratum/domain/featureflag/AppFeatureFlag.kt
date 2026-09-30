package com.stratum.domain.featureflag

/**
 * Product feature flags owned by the app.
 *
 * Flags fail closed (`false`) unless a Remote Config default or server value says otherwise —
 * see in-app defaults in `:core:data` (`FeatureFlagProvidesModule`). Two kinds:
 * - Launch flags default to `false` (feature hidden) and roll out by flipping to `true`.
 * - Kill-switch flags must default to `true` (backend allowed) so flipping them to `false`
 *   in the console disables the path; an unconfigured kill-switch must never disable its path.
 */
enum class AppFeatureFlag(
    override val key: String,
    override val description: String,
) : FeatureFlag {
    CLOUD_PROGRAM_SYNTHESIS(
        key = "cloud_program_synthesis",
        description = "Kill-switch for the cloud CP-SAT synthesis backend. " +
            "When off, program generation fails fast without touching the network.",
    ),
    INCREMENTS_AI_SCAN(
        key = "increments_ai_scan",
        description = "Launch flag for the AI increment scan prefill " +
            "(photo of a stack label prefills editor drafts for review, never auto-saves). " +
            "When off, the scan entry stays hidden and manual entry behaves as before.",
    ),
}
