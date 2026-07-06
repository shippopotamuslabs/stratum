package com.stratum.domain.lift

/**
 * How external load relates to a [Lift]'s resistance profile.
 *
 * - [Weighted]: standard external load (barbell, dumbbell, cable, machine stack). The
 *   logged weight is the added resistance. Default for all lifts.
 * - [Bodyweight]: bodyweight-only (pull-ups, dips, push-ups). No external load is logged;
 *   the weight field is hidden in the UI and stored as `Weight(0.0, ...)`. Progression is
 *   rep-only — the engine never attempts to increment load.
 * - [Assisted]: machine-assisted or band-assisted variant (assisted pull-up, assisted dip).
 *   The logged weight is **negative** (e.g. `Weight(-50.0, Lb)` = 50 lb of assistance).
 *   Progression adds positive increments, reducing assistance toward zero. The UI displays
 *   the absolute value with a "−" prefix header.
 */
enum class LoadType {
    Weighted,
    Bodyweight,
    Assisted,
}
