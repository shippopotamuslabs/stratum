package com.stratum.domain.featureflag

/**
 * Product feature flags owned by the app.
 *
 * Flags fail closed (`false`) unless a Remote Config default or server value says otherwise —
 * see in-app defaults in `:core:data` (`FeatureFlagProvidesModule`). A kill-switch flag must
 * therefore default to `true` (backend allowed) so flipping it to `false` in the console
 * disables the path; an unconfigured kill-switch must never disable its path.
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
}
