package com.stratum.domain.onboarding

/**
 * Self-reported training age: years of *solid, consistent* training — not calendar years
 * since the first gym visit. Feeds progression defaults in OS-6 generation.
 *
 * Year ranges are surfaced in the UI as guidance (see ProfileDraftMapping); the enum
 * itself is crisp. Ranges are grounded in empirical training-age frameworks:
 * StrengthLevel.com percentile data, FullStack Body / r/powerlifting training-age models,
 * and Jeff Nippard's outcome-based timelines.
 */
enum class TrainingAge {
    /** 0–2 years of solid training. Beginner-gains phase. */
    Beginner,

    /** 2–4 years. Novice-to-intermediate transition. */
    EarlyIntermediate,

    /** 4–7 years. Median lifter; steady monthly gains. */
    Intermediate,

    /** 7–10 years. Approaching advanced; diminishing returns. */
    LateIntermediate,

    /** 10+ years. Advanced / near genetic potential under quality training. */
    Advanced,
    ;
}

/**
 * Coarse bucket for "when did you last train seriously?". Drives deload/return-to-training
 * heuristics; avoids the need for a date picker in OS-2 (slice decision).
 *
 * D8 collapsed 5→3: `Never` / `WithinLastThreeMonths` / `MoreThanThreeMonthsAgo`.
 * The latter is the single layoff bucket routing non-beginners through Linear.
 * Schema-adjacent (v1 reinstall policy applies — stale old names decode to null).
 */
enum class LastTrainedBucket {
    Never,
    WithinLastThreeMonths,
    MoreThanThreeMonthsAgo,
    ;
}

/**
 * Stratum-owned weekday enum. Kept in domain to avoid coupling `:core:domain` (a pure JVM
 * module) to `java.time.DayOfWeek` Android APIs at the model boundary.
 */
enum class Weekday {
    Monday,
    Tuesday,
    Wednesday,
    Thursday,
    Friday,
    Saturday,
    Sunday,
    ;
}
