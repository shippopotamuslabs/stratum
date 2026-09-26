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
    EQUIPMENT_MANAGEMENT_V2(
        key = "equipment_management_v2",
        description = "Launch flag for the equipment-management rework " +
            "(in-workout editing, shared/fork save choice, usage visibility, flexible tier starts). " +
            "When off, all existing equipment screens behave as before.",
    ),
    INCREMENTS_AI_SCAN(
        key = "increments_ai_scan",
        description = "Launch flag for the AI increment scan prefill " +
            "(photo of a stack label prefills editor drafts for review, never auto-saves). " +
            "When off, the scan entry stays hidden and manual entry behaves as before.",
    ),
}
