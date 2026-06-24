package com.stratum.domain.lift

/**
 * A bundled, bulk-enable lift set the user can pick in onboarding (§2.1). Stratum is
 * lift-first — there is no equipment graph — so a preset is just a tag carried on each
 * [Lift] via [Lift.presetTags]. Selecting a preset in OS-4 enables every lift tagged with it;
 * the user can then toggle individual lifts on or off.
 *
 * Adding a preset = new enum value + seed-data tag entries; no schema or feature change.
 */
enum class GymPreset {
    CommercialGym,
    GarageGym,
    ;

    /** Human-readable label shown on the preset chips (kept in domain for reuse by OS-5 Review). */
    val displayName: String
        get() = when (this) {
            CommercialGym -> "Commercial gym"
            GarageGym -> "Garage gym"
        }
}
