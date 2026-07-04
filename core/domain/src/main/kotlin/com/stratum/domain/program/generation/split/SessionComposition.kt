package com.stratum.domain.program.generation.split

/**
 * Organic session role — how a workout is *composed*, independent of catalog split templates.
 *
 * Used by [slotsForSessionType] to specialize slot lists (e.g. spec-focused upper days).
 */
enum class SessionComposition {
    /** Maintenance-oriented upper: compounds + light iso. */
    UpperBalanced,

    /** Dedicated specialization upper: extra direct work for specialized upper muscles. */
    UpperSpecFocused,

    /** Third upper in a 5-day week: finishes remaining chest/back/shoulder volume. */
    UpperVolumeFinisher,

    /** Primary lower: squat/hinge anchors and in-domain leg iso. */
    LowerPrimary,

    /** Lower with supplementary upper-domain touches (arms/rear delt on leg day). */
    LowerWithUpperTouch,
}
